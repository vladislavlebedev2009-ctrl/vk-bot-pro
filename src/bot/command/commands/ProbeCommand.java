package bot.command.commands;


import bot.command.Command;
import bot.command.CommandContext;
import bot.model.User;
import bot.service.UserTargetResolver;


public class ProbeCommand
        implements Command {


    @Override
    public String getName() {


        return "probe";
    }




    @Override
    public String getDescription() {


        return "Пробив информации о пользователе";
    }




    @Override
    public void execute(
            CommandContext context
    ) {


        Long targetId =
                UserTargetResolver.resolve(
                        context.getArgument(
                                0
                        )
                );




        if (
                targetId == null
        ) {


            context.reply(
                    "❌ Использование:\n"
                            + "/probe @пользователь\n"
                            + "/probe ID"
            );


            return;
        }




        context.getUserRepository()
                .ensureUser(
                        targetId
                );




        User user =
                context.getUserRepository()
                        .findById(
                                targetId
                        );




        String muteStatus;




        if (
                user.getMuteEnd()
                        > System.currentTimeMillis()
        ) {


            long remaining =
                    (
                            user.getMuteEnd()
                                    - System.currentTimeMillis()
                    )
                            / 60_000L;




            muteStatus =
                    "🔇 Да, ещё "
                            + remaining
                            + " мин.";


        } else {


            muteStatus =
                    "🔊 Нет";
        }




        context.reply(
                "🔎 ПРОБИВ ПОЛЬЗОВАТЕЛЯ\n\n"

                        + "🆔 ID: "
                        + user.getId()
                        + "\n"

                        + "📛 Имя: "
                        + safe(
                        user.getName()
                )
                        + "\n"

                        + "📞 Позывной: "
                        + safe(
                        user.getCallSign()
                )
                        + "\n"

                        + "🏅 Ранг: "
                        + rankText(
                        user
                )
                        + "\n\n"

                        + "💰 Баланс: "
                        + bot.util.Text.number(
                        user.getBalance()
                )
                        + "\n"

                        + "⭐ Репутация: "
                        + user.getRep()
                        + "\n"

                        + "⚠ Предупреждения: "
                        + user.getWarns()
                        + "\n"

                        + "🔨 Бан: "
                        + (
                        user.isBanned()
                                ? "Да"
                                : "Нет"
                )
                        + "\n"

                        + muteStatus
        );
    }


    private String rankText(
            User user
    ) {

        if (
                user.getRankEnum() != null
        ) {

            return user.getRankEnum()
                    .getDisplayName();
        }

        return "Не указано";
    }




    private String safe(
            String value
    ) {


        if (
                value == null
                        || value.isBlank()
        ) {


            return "Не указано";
        }




        return value;
    }
}