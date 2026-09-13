package bot.command.commands;


import bot.command.Command;
import bot.command.CommandContext;

import java.util.List;


/**
 * Статистика клуба: /stats.
 *
 * Общая статистика базы: участники, сообщения, репутация,
 * баланс, активные наказания и распределение по званиям.
 * Требует звания «Член Президиума» (уровень 8).
 */
public class StatsCommand
        implements Command {


    @Override
    public String getName() {

        return "stats";
    }


    @Override
    public String getDescription() {

        return "Статистика клуба";
    }


    @Override
    public String getPermission() {

        return "stats";
    }


    @Override
    public void execute(
            CommandContext context
    ) {

        if (
                !context.requirePermission(
                        "stats"
                )
        ) {

            return;
        }

        int registered =
                context.getUserRepository()
                        .countRegistered();

        double averageRep =
                context.getUserRepository()
                        .averageRep();

        long totalBalance =
                context.getUserRepository()
                        .totalBalance();

        int activePunishments =
                context.getPunishmentRepository()
                        .countActive();

        List<bot.database.UserRepository.RankCount> distribution =
                context.getUserRepository()
                        .rankDistribution();

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
                bot.util.Text.number(
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
                    bot.database.UserRepository.RankCount entry :
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

        context.reply(
                message.toString()
        );
    }
}