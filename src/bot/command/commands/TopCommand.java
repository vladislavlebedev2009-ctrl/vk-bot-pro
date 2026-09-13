package bot.command.commands;


import bot.command.Command;
import bot.command.CommandContext;
import bot.database.UserRepository;

import java.util.List;
import java.util.Locale;


/**
 * Рейтинг участников клуба: /top, /toprep, /topbalance.
 *
 * /top — по репутации; /toprep — репутация; /topbalance — баланс.
 * Метрику можно задать и аргументом: /top balance, /top messages.
 */
public class TopCommand
        implements Command {


    private static final int MAX_ROWS =
            50;


    @Override
    public String getName() {

        return "top";
    }


    @Override
    public String getDescription() {

        return "Рейтинг участников: /top [rep|balance|messages]";
    }


    @Override
    public List<String> getAliases() {

        return List.of(
                "toprep",
                "topbalance"
        );
    }


    @Override
    public void execute(
            CommandContext context
    ) {

        String metric =
                context.getArgument(
                        0
                );

        if (
                metric == null
                        || metric.isBlank()
        ) {

            metric =
                    "rep";
        }

        metric =
                metric.toLowerCase(
                        Locale.ROOT
                );

        if (
                !"rep".equals(
                        metric
                )
                        && !"messages".equals(
                        metric
                )
                        && !"balance".equals(
                        metric
                )
        ) {

            metric =
                    "rep";
        }

        int limit =
                Math.min(
                        Math.max(
                                context.getConfig()
                                        .getTopLimit(),
                                1
                        ),
                        MAX_ROWS
                );

        List<UserRepository.TopEntry> entries =
                context.getUserRepository()
                        .top(
                                metric,
                                limit
                        );

        if (
                entries.isEmpty()
        ) {

            context.reply(
                    "📊 Рейтинг пока пуст."
            );

            return;
        }

        String title =
                switch (
                        metric
                ) {

                    case "balance" -> "💰 TOP по балансу";

                    case "messages" -> "💬 TOP по сообщениям";

                    default -> "⭐ TOP по репутации";
                };

        StringBuilder message =
                new StringBuilder();

        message.append(
                title
        );

        message.append(
                "\n\n"
        );

        int position =
                1;

        for (
                UserRepository.TopEntry entry :
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
                    bot.util.Text.safe(
                            entry.name(),
                            "Не указано"
                    )
            );

            message.append(
                    " ("
            );

            message.append(
                    bot.util.Text.safe(
                            entry.callSign(),
                            "Не указан"
                    )
            );

            message.append(
                    ") — "
            );

            if (
                    "balance".equals(
                            metric
                    )
            ) {

                message.append(
                        bot.util.Text.number(
                                entry.value()
                        )
                );

                message.append(
                        " ₽"
                );

            } else {

                message.append(
                        entry.value()
                );
            }

            message.append(
                    "\n"
            );

            position++;
        }

        context.reply(
                message.toString()
        );
    }
}