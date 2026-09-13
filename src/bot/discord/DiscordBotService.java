package bot.discord;

import bot.config.BotConfig;
import bot.database.AuditRepository;
import bot.database.ChatRepository;
import bot.database.PunishmentRepository;
import bot.database.UserRepository;
import bot.service.AccessControlService;
import bot.service.LinkService;
import bot.service.Logger;
import bot.service.RegistrationService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Жизненный цикл Discord-бота (JDA).
 *
 * Запускается вместе с VK-ботом, но как ПОПУТНОЕ дополнение: отсутствие
 * переменных/ошибки подключения/rate-limit НЕ ломают VK-часть. Токен
 * читается только из окружения (DISCORD_TOKEN), в коде и логе его нет.
 * Интент message content не включается — работаем исключительно
 * slash-командами.
 */
public class DiscordBotService {

    /**
     * Терпим к отсутствию соединения с Gateway, но ждём статус READY,
     * чтобы команды регистрировались уже после подключения.
     */
    private static final int READY_TIMEOUT_SECONDS =
            15;

    private static final Logger logger =
            new Logger(
                    DiscordBotService.class
            );

    private final BotConfig config;

    private final LinkService linkService;

    private final UserRepository userRepository;

    private final PunishmentRepository punishmentRepository;

    private final AuditRepository auditRepository;

    private final ChatRepository chatRepository;

    private final RegistrationService registrationService;

    private final AccessControlService accessControl;

    private JDA jda;

    private boolean started;


    public DiscordBotService(
            BotConfig config,
            LinkService linkService,
            UserRepository userRepository,
            PunishmentRepository punishmentRepository,
            AuditRepository auditRepository,
            ChatRepository chatRepository,
            RegistrationService registrationService,
            AccessControlService accessControl
    ) {

        this.config =
                config;

        this.linkService =
                linkService;

        this.userRepository =
                userRepository;

        this.punishmentRepository =
                punishmentRepository;

        this.auditRepository =
                auditRepository;

        this.chatRepository =
                chatRepository;

        this.registrationService =
                registrationService;

        this.accessControl =
                accessControl;
    }


    public boolean isStarted() {

        return started;
    }


    /**
     * Запуск Discord-бота. Корректно обрабатывает случаи:
     *  - DISCORD_ENABLED=false или не задан DISCORD_TOKEN — VK не трогаем;
     *  - ошибка подключения/невалидный токен/нет доступа к Gateway —
     *    логируем и продолжаем работать (VK-бот живёт);
     *  - rate limit API — JDA сам ставит запросы в очередь,
     *    ошибки отправки фиксируются в лог, а не роняют бота.
     */
    public void start() {

        if (
                !config.isDiscordEnabled()
        ) {

            logger.info(
                    "Discord отключён (DISCORD_ENABLED=false). "
                            + "VK-бот продолжает работать."
            );

            return;
        }

        if (
                !config.hasDiscordToken()
        ) {

            logger.warning(
                    "Discord включён, но не задан DISCORD_TOKEN — "
                            + "Discord-бот не запускается. "
                            + "VK-бот продолжает работать."
            );

            return;
        }

        /*
         * Токену нужен shutdown-подобный уровень лога: JDA пишет
         * много служебных строк, а бот сам логирует свои события.
         */
        System.setProperty(
                "org.slf4j.simpleLogger.defaultLogLevel",
                "warn"
        );

        try {

            jda =
                    JDABuilder.createDefault(
                                    config.getDiscordToken()
                            )
                            .setEnabledIntents(
                                    EnumSet.noneOf(
                                            GatewayIntent.class
                                    )
                            )
                            .build();

        } catch (
                Exception e
        ) {

            logger.error(
                    "Не удалось инициализировать Discord "
                            + "(проверь DISCORD_TOKEN). "
                            + "VK-бот продолжает работать.",
                    e
            );

            return;
        }

        /*
         * Ждём READY с таймаутом: если бот не смог подключиться
         * к Gateway за отведённый срок, Discord-часть просится
         * в лог как предупреждение, но VK-бот продолжает жить.
         */
        CountDownLatch readyLatch =
                new CountDownLatch(
                        1
                );

        jda.addEventListener(
                new ListenerAdapter() {

                    @Override
                    public void onReady(
                            ReadyEvent event
                    ) {

                        readyLatch.countDown();
                    }
                }
        );

        try {

            boolean ready =
                    readyLatch.await(
                            READY_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    );

            if (
                    !ready
            ) {

                jda.shutdown();

                logger.warning(
                        "Discord: нет соединения с Gateway за "
                                + READY_TIMEOUT_SECONDS
                                + " секунд — Discord-бот отключён. "
                                + "VK-бот продолжает работать."
                );

                return;
            }

        } catch (
                InterruptedException e
        ) {

            Thread.currentThread()
                    .interrupt();

            jda.shutdown();

            logger.warning(
                    "Запуск Discord прерван. "
                            + "VK-бот продолжает работать."
            );

            return;
        }

        registerCommands();

        jda.addEventListener(
                new DiscordCommandHandler(
                        config,
                        linkService,
                        userRepository,
                        punishmentRepository,
                        auditRepository,
                        chatRepository,
                        registrationService,
                        accessControl
                )
        );

        logger.info(
                "Discord-бот запущен (slash-команды, без "
                        + "Message Content intent)"
        );

        started =
                true;
    }


