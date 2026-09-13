package bot.command.commands;

import bot.command.Command;
import bot.command.CommandContext;
import bot.model.Rank;
import bot.model.User;
import bot.service.UserTargetResolver;

/**
 * Назначение званий: /setrank @id &lt;ранг | уровень&gt;.
 *
 * Звание задаётся русским названием («Товарищ», «Народный комиссар»),
 * идентификатором (TOVARISHCH) или номером уровня (1-10).
 * Нельзя назначить звание выше или равное своему, менять звание
 * себе или Вождю клуба — проверки централизованы в AccessControlService.
 */
public class SetRankCommand
        implements Command {


    @Override
    public String getName() {

        return "setrank";
    }


    @Override
    public String getDescription() {

        return "Изменить звание: /setrank @id <ранг | уровень>";
    }


    @Override
    public String getPermission() {

        return "setrank";
    }


    @Override
    public void execute(
            CommandContext context
    ) {

        if (
                !context.requirePermission(
                        "setrank"
                )
        ) {

            return;
        }

        Long targetId =
                UserTargetResolver.resolve(
                        context.getArgument(
                                0
                        )
                );

        String rankText =
                context.getArgument(
                        1
                );

        if (
                targetId == null
                        || rankText == null
        ) {

            context.reply(
                    "❌ Использование:\n\n"
                            + "/setrank @пользователь РАНГ\n"
                            + "/setrank ID РАНГ\n\n"
                            + "РАНГ — название (" +
                            Rank.TOVARISHCH.getDisplayName() +
                            ") или номер уровня (1-10).\n\n"
                            + "Доступные звания:\n"
                            + getRanks()
            );

            return;
        }

        Rank newRank =
                parseRank(
                        rankText
                );

        if (
                newRank == null
        ) {

            context.reply(
                    "❌ Неизвестное звание: "
                            + rankText
                            + ".\n\n"
                            + "Доступные звания:\n"
                            + getRanks()
            );

            return;
        }

        User targetUser =
                context.getUserRepository()
                        .findById(
                                targetId
                        );

        if (
                !context.getAccessControl()
                        .canChangeRank(
                                context,
                                targetUser
                        )
        ) {

            context.getAuditRepository()
                    .log(
                            context.getUserId(),
                            "SETRANK",
                            targetId,
                            rankText,
                            "DENIED"
                    );

            return;
        }

        /*
         * Нельзя назначить звание, равное или выше собственного
         * (Вождь клуба обходит проверку выше, ему всё можно).
         */
        if (
                !context.isPatron()
                        && newRank.getLevel()
                        >= context.getRank()
                        .getLevel()
        ) {

            context.reply(
                    "⛔ Нельзя назначить звание, равное или выше собственного."
            );

            context.getAuditRepository()
                    .log(
                            context.getUserId(),
                            "SETRANK",
                            targetId,
                            newRank.name(),
                            "DENIED"
                    );

            return;
        }

        String oldRankText =
                targetUser != null
                        && targetUser.getRankEnum() != null
                        ? targetUser.getRankEnum()
                        .getDisplayName()
                        : "Без звания";

        context.getUserRepository()
                .ensureUser(
                        targetId
                );

        context.getUserRepository()
                .setRank(
                        targetId,
                        newRank.name()
                );

        context.reply(
                "✅ Звание пользователя изменено:\n\n"
                        + "👤 "
                        + oldRankText
                        + " → "
                        + newRank.getDisplayName()
                        + " (уровень "
                        + newRank.getLevel()
                        + ")"
        );

        context.getAuditRepository()
                .log(
                        context.getUserId(),
                        "SETRANK",
                        targetId,
                        oldRankText
                                + " -> "
                                + newRank.getDisplayName(),
                        "OK"
                );
    }


    private Rank parseRank(
            String text
    ) {

        Rank rank =
                Rank.fromString(
                        text
                );

        if (
                rank != null
        ) {

            return rank;
        }

        try {

            return Rank.fromLevel(
                    Integer.parseInt(
                            text.trim()
                    )
            );

        } catch (
                NumberFormatException e
        ) {

            return null;
        }
    }


    private String getRanks() {

        StringBuilder result =
                new StringBuilder();

        for (
                Rank rank :
                Rank.values()
        ) {

            result.append(
                    "• "
            );

            result.append(
                    rank.getDisplayName()
            );

            result.append(
                    " ("
            );

            result.append(
                    rank.name()
            );

            result.append(
                    ", уровень "
            );

            result.append(
                    rank.getLevel()
            );

            result.append(
                    ")\n"
            );
        }

        return result.toString();
    }
}