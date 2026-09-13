package bot.command.commands;

import bot.command.Command;
import bot.command.CommandContext;
import bot.model.Rank;
import bot.model.User;
import bot.service.UserTargetResolver;
import bot.util.Text;

import java.util.List;

/**
 * Карточка участника клуба: /profile, /me, /myinfo, /info.
 *
 * Без аргумента — свой профиль. С аргументом (/profile @id,
 * /profile 123) — профиль другого участника.
 */
public class ProfileCommand
        implements Command {

    @Override
    public String getName() {

        return "profile";
    }

    @Override
    public String getDescription() {

        return "Профиль участника: /profile [@id]";
    }

    @Override
    public List<String> getAliases() {

        return List.of(
                "me",
                "myinfo",
                "info"
        );
    }

    @Override
    public void execute(
            CommandContext context
    ) {

        long targetId =
                context.getUserId();

        String argument =
                context.getArgument(
                        0
                );

        if (
                argument != null
                        && !argument.isBlank()
        ) {

            Long resolved =
                    UserTargetResolver.resolve(
                            argument
                    );

            if (
                    resolved == null
            ) {

                context.reply(
                        "❌ Укажи пользователя через @ или ID.\n\n"
                                + "Примеры:\n"
                                + "/profile @id123456789\n"
                                + "/profile 123456789"
                );

                return;
            }

            targetId =
                    resolved;

            context.getUserRepository()
                    .ensureUser(
                            targetId
                    );
        }

        User user =
                context.getUserRepository()
                        .findById(
                                targetId
                        );

        if (
                user == null
        ) {

            context.reply(
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

        String status;

        if (
                user.isBanned()
        ) {

            status =
                    "🔴 Заблокирован";

        } else if (
                user.isMuted()
        ) {

            status =
                    "🟡 Ограничен в общении";

        } else {

            status =
                    "🟢 Активен";
        }

        context.reply(
                """
                👤 ПРОФИЛЬ ТОВАРИЩА

                📛 Имя: %s
                📞 Позывной: %s
                🆔 VK: @id%d (%s)

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
                        name,
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
                        context.getChatRepository()
                                .getConnectedChatsCount(
                                        user.getId()
                                ),
                        Text.date(
                                user.getRegisteredAt()
                        ),
                        status
                )
        );
    }
}