    private void registerCommands() {

        List<CommandData> commands =
                commandList();

        Guild guild =
                config.getDiscordGuildId() > 0
                        ? jda.getGuildById(
                        config.getDiscordGuildId()
                )
                        : null;

        if (
                guild == null
        ) {

            logger.warning(
                    "Discord: сервер с ID "
                            + config.getDiscordGuildId()
                            + " не найден (проверь DISCORD_GUILD_ID "
                            + "и что бот добавлен на сервер). "
                            + "Команды зарегистрируются глобально."
            );

            jda.updateCommands()
                    .addCommands(
                            commands
                    )
                    .queue(
                            success ->
                                    logger.info(
                                            "Discord: команды "
                                                    + "зарегистрированы"
                                    ),
                            failure ->
                                    logger.error(
                                            "Discord: не удалось "
                                                    + "зарегистрировать "
                                                    + "команды",
                                            failure
                                    )
                    );

            return;
        }

        guild.updateCommands()
                .addCommands(
                        commands
                )
                .queue(
                        success ->
                                logger.info(
                                        "Discord: команды "
                                                + "зарегистрированы "
                                                + "на сервере "
                                                + guild.getId()
                                ),
                        failure ->
                                logger.error(
                                        "Discord: не удалось "
                                                + "зарегистрировать "
                                                + "команды",
                                        failure
                                )
                );
    }


    private List<CommandData> commandList() {

        List<CommandData> commands =
                new ArrayList<>();

        commands.add(
                Commands.slash(
                        "help",
                        "Справка по командам Discord-бота"
                )
        );

        commands.add(
                Commands.slash(
                        "register",
                        "Статус регистрации твоего профиля"
                )
        );

        commands.add(
                Commands.slash(
                        "link",
                        "Привязать Discord к VK-профилю по коду из VK"
                ).addOption(
                        OptionType.STRING,
                        "code",
                        "Код из команды /link в VK-боте",
                        true
                )
        );

        commands.add(
                Commands.slash(
                        "profile",
                        "Карточка твоего профиля (общие данные с VK)"
                )
        );

        commands.add(
                Commands.slash(
                        "rank",
                        "Твоё звание в клубе"
                )
        );

        commands.add(
                Commands.slash(
                        "ranks",
                        "Все звания клуба"
                )
        );

        commands.add(
                Commands.slash(
                        "balance",
                        "Твой баланс"
                )
        );

        commands.add(
                Commands.slash(
                        "top",
                        "TOP участников по репутации"
                )
        );

        commands.add(
                Commands.slash(
                        "stats",
                        "Статистика клуба (с 8 уровня)"
                )
        );

        commands.add(
                Commands.slash(
                        "history",
                        "История и журнал действий (с 4 уровня)"
                )
        );

        return commands;
    }


    public void stop() {

        if (
                jda != null
                        && started
        ) {

            logger.info(
                    "Останавливаю Discord-бота"
            );

            jda.shutdown();

            started =
                    false;
        }
    }
}