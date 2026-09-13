package bot.command;

import bot.api.VkApi;
import bot.command.commands.AddBotCommand;
import bot.command.commands.BalanceCommand;
import bot.command.commands.ConnectCommand;
import bot.command.commands.DisconnectCommand;
import bot.command.commands.HelpCommand;
import bot.command.commands.HistoryCommand;
import bot.command.commands.LinkCommand;
import bot.command.commands.ModerationCommand;
import bot.command.commands.PayCommand;
import bot.command.commands.PingCommand;
import bot.command.commands.PostCommand;
import bot.command.commands.ProbeCommand;
import bot.command.commands.ProfileCommand;
import bot.command.commands.RankCommand;
import bot.command.commands.RanksCommand;
import bot.command.commands.RegisterCommand;
import bot.command.commands.RegisteredUsersCommand;
import bot.command.commands.RepCommand;
import bot.command.commands.SetRankCommand;
import bot.command.commands.StatsCommand;
import bot.command.commands.TopCommand;
import bot.config.BotConfig;
import bot.database.AuditRepository;
import bot.database.ChatRepository;
import bot.database.PunishmentRepository;
import bot.database.UserRepository;
import bot.model.Rank;
import bot.model.User;
import bot.service.AccessControlService;
import bot.service.CommandRateLimiter;
import bot.service.LinkService;
import bot.service.Logger;
import bot.service.RegistrationService;
import bot.service.VkCommunityService;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;

public class CommandDispatcher {

    private final VkApi vkApi;

    private final VkCommunityService communityService;

    private final UserRepository userRepository;

    private final RegistrationService registrationService;

    private final ChatRepository chatRepository;

    private final PunishmentRepository punishmentRepository;

    private final AuditRepository auditRepository;

    private final AccessControlService accessControl;

    private final CommandRateLimiter rateLimiter;

    private final LinkService linkService;

    private final BotConfig config;

    private final Map<String, Command> commands =
            new LinkedHashMap<>();

    private static final Logger logger =
            new Logger(
                    CommandDispatcher.class
            );


    public CommandDispatcher(
            VkApi vkApi,
            VkCommunityService communityService,
            UserRepository userRepository,
            RegistrationService registrationService,
            ChatRepository chatRepository,
            PunishmentRepository punishmentRepository,
            AuditRepository auditRepository,
            BotConfig config,
            AccessControlService accessControl,
            CommandRateLimiter rateLimiter,
            LinkService linkService
    ) {

        this.vkApi =
                vkApi;

        this.communityService =
                communityService;

        this.userRepository =
                userRepository;

        this.registrationService =
                registrationService;

        this.chatRepository =
                chatRepository;

        this.punishmentRepository =
                punishmentRepository;

        this.auditRepository =
                auditRepository;

        this.config =
                config;

        this.accessControl =
                accessControl;

        this.rateLimiter =
                rateLimiter;

        this.linkService =
                linkService;

        registerCommands();
    }


    private void registerCommands() {

        register(
                new PingCommand()
        );


        register(
                new BalanceCommand()
        );


        register(
                new ProfileCommand()
        );


        register(
                new ProbeCommand()
        );


        register(
                new RegisterCommand(
                        registrationService
                )
        );


        register(
                new HelpCommand(
                        this
                )
        );


        register(
                new RegisteredUsersCommand()
        );


        register(
                new SetRankCommand()
        );


        register(
                new AddBotCommand()
        );


        register(
                new PostCommand(
                        communityService
                )
        );


        register(
                new ConnectCommand()
        );


        register(
                new DisconnectCommand()
        );


        register(
                new RepCommand()
        );


        register(
                new PayCommand()
        );


        register(
                new TopCommand()
        );


        register(
                new RankCommand()
        );


        register(
                new RanksCommand()
        );


        register(
                new StatsCommand()
        );


        register(
                new HistoryCommand()
        );


        register(
                new LinkCommand(
                        linkService
                )
        );


        register(
                new ModerationCommand(
                        "ban",
                        "Исключить пользователя из беседы: /ban @id причина",
                        "ban"
                )
        );


        register(
                new ModerationCommand(
                        "unban",
                        "Снять бан с пользователя",
                        "unban"
                )
        );


        register(
                new ModerationCommand(
                        "warn",
                        "Выдать предупреждение: /warn @id причина",
                        "warn"
                )
        );


        register(
                new ModerationCommand(
                        "unwarn",
                        "Снять предупреждение",
                        "unwarn"
                )
        );


        register(
                new ModerationCommand(
                        "mute",
                        "Выдать мут: /mute @id минуты причина",
                        "mute"
                )
        );


        register(
                new ModerationCommand(
                        "unmute",
                        "Снять мут",
                        "unmute"
                )
        );
    }


    private void register(
            Command command
    ) {

        commands.put(
                command.getName()
                        .toLowerCase(
                                Locale.ROOT
                        ),
                command
        );


        if (
                command.getAliases() != null
        ) {

            for (
                    String alias :
                    command.getAliases()
            ) {

                commands.put(
                        alias.toLowerCase(
                                Locale.ROOT
                        ),
                        command
                );
            }
        }
    }


