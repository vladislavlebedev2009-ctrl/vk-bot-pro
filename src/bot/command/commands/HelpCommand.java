package bot.command.commands;

import bot.command.Command;
import bot.command.CommandContext;
import bot.command.CommandDispatcher;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HelpCommand
        implements Command {


    private final CommandDispatcher dispatcher;


    private static final List<Group> GROUPS =
            List.of(
                    new Group(
                            "📝 Регистрация и профиль",
                            List.of(
                                    "register",
                                    "profile",
                                    "balance",
                                    "pay",
                                    "rep",
                                    "top"
                            )
                    ),
                    new Group(
                            "🏅 Ранги",
                            List.of(
                                    "rank",
                                    "ranks"
                            )
                    ),
                    new Group(
                            "💬 Беседы",
                            List.of(
                                    "connect",
                                    "disconnect"
                            )
                    ),
                    new Group(
                            "📢 Сообщество",
                            List.of(
                                    "post"
                            )
                    ),
                    new Group(
                            "🛠 Модерация",
                            List.of(
                                    "ban",
                                    "unban",
                                    "warn",
                                    "unwarn",
                                    "mute",
                                    "unmute"
                            )
                    ),
                    new Group(
                            "⚙️ Управление",
                            List.of(
                                    "users",
                                    "setrank",
                                    "history",
                                    "addbot"
                            )
                    ),
                    new Group(
                            "📊 Статистика",
                            List.of(
                                    "stats"
                            )
                    ),
                    new Group(
                            "ℹ️ Сервис",
                            List.of(
                                    "help",
                                    "ping",
                                    "probe"
                            )
                    )
            );


    public HelpCommand(
            CommandDispatcher dispatcher
    ) {

        this.dispatcher =
                dispatcher;
    }


    @Override
    public String getName() {

        return "help";
    }


    @Override
    public String getDescription() {

        return "Показать список команд";
    }


    @Override
    public boolean requiresRegistration() {

        return false;
    }


    @Override
    public void execute(
            CommandContext context
    ) {

        Map<String, Command> byName =
                new LinkedHashMap<>();

        for (
                Command command :
                dispatcher.getUniqueCommands()
        ) {

            byName.put(
                    command.getName(),
                    command
            );
        }


        StringBuilder message =
                new StringBuilder();

        message.append(
                "📖 СПИСОК КОМАНД\n"
        );

        message.append(
                "◾ — требуется звание\n\n"
        );


        for (
                Group group :
                GROUPS
        ) {

            StringBuilder items =
                    new StringBuilder();

            for (
                    String name :
                    group.commandNames
            ) {

                Command command =
                        byName.get(
                                name
                        );

                if (
                        command == null
                ) {

                    continue;
                }

                String permission =
                        command.getPermission();

                if (
                        permission != null
                                && !context.getAccessControl()
                                .canUse(
                                        context,
                                        permission
                                )
                ) {

                    continue;
                }

                items.append(
                        "\n/"
                );

                items.append(
                        command.getName()
                );

                appendAliases(
                        items,
                        command.getAliases()
                );

                items.append(
                        " — "
                );

                items.append(
                        command.getDescription()
                );

                if (
                        permission != null
                ) {

                    items.append(
                            " ◾"
                    );
                }
            }

            if (
                    items.length() > 0
            ) {

                message.append(
                        "\n"
                );

                message.append(
                        group.title
                );

                message.append(
                        ":"
                );

                message.append(
                        items
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


    private void appendAliases(
            StringBuilder message,
            List<String> aliases
    ) {

        if (
                aliases == null
                        || aliases.isEmpty()
        ) {

            return;
        }

        message.append(
                " ("
        );

        for (
                int i = 0;
                i < aliases.size();
                i++
        ) {

            message.append(
                    "/"
            );

            message.append(
                    aliases.get(
                            i
                    )
            );

            if (
                    i < aliases.size()
                            - 1
            ) {

                message.append(
                        ", "
                );
            }
        }

        message.append(
                ")"
        );
    }


    private record Group(
            String title,
            List<String> commandNames
    ) {
    }
}