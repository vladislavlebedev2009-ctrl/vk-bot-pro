package bot.command.commands;

import bot.command.Command;
import bot.command.CommandContext;
import bot.model.Rank;
import bot.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Список участников клуба: /users, /users online, /users &lt;ранг&gt;.
 *
 * Сортировка по званию (высший ранг первым). Композиция:
 * имя, VK (@id), звание, репутация, баланс, статус. Пагинация —
 * номер страницы числом.
 */
public class RegisteredUsersCommand
        implements Command {

    private static final int MAX_PER_PAGE =
            50;


    @Override
    public String getName() {

        return "users";
    }


    @Override
    public String getDescription() {

        return "Список участников: /users [online|<ранг>|страница]";
    }


    @Override
    public List<String> getAliases() {

        return List.of(
                "registered",
                "members",
                "roster"
        );
    }


    @Override
    public String getPermission() {

        return "users";
    }


    @Override
    public void execute(
            CommandContext context
    ) {

        if (
                !context.requirePermission(
                        "users"
                )
        ) {

            return;
        }

        String[] args =
                context.getArguments();

        int page =
                1;

        String query =
                null;

        Rank rank =
                null;

        String status =
                null;

        String sort =
                null;

        List<String> searchParts =
                new ArrayList<>();

        for (
                String arg :
                args
        ) {

            String lower =
                    arg.toLowerCase(
                            Locale.ROOT
                    );

            if (
                    lower.matches(
                            "\\d+"
                    )
            ) {

                page =
                        Math.max(
                                1,
                                Integer.parseInt(
                                        lower
                                )
                        );

                continue;
            }

            if (
                    lower.startsWith(
                            "rank:"
                    )
            ) {

                rank =
                        parseRank(
                                arg.substring(
                                        5
                                )
                        );

                continue;
            }

            if (
                    lower.startsWith(
                            "sort:"
                    )
            ) {

                sort =
                        arg.substring(
                                5
                        ).trim();

                continue;
            }

            if (
                    "online".equals(
                            lower
                    )
            ) {

                status =
                        "online";

                continue;
            }

            if (
                    "banned".equals(
                            lower
                    )
            ) {

                status =
                        "banned";

                continue;
            }

            if (
                    "muted".equals(
                            lower
                    )
            ) {

                status =
                        "muted";

                continue;
            }

            if (
                    "active".equals(
                            lower
                    )
            ) {

                status =
                        "active";

                continue;
            }

            Rank rankArg =
                    parseRank(
                            arg
                    );

            if (
                    rankArg != null
            ) {

                rank =
                        rankArg;

                continue;
            }

            searchParts.add(
                    arg
            );
        }


        if (
                !searchParts.isEmpty()
        ) {

            query =
                    String.join(
                            " ",
                            searchParts
                    );
        }


        int perPage =
                Math.min(
                        Math.max(
                                context.getConfig()
                                        .getTopLimit(),
                                1
                        ),
                        MAX_PER_PAGE
                );

        int total =
                context.getUserRepository()
                        .queryUsersCount(
                                query,
                                rank,
                                status
                        );

        int totalPages =
                Math.max(
                        1,
                        (
                                total + perPage - 1
                        ) / perPage
                );

        page =
                Math.min(
                        page,
                        totalPages
                );

        int offset =
                (page - 1)
                        * perPage;

        List<User> users =
                context.getUserRepository()
                        .queryUsers(
                                query,
                                rank,
                                status,
                                sort,
                                perPage,
                                offset
                        );


        StringBuilder message =
                new StringBuilder();

        message.append(
                "📋 УЧАСТНИКИ КЛУБА"
        );

        appendFilterDescription(
                message,
                query,
                rank,
                status,
                sort
        );

        message.append(
                "\n\n👥 Найдено: "
        );

        message.append(
                total
        );

        message.append(
                "\n\n"
        );

        if (
                users.isEmpty()
        ) {

            message.append(
                    "По заданным условиям никого не найдено."
            );

            context.reply(
                    message.toString()
            );

            return;
        }


        int number =
                offset
                        + 1;

        for (
                User user :
                users
        ) {

            message.append(
                    number
            );

            message.append(
                    ". "
            );

            message.append(
                    safe(
                            user.getName()
                    )
            );

            message.append(
                    " (@id"
            );

            message.append(
                    user.getId()
            );

            message.append(
                    ") — "
            );

            message.append(
                    rankText(
                            user
                    )
            );

            message.append(
                    " | ⭐ "
            );

            message.append(
                    user.getRep()
            );

            message.append(
                    " | 💰 "
            );

            message.append(
                    bot.util.Text.number(
                            user.getBalance()
                    )
            );

            message.append(
                    statusMark(
                            user
                    )
            );

            message.append(
                    "\n"
            );

            number++;
        }

        message.append(
                "\n📄 Страница "
        );

        message.append(
                page
        );

        message.append(
                " из "
        );

        message.append(
                totalPages
        );

        message.append(
                "\n\nКоманды: /users [online|banned|muted|active], "
        );

        message.append(
                "звание (например «Товарищ»), страница"
        );

        context.reply(
                message.toString()
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


    private void appendFilterDescription(
            StringBuilder message,
            String query,
            Rank rank,
            String status,
            String sort
    ) {

        StringBuilder filters =
                new StringBuilder();

        if (
                query != null
        ) {

            filters.append(
                    "; поиск: "
            );

            filters.append(
                    query
            );
        }

        if (
                rank != null
        ) {

            filters.append(
                    "; звание: "
            );

            filters.append(
                    rank.getDisplayName()
            );
        }

        if (
                status != null
        ) {

            filters.append(
                    "; статус: "
            );

            filters.append(
                    status
            );
        }

        if (
                sort != null
        ) {

            filters.append(
                    "; сортировка: "
            );

            filters.append(
                    sort
            );
        }

        if (
                filters.length() > 0
        ) {

            message.append(
                    " ("
            );

            message.append(
                    filters.substring(
                            2
                    )
            );

            message.append(
                    ")"
            );
        }
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

        return "Без звания";
    }


    private String statusMark(
            User user
    ) {

        if (
                user.isBanned()
        ) {

            return " 🔴";
        }

        if (
                user.isMuted()
        ) {

            return " 🟡";
        }

        return " 🟢";
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