    public void dispatch(
            String text,
            long userId,
            int peerId
    ) {

        if (
                text == null
                        || text.isBlank()
        ) {

            return;
        }


        userRepository.ensureUser(
                userId
        );


        User user =
                userRepository.findById(
                        userId
                );


        /*
         * Обработка регистрации
         */

        if (
                registrationService.isActive(
                        userId
                )
        ) {

            /*
             * Любая команда с префиксом во время регистрации
             * отменяет текущую сессию регистрации.
             */

            if (
                    text.trim()
                            .startsWith(
                            config.getCommandPrefix()
                    )
            ) {

                registrationService.cancel(
                        userId
                );


                vkApi.sendMessage(
                        peerId,
                        "❌ Регистрация отменена.\n\n"
                                + "Для новой регистрации используй /register"
                );


                return;
            }


            String response =
                    registrationService.process(
                            userId,
                            text
                    );


            if (
                    response != null
            ) {

                vkApi.sendMessage(
                        peerId,
                        response
                );
            }


            return;
        }


        /*
         * Проверка, является ли сообщение командой.
         * Обычные сообщения (не начинающиеся с префикса) бот
         * не обрабатывает и не комментирует — фраза «Ты не
         * зарегистрирован» присылается только на команды.
         */

        String prefix =
                config.getCommandPrefix();

        String trimmedText =
                text.trim();


        if (
                !trimmedText.startsWith(
                        prefix
                )
        ) {

            return;
        }


        String commandText =
                trimmedText.substring(
                        prefix.length()
                ).trim();


        if (
                commandText.isBlank()
        ) {

            return;
        }


        String[] parts =
                commandText.split(
                        "\\s+"
                );


        String commandName =
                parts[0]
                        .toLowerCase(
                                Locale.ROOT
                        );


        Command command =
                commands.get(
                        commandName
                );


        if (
                command == null
        ) {

            vkApi.sendMessage(
                    peerId,
                    "❌ Неизвестная команда.\n\n"
                            + "Используй /help"
            );


            return;
        }


        /*
         * Registration gate: срабатывает ТОЛЬКО для команд, которым
         * действительно нужна регистрация
         * ({@link Command#requiresRegistration()}).
         * /register, /start и /help доступны без регистрации.
         * Обычные сообщения до этого блока вообще не доходят
         * (см. проверку префикса выше).
         */

        if (
                !registrationService.isRegistered(
                        userId
                )
                        && userId !=
                        config.getPatronId()
                        && command.requiresRegistration()
        ) {

            vkApi.sendMessage(
                    peerId,
                    "⛔ Ты не зарегистрирован! Используй /register."
            );


            return;
        }


        /*
         * Rate limit: количество команд за окно времени.
         * Для админов (Вождь клуба и высший состав, уровень ≥ 8)
         * используется отдельный, более щедрый лимит.
         */

        if (
                !isAdmin(
                        userId,
                        user
                )
                        && !rateLimiter.allow(
                        userId,
                        config.getRateLimit(),
                        config.getRateLimitWindowMillis()
                )
        ) {

            vkApi.sendMessage(
                    peerId,
                    "⏳ Слишком много команд подряд.\n"
                            + "Подожди немного и попробуй снова."
            );


            return;
        }


        /*
         * Cooldown одной команды: пауза между повторным запуском
         * ОДНОЙ И ТОЙ ЖЕ команды. Для админов — отдельный лимит.
         * Регистрация и обычная переписка не затрагиваются.
         */
        long commandCooldownMillis =
                isAdmin(
                        userId,
                        user
                )
                        ? config.getAdminCommandCooldownMillis()
                        : config.getCommandCooldownMillis();

        long remainingCooldown =
                rateLimiter.commandCooldownMillis(
                        userId,
                        commandName,
                        commandCooldownMillis
                );

        if (
                remainingCooldown == -1L
        ) {

            return;
        }

        if (
                remainingCooldown > 0
        ) {

            logger.info(
                    "Cooldown команды "
                            + commandName
                            + " для пользователя "
                            + userId
                            + ": осталось "
                            + remainingCooldown
                            + " мс"
            );

            vkApi.sendMessage(
                    peerId,
                    "⏳ Подождите "
                            + Math.max(
                            1,
                            (remainingCooldown + 999) / 1000
                    )
                            + " секунд перед повторным "
                            + "использованием команды."
            );


            return;
        }


        CommandContext context =
                new CommandContext(
                        text,
                        userId,
                        peerId,
                        vkApi,
                        userRepository,
                        chatRepository,
                        punishmentRepository,
                        auditRepository,
                        user,
                        config,
                        accessControl
                );


        try {

            command.execute(
                    context
            );

            /*
             * Счётчик выполненной команды (активность).
             * Счётчик сообщений уже учтён в MessageProcessingService.
             */
            userRepository.incrementCommandCount(
                    userId
            );

            /*
             * Cooldown отсчитывается от последнего УСПЕШНОГО запуска.
             */
            rateLimiter.recordCommandExecution(
                    userId,
                    commandName
            );


        } catch (
                Exception e
        ) {

            logger.error(
                    "Ошибка при выполнении команды: "
                            + commandName,
                    e
            );


            vkApi.sendMessage(
                    peerId,
                    "❌ Произошла ошибка при выполнении команды."
            );
        }
    }


    private boolean isAdmin(
            long userId,
            User user
    ) {

        if (
                userId ==
                        config.getPatronId()
        ) {

            return true;
        }


        if (
                user == null
        ) {

            return false;
        }


        Rank rank =
                user.getRankEnum();

        return rank != null
                && rank.getLevel()
                >= Rank.CHLEN_PREZIDIUMA
                .getLevel();
    }


    public Map<String, Command> getCommands() {

        return commands;
    }


    public Collection<Command> getUniqueCommands() {

        return new LinkedHashSet<>(
                commands.values()
        );
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


    public BotConfig getConfig() {

        return config;
    }
}