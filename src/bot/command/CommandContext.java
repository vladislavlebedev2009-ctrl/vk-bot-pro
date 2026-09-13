package bot.command;

import bot.api.VkApi;
import bot.config.BotConfig;
import bot.database.AuditRepository;
import bot.database.ChatRepository;
import bot.database.PunishmentRepository;
import bot.database.UserRepository;
import bot.model.Rank;
import bot.model.User;
import bot.service.AccessControlService;

public class CommandContext {

    private final String commandText;

    private final long userId;

    private final int peerId;

    private final VkApi vkApi;

    private final UserRepository userRepository;

    private final ChatRepository chatRepository;

    private final PunishmentRepository punishmentRepository;

    private final AuditRepository auditRepository;

    private final BotConfig config;

    private final User user;

    private final AccessControlService accessControl;


    public CommandContext(
            String commandText,
            long userId,
            int peerId,
            VkApi vkApi,
            UserRepository userRepository,
            ChatRepository chatRepository,
            PunishmentRepository punishmentRepository,
            AuditRepository auditRepository,
            User user,
            BotConfig config,
            AccessControlService accessControl
    ) {

        this.commandText =
                commandText;

        this.userId =
                userId;

        this.peerId =
                peerId;

        this.vkApi =
                vkApi;

        this.userRepository =
                userRepository;

        this.chatRepository =
                chatRepository;

        this.punishmentRepository =
                punishmentRepository;

        this.auditRepository =
                auditRepository;

        this.user =
                user;

        this.config =
                config;

        this.accessControl =
                accessControl;
    }


    public String getCommandText() {

        return commandText;
    }


    public long getUserId() {

        return userId;
    }


    public int getPeerId() {

        return peerId;
    }


    public VkApi getVkApi() {

        return vkApi;
    }


    public UserRepository getUserRepository() {

        return userRepository;
    }


    public ChatRepository getChatRepository() {

        return chatRepository;
    }


    public PunishmentRepository getPunishmentRepository() {

        return punishmentRepository;
    }


    public AuditRepository getAuditRepository() {

        return auditRepository;
    }


    public BotConfig getConfig() {

        return config;
    }


    public User getUser() {

        return user;
    }


    public AccessControlService getAccessControl() {

        return accessControl;
    }


    public String getArgument(
            int index
    ) {

        String[] arguments =
                getArguments();


        if (
                index < 0
                        || index >= arguments.length
        ) {

            return null;
        }


        return arguments[index];
    }


    public String[] getArguments() {

        if (
                commandText == null
                        || commandText.isBlank()
        ) {

            return new String[0];
        }

        java.util.List<String> parts =
                mergeMentions(
                        commandText.trim()
                                .split(
                                        "\\s+"
                                )
                );

        if (
                parts.size() <= 1
        ) {

            return new String[0];
        }

        return parts.subList(
                        1,
                        parts.size()
                )
                .toArray(
                        new String[0]
                );
    }


    /*
     * Склеивает обратно упоминание VK вида [id123|Имя Фамилия],
     * которое при разбиении по пробелам распалось на части.
     */
    private static java.util.List<String> mergeMentions(
            String[] parts
    ) {

        java.util.List<String> merged =
                new java.util.ArrayList<>(
                        parts.length
                );

        for (
                int i = 0;
                i < parts.length;
                i++
        ) {

            String part =
                    parts[i];

            if (
                    part.startsWith(
                            "[id"
                    )
                            && !part.endsWith(
                            "]"
                    )
            ) {

                StringBuilder joined =
                        new StringBuilder(
                                part
                        );

                while (
                        i + 1 < parts.length
                                && !joined
                                .toString()
                                .endsWith(
                                        "]"
                                )
                ) {

                    joined.append(
                            " "
                    );

                    joined.append(
                            parts[i + 1]
                    );

                    i++;
                }

                merged.add(
                        joined.toString()
                );

            } else {

                merged.add(
                        part
                );
            }
        }

        return merged;
    }


    public String getArgumentsText() {

        String[] arguments =
                getArguments();


        if (
                arguments.length == 0
        ) {

            return "";
        }


        return String.join(
                " ",
                arguments
        );
    }


    public Rank getRank() {

        if (
                user == null
        ) {

            return null;
        }


        return user.getRankEnum();
    }


    public boolean isPatron() {

        return userId ==
                config.getPatronId();
    }


    public boolean requirePermission(
            String permission
    ) {

        return accessControl.requirePermission(
                this,
                permission
        );
    }


    public void reply(
            String message
    ) {

        vkApi.sendMessage(
                peerId,
                message
        );
    }
}