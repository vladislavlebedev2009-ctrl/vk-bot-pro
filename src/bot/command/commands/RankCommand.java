package bot.command.commands;


import bot.command.Command;
import bot.command.CommandContext;
import bot.model.Rank;
import bot.model.User;


/**
 * Твоё звание в клубе: /rank.
 *
 * Показывает текущее звание, римскую ступень, уровень и требования
 * следующей ступени. Пользователь без звания получает подсказку
 * пройти регистрацию (после неё выдаётся «Кандидат»).
 */
public class RankCommand
        implements Command {


    @Override
    public String getName() {

        return "rank";
    }


    @Override
    public String getDescription() {

        return "Твоё звание в клубе";
    }


    @Override
    public void execute(
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

        Rank rank =
                user.getRankEnum();

        if (
                rank == null
        ) {

            context.reply(
                    "🎖 У тебя пока нет звания.\n\n"
                            + "Пройди регистрацию "
                            + "(/register), чтобы получить"
                            + " стартовое звание «Кандидат»."
            );

            return;
        }

        context.reply(
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
                                context,
                                rank
                        )
                )
        );
    }


    private int countByRank(
            CommandContext context,
            Rank rank
    ) {

        var distribution =
                context.getUserRepository()
                        .rankDistribution();

        return distribution
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
}