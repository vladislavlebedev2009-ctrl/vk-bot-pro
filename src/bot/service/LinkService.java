package bot.service;

import bot.database.AuditRepository;
import bot.database.LinkRepository;
import bot.database.LinkRepository.LinkRow;
import bot.database.UserRepository;
import bot.model.User;

import java.security.SecureRandom;

/**
 * Единая логика привязки Discord к VK-профилю.
 *
 * Поток такой:
 *  1. В VK бот получает /link — сервис генерирует одноразовый код;
 *  2. В Discord пользователь вызывает /link <код> — сервис забирает
 *     код и записывает связь 1:1 (VK ↔ Discord);
 *  3. Все Discord-команды резолвят Discord ID в VK через этот сервис
 *     и работают с общим профилем users (данные общие).
 *
 * VK-логика не дублируется: сам код живёт здесь, а команды VK и Discord
 * только вызывают один и тот же сервис.
 */
public class LinkService {

    /**
     * Срок жизни кода привязки.
     */
    private static final long CODE_TTL_MILLIS =
            15 * 60_000L;

    /**
     * Длина кода привязки.
     */
    private static final int CODE_LENGTH =
            8;

    /**
     * Алфавит без похожих символов (0/O, 1/I/L).
     */
    private static final String CODE_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final SecureRandom RANDOM =
            new SecureRandom();

    private final LinkRepository linkRepository;

    private final UserRepository userRepository;

    private final AuditRepository auditRepository;


    public LinkService(
            LinkRepository linkRepository,
            UserRepository userRepository,
            AuditRepository auditRepository
    ) {

        this.linkRepository =
                linkRepository;

        this.userRepository =
                userRepository;

        this.auditRepository =
                auditRepository;
    }


    public boolean isVkLinked(
            long vkUserId
    ) {

        return linkRepository.isLinked(
                vkUserId
        );
    }


    public boolean isLinked(
            long discordUserId
    ) {

        return linkRepository.findVkByDiscord(
                discordUserId
        ) != null;
    }


    public Long resolveVkUser(
            long discordUserId
    ) {

        return linkRepository.findVkByDiscord(
                discordUserId
        );
    }


    /**
     * Сгенерировать (или обновить) одноразовый код для VK-пользователя.
     * Если профиль уже привязан — возвращается описание ошибки.
     */
    public CreateCodeResult createCode(
            long vkUserId
    ) {

        if (
                linkRepository.isLinked(
                        vkUserId
                )
        ) {

            return new CreateCodeResult(
                    "🔗 Профиль уже привязан к Discord.\n"
                            + "Привязка одноразовая — повторно "
                            + "сгенерировать код нельзя.",
                    null
            );
        }

        String code =
                generateCode();

        linkRepository.saveCode(
                vkUserId,
                code,
                System.currentTimeMillis()
                        + CODE_TTL_MILLIS
        );

        return new CreateCodeResult(
                null,
                code
        );
    }


    /**
     * Забрать код и привязать Discord-аккаунт к VK-профилю.
     * Проверки: повторная привязка Discord, неверный код,
     * истёкший код, уже использованный код.
     */
    public BindResult bind(
            long discordUserId,
            String rawCode
    ) {

        Long existingVk =
                linkRepository.findVkByDiscord(
                        discordUserId
                );

        if (
                existingVk != null
        ) {

            return new BindResult(
                    "🔗 Этот Discord-аккаунт уже привязан "
                            + "к VK-профилю ("
                            + idLabel(
                            existingVk
                    )
                            + ").\nОтвязать нельзя — связь одноразовая.",
                    false,
                    existingVk
            );
        }

        String code =
                normalizeCode(
                        rawCode
                );

        if (
                code.isEmpty()
        ) {

            return new BindResult(
                    "❌ Укажи код, полученный в VK-боте:\n/link <код>",
                    false,
                    null
            );
        }

        LinkRow row =
                linkRepository.findByCode(
                        code
                );

        if (
                row == null
        ) {

            return new BindResult(
                    "❌ Неверный код. Проверь код из VK-бота "
                            + "(команда /link) и попробуй ещё раз.",
                    false,
                    null
            );
        }

        if (
                row.linkedAt() > 0
        ) {

            return new BindResult(
                    "♻️ Этот код уже был использован.",
                    false,
                    row.vkUserId()
            );
        }

        if (
                System.currentTimeMillis()
                        > row.codeExpiresAt()
        ) {

            return new BindResult(
                    "⏰ Код истёк. Запроси новый код в VK-боте "
                            + "(команда /link).",
                    false,
                    row.vkUserId()
            );
        }

        linkRepository.bind(
                row.vkUserId(),
                discordUserId
        );

        auditRepository.log(
                row.vkUserId(),
                "DISCORD_LINK",
                0L,
                "Discord ID "
                        + discordUserId
                        + " привязан",
                "OK"
        );

        User user =
                userRepository.findById(
                        row.vkUserId()
                );

        return new BindResult(
                "✅ Discord успешно привязан к VK-профилю!\n\n"
                        + "📛 Имя: "
                        + (user == null
                        ? idLabel(
                        row.vkUserId()
                )
                        : safeName(
                        user
                ))
                        + "\n🆔 VK: "
                        + idLabel(
                        row.vkUserId()
                )
                        + "\n\nТеперь команды /profile, /rank, /balance "
                        + "и другие будут показывать общие данные клуба.",
                true,
                row.vkUserId()
        );
    }


    private String normalizeCode(
            String rawCode
    ) {

        if (
                rawCode == null
        ) {

            return "";
        }

        return rawCode.trim()
                .toUpperCase();
    }


    private String generateCode() {

        StringBuilder code =
                new StringBuilder(
                        CODE_LENGTH
                );

        for (
                int i = 0;
                i < CODE_LENGTH;
                i++
        ) {

            code.append(
                    CODE_ALPHABET.charAt(
                            RANDOM.nextInt(
                                    CODE_ALPHABET.length()
                            )
                    )
            );
        }

        return code.toString();
    }


    private String idLabel(
            long id
    ) {

        return "@id"
                + id;
    }


    private String safeName(
            User user
    ) {

        return user.getName() == null
                || user.getName()
                .isBlank()
                ? "(без имени)"
                : user.getName();
    }


    public record CreateCodeResult(
            String error,
            String code
    ) {
    }


    public record BindResult(
            String message,
            boolean success,
            Long vkUserId
    ) {
    }
}