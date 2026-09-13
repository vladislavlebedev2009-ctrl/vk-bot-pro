package bot.discord;

import bot.config.BotConfig;
import bot.database.AuditRepository;
import bot.database.AuditRepository.AuditEntry;
import bot.database.ChatRepository;
import bot.database.PunishmentRepository;
import bot.database.PunishmentRepository.Punishment;
import bot.database.UserRepository;
import bot.database.UserRepository.RankCount;
import bot.database.UserRepository.TopEntry;
import bot.model.Rank;
import bot.model.User;
import bot.service.AccessControlService;
import bot.service.LinkService;
import bot.service.LinkService.BindResult;
import bot.service.Logger;
import bot.service.RegistrationService;
import bot.util.Text;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Обработчик slash-команд Discord.
 *
 * Все команды работают с ОБЩЕЙ базой VK-бота: Discord ID резолвится
 * в VK-профиль через {@link LinkService}, права проверяются той же
 * матрицей RankService (через AccessControlService). Дублирования
 * бизнес-логики VK нет — здесь только форматирование ответа.
 */
public final class DiscordCommandHandler
        implements EventListener {

    private static final Logger logger =
            new Logger(
                    DiscordCommandHandler.class
            );

    private static final int MAX_RECORDS =
            20;

    private static final int MAX_ROWS =
            50;

    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat(
                    "dd.MM.yyyy HH:mm"
            );

    private final BotConfig config;

    private final LinkService linkService;

    private final UserRepository userRepository;

    private final PunishmentRepository punishmentRepository;

    private final AuditRepository auditRepository;

    private final ChatRepository chatRepository;

    private final RegistrationService registrationService;

    private final AccessControlService accessControl;


    public DiscordCommandHandler(
            BotConfig config,
            LinkService linkService,
            UserRepository userRepository,
            PunishmentRepository punishmentRepository,
            AuditRepository auditRepository,
            ChatRepository chatRepository,
            RegistrationService registrationService,
            AccessControlService accessControl
    ) {

        this.config =
                config;

        this.linkService =
                linkService;

        this.userRepository =
                userRepository;

        this.punishmentRepository =
                punishmentRepository;

        this.auditRepository =
                auditRepository;

        this.chatRepository =
                chatRepository;

        this.registrationService =
                registrationService;

        this.accessControl =
                accessControl;
    }


    @Override
    public void onEvent(
            GenericEvent event
    ) {

        if (
                event instanceof
                        SlashCommandInteractionEvent slash
        ) {

            handle(
                    slash
            );
        }
    }


    private void handle(
            SlashCommandInteractionEvent event
    ) {

        long discordId =
                event.getUser()
                        .getIdLong();

        String name =
                event.getName();

        try {

            switch (
                    name
            ) {

                case "help" ->
                        replyText(
                                event,
                                help()
                        );

                case "register" ->
                        register(
                                event,
                                discordId
                        );

                case "link" ->
                        link(
                                event,
                                discordId
                        );

                case "profile" ->
                        profile(
                                event,
                                discordId
                        );

                case "rank" ->
                        rank(
                                event,
                                discordId
                        );

                case "ranks" ->
                        ranks(
                                event,
                                discordId
                        );

                case "balance" ->
                        balance(
                                event,
                                discordId
                        );

                case "top" ->
                        top(
                                event,
                                discordId
                        );

                case "stats" ->
                        stats(
                                event,
                                discordId
                        );

                case "history" ->
                        history(
                                event,
                                discordId
                        );

                default ->
                        replyText(
                                event,
                                "❌ Неизвестная команда."
                        );
            }

        } catch (
                Exception e
        ) {

            logger.error(
                    "Ошибка при выполнении Discord-команды: "
                            + name,
                    e
            );

            replyText(
                    event,
                    "❌ Произошла ошибка при выполнении команды."
            );
        }
    }


    private String help() {

        return """
                🎮 DISCORD-БОТ КЛУБА
                Все команды работают с общим профилем клуба.

                🔗 /link <код> — привязать Discord к VK
                📋 /register — статус регистрации
                👤 /profile — карточка профиля
                🎖 /rank — твоё звание
                🏅 /ranks — все звания клуба
                💰 /balance — твой баланс
                ⭐ /top — TOP по репутации
                📊 /stats — статистика клуба (с 8 уровня)
                📋 /history — журнал и история (с 4 уровня)

                Чтобы начать: в VK-боте выполни /link,
                затем здесь /link <код>.
                """;
    }


    private void register(
            SlashCommandInteractionEvent event,
            long discordId
    ) {

        Long vk =
                linkService.resolveVkUser(
                        discordId
                );

        if (
                vk == null
        ) {

            replyText(
                    event,
                    linkPrompt()
            );

            return;
        }

        if (
                registrationService.isRegistered(
                        vk
                )
        ) {

            User user =
                    userRepository.findById(
                            vk
                    );

            replyText(
                    event,
                    "✅ Связь подтверждена:\n"
                            + "🆔 VK: @id"
                            + vk
                            + "\n📛 Имя: "
                            + safeName(
                            user
                    )
                            + "\n📅 Дата вступления: "
                            + Text.date(
                            user == null
                                    ? 0
                                    : user.getRegisteredAt()
                    ) + "\n\n"
                            + "Команды /profile, /rank, /balance "
                            + "уже доступны."
            );

            return;
        }

        replyText(
                event,
                "⛔ Профиль ещё не зарегистрирован:\n"
                        + "🆔 VK: @id"
                        + vk
                        + "\n\nПройди регистрацию в VK-боте: "
                        + "напиши /register."
        );
    }


    private void link(
            SlashCommandInteractionEvent event,
            long discordId
    ) {

        OptionMapping option =
                event.getOption(
                        "code"
                );

        String code =
                option == null
                        ? null
                        : option.getAsString();

        BindResult result =
                linkService.bind(
                        discordId,
                        code
                );

        replyText(
                event,
                result.message()
        );
    }


    private void profile(
            SlashCommandInteractionEvent event,
            long discordId
    ) {

        Long vk =
                linkedVk(
                        event,
                        discordId
                );

        if (
                vk == null
        ) {

            return;
        }

        User user =
                resolveLinkedUser(
                        vk
                );

        if (
                user == null
        ) {

            replyText(
                    event,
                    "❌ Профиль не найден."
            );

            return;
        }

        String name =
                Text.safe(
                        user.getName(),
                        "Не указано"
                );

        String callSign =
                Text.safe(
                        user.getCallSign(),
                        "Не указан"
                );

        Rank rank =
                user.getRankEnum();

        replyText(
                event,
                """
                👤 ПРОФИЛЬ ТОВАРИЩА

                📛 Имя: %s
                📞 Позывной: %s
                🆔 VK: @id%d

                🏅 Звание: %s
                🔰 Уровень: %s (%d)

                ⭐ Репутация: %d
                💰 Баланс: %s ₽
                ⚠️ Предупреждения: %d
                🚫 Наказания: %d
                🏠 Подключено бесед: %d

                📅 Дата вступления: %s
                📌 Статус: %s
                """.formatted(
                        name,
                        callSign,
                        user.getId(),
                        rank == null
                                ? "Не указано"
                                : rank.getDisplayName(),
                        rank == null
                                ? "-"
                                : rank.getRoman(),
                        rank == null
                                ? 0
                                : rank.getLevel(),
                        user.getRep(),
                        Text.number(
                                user.getBalance()
                        ),
                        user.getWarns(),
                        user.getPunishmentsReceived(),
                        chatRepository.getConnectedChatsCount(
                                user.getId()
                        ),
                        Text.date(
                                user.getRegisteredAt()
                        ),
                        status(
                                user
                        )
                )
        );
    }


    private void rank(
            SlashCommandInteractionEvent event,
            long discordId
    ) {

        Long vk =
                linkedVk(
                        event,
                        discordId
                );

        if (
                vk == null
        ) {

            return;
        }

        User user =
                resolveLinkedUser(
                        vk
                );

        if (
                user == null
        ) {

            replyText(
                    event,
                    "❌ Пользователь не найден."
            );

            return;
        }

        Rank rank =
                user.getRankEnum();

        if (
                rank == null
        ) {

            replyText(
                    event,
                    "🎖 У тебя пока нет звания.\n\n"
                            + "Пройди регистрацию в VK-боте "
                            + "(/register), чтобы получить "
                            + "стартовое звание «Кандидат»."
            );

            return;
        }

        replyText(
                event,
                """
                🎖 ТВОЁ ЗВАНИЕ

                🏅 Звание: %s
                🔰 Ступень: %s (уровень %d)

                %s

                👥 Участников с этим званием: %d
                """.formatted(
                        rank.getDisplayName(),
                        rank.getRoman(),
                        rank.getLevel(),
                        rank.getDescription(),
                        countByRank(
                                rank
                        )
                )
        );
    }


    private void ranks(
            SlashCommandInteractionEvent event,
            long discordId
    ) {

        Long vk =
                linkedVk(
                        event,
                        discordId
                );

        if (
                vk == null
        ) {

            return;
        }

        User user =
                resolveLinkedUser(
                        vk
                );

        Rank current =
                user == null
                        ? null
                        : user.getRankEnum();

        List<Rank> ranks =
                java.util.Arrays.asList(
                        Rank.values()
                );

        ranks.sort(
                (
                        a,
                        b
                ) ->
                        Integer.compare(
                                b.getLevel(),
                                a.getLevel()
                        )
        );

        StringBuilder message =
                new StringBuilder();

        message.append(
                "🏅 ЗВАНИЯ КЛУБА\n\n"
        );

        for (
                Rank rank :
                ranks
        ) {

            message.append(
                    rank == current
                            ? "👉 "
                            : "• "
            );

            message.append(
                    rank.getDisplayName()
            );

            message.append(
                    " — "
            );

            message.append(
                    rank.getRoman()
            );

            message.append(
                    " (уровень "
            );

            message.append(
                    rank.getLevel()
            );

            message.append(
                    ")\n    "
            );

            message.append(
                    rank.getDescription()
            );

            message.append(
                    "\n"
            );
        }

        replyText(
                event,
                message.toString()
        );
    }


    private void balance(
            SlashCommandInteractionEvent event,
            long discordId
    ) {

        Long vk =
                linkedVk(
                        event,
                        discordId
                );

        if (
                vk == null
        ) {

            return;
        }

        User user =
                resolveLinkedUser(
                        vk
                );

        if (
                user == null
        ) {

            replyText(
                    event,
                    "❌ Пользователь не найден."
            );

            return;
        }

        replyText(
                event,
                "💰 ТВОЙ БАЛАНС\n\n"
                        + "📛 Имя: "
                        + safeName(
                        user
                )
                        + "\n🆔 VK: @id"
                        + user.getId()
                        + "\n💵 Баланс: "
                        + Text.number(
                        user.getBalance()
                )
                        + " ₽"
        );
    }


    private void top(
            SlashCommandInteractionEvent event,
            long discordId
    ) {

        Long vk =
                linkedVk(
                        event,
                        discordId
                );

        if (
                vk == null
        ) {

            return;
        }

        resolveLinkedUser(
                vk
        );

        int limit =
                Math.min(
                        Math.max(
                                config.getTopLimit(),
                                1
                        ),
                        MAX_ROWS
                );

        List<TopEntry> entries =
                userRepository.top(
                        "rep",
                        limit
                );

        if (
                entries.isEmpty()
        ) {

            replyText(
                    event,
                    "📊 Рейтинг пока пуст."
            );

            return;
        }

        StringBuilder message =
                new StringBuilder();

        message.append(
                "⭐ TOP ПО РЕПУТАЦИИ\n\n"
        );

        int position =
                1;

        for (
                TopEntry entry :
                entries
        ) {

            message.append(
                    position == 1
                            ? "🏆"
                            : String.format(
                            "%2d.",
                            position
                    )
            );

            message.append(
                    " "
            );

            message.append(
                    Text.safe(
                            entry.name(),
                            "Не указано"
                    )
            );

            message.append(
                    " ("
            );

            message.append(
                    Text.safe(
                            entry.callSign(),
                            "Не указан"
                    )
            );

            message.append(
                    ") — "
            );

            message.append(
                    entry.value()
            );

            message.append(
                    "\n"
            );

            position++;
        }

        replyText(
                event,
                message.toString()
        );
    }


    private void stats(
            SlashCommandInteractionEvent event,
            long discordId
    ) {

        Long vk =
                linkedVk(
                        event,
                        discordId
                );

        if (
                vk == null
        ) {

            return;
        }

        User user =
                resolveLinkedUser(
                        vk
                );

        if (
                !accessControl.canUse(
                        vk,
                        user == null
                                ? null
                                : user.getRankEnum(),
                        "stats"
                )
        ) {

            replyText(
                    event,
                    "⛔ Недостаточно прав: статистика доступна "
                            + "званию «Член Президиума» "
                            + "(8 уровень) и выше."
            );

            return;
        }

        int registered =
                userRepository.countRegistered();

        double averageRep =
                userRepository.averageRep();

        long totalBalance =
                userRepository.totalBalance();

        int activePunishments =
                punishmentRepository.countActive();

        List<RankCount> distribution =
                userRepository.rankDistribution();

        StringBuilder message =
                new StringBuilder();

        message.append(
                "📊 СТАТИСТИКА КЛУБА\n\n"
        );

        message.append(
                "👥 Зарегистрировано: "
        );

        message.append(
                registered
        );

        message.append(
                "\n⭐ Средняя репутация: "
        );

        message.append(
                String.format(
                        "%.1f",
                        averageRep
                )
        );

        message.append(
                "\n💰 Общий баланс: "
        );

        message.append(
                Text.number(
                        totalBalance
                )
        );

        message.append(
                " ₽\n⚖️ Активных наказаний: "
        );

        message.append(
                activePunishments
        );

        message.append(
                "\n\n"
        );

        message.append(
                "🏅 ПО ЗВАНИЯМ:\n"
        );

        if (
                distribution.isEmpty()
        ) {

            message.append(
                    "Нет данных."
            );

        } else {

            for (
                    RankCount entry :
                    distribution
            ) {

                message.append(
                        "• "
                );

                message.append(
                        entry.rankName()
                );

                message.append(
                        " — "
                );

                message.append(
                        entry.count()
                );

                message.append(
                        "\n"
                );
            }
        }

        replyText(
                event,
                message.toString()
        );
    }


    private void history(
            SlashCommandInteractionEvent event,
            long discordId
    ) {

        Long vk =
                linkedVk(
                        event,
                        discordId
                );

        if (
                vk == null
        ) {

            return;
        }

        User user =
                resolveLinkedUser(
                        vk
                );

        if (
                !accessControl.canUse(
                        vk,
                        user == null
                                ? null
                                : user.getRankEnum(),
                        "history"
                )
        ) {

            replyText(
                    event,
                    "⛔ Недостаточно прав: история доступна "
                            + "званию «Организатор» "
                            + "(4 уровень) и выше."
            );

            return;
        }

        List<Punishment> punishments =
                punishmentRepository.getHistory(
                        vk,
                        MAX_RECORDS
                );

        List<AuditEntry> actions =
                auditRepository.getForUser(
                        vk,
                        MAX_RECORDS
                );

        if (
                punishments.isEmpty()
                        && actions.isEmpty()
        ) {

            replyText(
                    event,
                    "📋 У профиля наказаний "
                            + "и журнал действий пусты."
            );

            return;
        }

        StringBuilder message =
                new StringBuilder();

        message.append(
                "📋 ИСТОРИЯ ПОЛЬЗОВАТЕЛЯ\n\n"
        );

        message.append(
                "👤 "
        );

        message.append(
                safeName(
                        user
                )
        );

        message.append(
                " (@id"
        );

        message.append(
                vk
        );

        message.append(
                ")\n\n"
        );

        if (
                !punishments.isEmpty()
        ) {

            message.append(
                    "⛔ НАКАЗАНИЯ:\n"
            );

            int number =
                    1;

            for (
                    Punishment record :
                    punishments
            ) {

                message.append(
                        number
                );

                message.append(
                        ". "
                );

                message.append(
                        TIME_FORMAT.format(
                                new Date(
                                        record.createdAt()
                                )
                        )
                );

                message.append(
                        "\n   ⚡ Тип: "
                );

                message.append(
                        record.type()
                                .toUpperCase(
                                        Locale.ROOT
                                )
                );

                message.append(
                        "\n   🏷 Статус: "
                );

                message.append(
                        statusLabel(
                                record.status()
                        )
                );

                if (
                        record.durationMinutes() > 0
                ) {

                    message.append(
                            "\n   ⏱ Срок: "
                    );

                    message.append(
                            record.durationMinutes()
                    );

                    message.append(
                            " мин."
                    );
                }

                if (
                        record.reason() != null
                                && !record.reason()
                                .isBlank()
                ) {

                    message.append(
                            "\n   📝 Причина: "
                    );

                    message.append(
                            record.reason()
                    );
                }

                message.append(
                        "\n"
                );

                number++;
            }

            message.append(
                    "\n"
            );
        }

        if (
                !actions.isEmpty()
        ) {

            message.append(
                    "📜 ЖУРНАЛ ДЕЙСТВИЙ:\n"
            );

            appendEntries(
                    message,
                    actions
            );
        }

        replyText(
                event,
                message.toString()
        );
    }


    /* === helpers === */


    /**
     * Резолв Discord ID в VK. Если связь не найдена — пользователю
     * приходит инструкция по привязке.
     */
    private Long linkedVk(
            SlashCommandInteractionEvent event,
            long discordId
    ) {

        Long vk =
                linkService.resolveVkUser(
                        discordId
                );

        if (
                vk == null
        ) {

            replyText(
                    event,
                    linkPrompt()
            );
        }

        return vk;
    }


    /**
     * VK-пользователь для Discord-команды: профиль гарантированно
     * существует, а Вождь клуба всегда в звании «Вождь клуба».
     */
    private User resolveLinkedUser(
            long vkUserId
    ) {

        userRepository.ensureUser(
                vkUserId
        );

        if (
                config.getPatronId()
                        == vkUserId
        ) {

            User user =
                    userRepository.findById(
                            vkUserId
                    );

            Rank rank =
                    user == null
                            ? null
                            : user.getRankEnum();

            if (
                    rank == null
                            || rank.getLevel()
                            < Rank.VOZHD_CLUBA
                            .getLevel()
            ) {

                userRepository.setRank(
                        vkUserId,
                        Rank.VOZHD_CLUBA.name()
                );
            }
        }

        return userRepository.findById(
                vkUserId
        );
    }


    private String linkPrompt() {

        return "🔗 Discord не привязан к VK-профилю.\n\n"
                + "Как привязать:\n"
                + "1. В VK-боте выполни /link "
                + "и получи код.\n"
                + "2. Здесь напиши /link <код>.";
    }


    private String status(
            User user
    ) {

        if (
                user.isBanned()
        ) {

            return "🔴 Заблокирован";
        }

        if (
                user.isMuted()
        ) {

            return "🟡 Ограничен в общении";
        }

        return "🟢 Активен";
    }


    private int countByRank(
            Rank rank
    ) {

        return userRepository.rankDistribution()
                .stream()
                .filter(
                        entry ->
                                entry.level()
                                        == rank.getLevel()
                )
                .mapToInt(
                        entry ->
                                entry.count()
                )
                .sum();
    }


    private void appendEntries(
            StringBuilder message,
            List<AuditEntry> entries
    ) {

        int number =
                1;

        for (
                AuditEntry entry :
                entries
        ) {

            message.append(
                    number
            );

            message.append(
                    ". "
            );

            message.append(
                    TIME_FORMAT.format(
                            new Date(
                                    entry.createdAt()
                            )
                    )
            );

            message.append(
                    "\n   🎭 Актор: "
            );

            message.append(
                    nameOrId(
                            entry.actorId()
                    )
            );

            message.append(
                    "\n   ⚡ Действие: "
            );

            message.append(
                    entry.action()
            );

            if (
                    entry.targetId() > 0
            ) {

                message.append(
                        "\n   🎯 Цель: "
                );

                message.append(
                        nameOrId(
                                entry.targetId()
                        )
                );
            }

            if (
                    entry.detail() != null
                            && !entry.detail()
                            .isBlank()
            ) {

                message.append(
                        "\n   📝 Детали: "
                );

                message.append(
                        entry.detail()
                );
            }

            if (
                    entry.result() != null
                            && !entry.result()
                            .isBlank()
            ) {

                message.append(
                        "\n   ✔️ Результат: "
                );

                message.append(
                        entry.result()
                );
            }

            message.append(
                    "\n"
            );

            number++;
        }
    }


    private String nameOrId(
            long id
    ) {

        User user =
                userRepository.findById(
                        id
                );

        if (
                user == null
                        || user.getName() == null
                        || user.getName()
                        .isBlank()
        ) {

            return "@id"
                    + id;
        }

        return "@id"
                + id
                + " ("
                + user.getName()
                + ")";
    }


    private String statusLabel(
            String status
    ) {

        if (
                status == null
        ) {

            return "?";
        }

        return switch (
                status
        ) {

            case "ACTIVE" -> "✅ Активно";

            case "EXPIRED" -> "⏰ Истекло";

            case "REVOKED" -> "↩️ Снято";

            default -> status;
        };
    }


    private String safeName(
            User user
    ) {

        if (
                user == null
                        || user.getName() == null
                        || user.getName()
                        .isBlank()
        ) {

            return "(без имени)";
        }

        return user.getName();
    }


    private void replyText(
            SlashCommandInteractionEvent event,
            String text
    ) {

        event.reply(
                        text
                )
                .setEphemeral(
                        true
                )
                .queue(
                        null,
                        failure ->
                                logger.warning(
                                        "Discord: не удалось отправить "
                                                + "ответ: "
                                                + failure.getMessage()
                                )
                );
    }
}