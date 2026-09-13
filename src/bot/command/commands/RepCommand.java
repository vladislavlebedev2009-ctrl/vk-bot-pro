package bot.command.commands;


import bot.command.Command;
import bot.command.CommandContext;
import bot.config.BotConfig;
import bot.model.User;
import bot.service.UserTargetResolver;

import java.util.Locale;


/**
 * Репутация: /rep, /rep +@user, /rep -@user.
 *
 * Каждый участник может один раз за период (VK_REP_COOLDOWN_SEC)
 * повысить или понизить репутацию другого участника. Свою
 * репутацию менять нельзя; Вождь клуба защищён от минуса.
 */
public class RepCommand
        implements Command {


    @Override
    public String getName() {


        return "rep";
    }


    @Override
    public String getDescription() {


        return "Репутация: /rep, /rep +@user, /rep -@user";
    }


    @Override
    public void execute(
            CommandContext context
    ) {


        String argument =
                context.getArgument(
                        0
                );


        if (
                argument == null
        ) {


            showOwnRep(
                    context
            );


            return;
        }


        int delta =
                1;


        String targetText =
                argument;


        if (
                targetText.startsWith(
                        "+"
                )
                        || targetText.startsWith(
                        "-"
                )
        ) {


            delta =
                    targetText.startsWith(
                            "-"
                    )
                            ? -1
                            : 1;


            targetText =
                    targetText.substring(
                            1
                    );
        }


        Long targetId =
                UserTargetResolver.resolve(
                        targetText
                );


        if (
                targetId == null
        ) {


            context.reply(
                    "❌ Укажи пользователя через @ или ID.\n\n"
                            + "Примеры:\n"
                            + "/rep +@id123456789\n"
                            + "/rep -@id123456789"
            );


            return;
        }


        if (
                targetId ==
                        context.getUserId()
        ) {


            context.reply(
                    "❌ Нельзя изменить репутацию самому себе."
            );


            return;
        }


        if (
                delta < 0
                        && targetId ==
                        context.getConfig()
                                .getPatronId()
        ) {


            context.reply(
                    "👑 Вождь клуба защищён от понижения репутации."
            );


            return;
        }


        context.getUserRepository()
                .ensureUser(
                        targetId
                );


        BotConfig config =
                context.getConfig();


        boolean applied =
                context.getUserRepository()
                        .changeRep(
                                targetId,
                                context.getUserId(),
                                delta
                        );


        if (
                !applied
        ) {


            long remaining =
                    context.getUserRepository()
                            .repCooldownRemainingMillis(
                                    context.getUserId(),
                                    targetId
                            );


            context.reply(
                    "⏳ Ты уже менял репутацию этому "
                            + "пользователю.\n"
                            + "Подожди ещё "
                            + formatMinutes(
                            remaining
                    )
                            + "."
            );


            return;
        }


        User target =
                context.getUserRepository()
                        .findById(
                                targetId
                        );


        context.getAuditRepository()
                .log(
                        context.getUserId(),
                        "REP",
                        targetId,
                        delta > 0
                                ? "+1"
                                : "-1",
                        "OK"
                );


        context.reply(
                (delta > 0
                        ? "✅ Ты повысил репутацию пользователя "
                        : "❌ Ты понизил репутацию пользователя ")
                        + safeName(
                        target
                )
                        + "\n\n"
                        + "⭐ Текущая репутация: "
                        + (
                        target == null
                                ? "?"
                                : target.getRep()
                )
        );
    }


    private void showOwnRep(
            CommandContext context
    ) {


        User user =
                context.getUser();


        if (
                user == null
        ) {


            context.reply(
                    "❌ Пользователь не найден."
            );


            return;
        }


        context.reply(
                "⭐ Твоя репутация: "
                        + user.getRep()
                        + "\n"
                        + "💰 Баланс: "
                        + bot.util.Text.number(
                        user.getBalance()
                )
                        + " ₽"
        );
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


            return "(пользователь)";
        }


        return user.getName();
    }


    private String formatMinutes(
            long millis
    ) {


        long seconds =
                Math.max(
                        millis,
                        0L
                ) / 1000L;


        if (
                seconds >= 60
        ) {


            return (seconds / 60)
                    + " мин.";
        }


        return seconds
                + " сек.";
    }
}