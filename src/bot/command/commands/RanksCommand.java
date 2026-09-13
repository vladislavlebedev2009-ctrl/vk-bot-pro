package bot.command.commands;


import bot.command.Command;
import bot.command.CommandContext;
import bot.model.Rank;
import bot.model.User;

import java.util.List;


/**
 * Список всех званий клуба: /ranks.
 *
 * Показывает шкалу званий (высшее первым), римские ступени
 * и количества участников по каждому званию из статистики.
 */
public class RanksCommand
        implements Command {


    @Override
    public String getName() {

        return "ranks";
    }


    @Override
    public String getDescription() {

        return "Все звания клуба";
    }


    @Override
    public void execute(
            CommandContext context
    ) {

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
                    isCurrent(
                            context,
                            rank
                    )
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

        context.reply(
                message.toString()
        );
    }


    private boolean isCurrent(
            CommandContext context,
            Rank rank
    ) {

        User user =
                context.getUser();

        if (
                user == null
        ) {

            return false;
        }

        Rank current =
                user.getRankEnum();

        return current != null
                && current == rank;
    }
}