package bot;

import bot.api.VkApi;
import bot.command.CommandDispatcher;
import bot.config.BotConfig;
import bot.database.AuditRepository;
import bot.database.ChatRepository;
import bot.database.Database;
import bot.database.PunishmentRepository;
import bot.database.UserRepository;
import bot.longpoll.LongPollClient;
import bot.service.AccessControlService;
import bot.service.CommandRateLimiter;
import bot.service.Logger;
import bot.service.MessageProcessingService;
import bot.service.PunishmentService;
import bot.service.RegistrationService;
import bot.service.VkCommunityService;

public class Main {

    private static final Logger logger =
            new Logger(
                    Main.class
            );


    public static void main(
            String[] args
    ) {

        printBanner();


        try {

            logger.info(
                    "Запуск VK Bot PRO"
            );


            BotConfig config =
                    new BotConfig();


            if (
                    !config.hasToken()
            ) {

                logger.error(
                        "Не задан VK_TOKEN. "
                                + "Установи переменную окружения VK_TOKEN "
                                + "или создай файл .env (см. .env.example)."
                );

                System.exit(1);

                return;
            }


            logger.info(
                    "Конфигурация загружена"
            );


            /*
             * USER DATABASE
             */

            UserRepository userRepository =
                    new UserRepository(
                            config
                    );


            userRepository.init();


            logger.info(
                    "База пользователей инициализирована"
            );


            /*
             * CHAT DATABASE
             */

            ChatRepository chatRepository =
                    new ChatRepository(
                            config
                    );


            chatRepository.init();


            logger.info(
                    "База чатов инициализирована"
            );


            /*
             * PUNISHMENT DATABASE
             */

            PunishmentRepository punishmentRepository =
                    new PunishmentRepository(
                            new Database(
                                    config.getDatabaseFile()
                            )
                    );

            punishmentRepository.init();


            /*
             * AUDIT DATABASE
             */

            AuditRepository auditRepository =
                    new AuditRepository(
                            new Database(
                                    config.getDatabaseFile()
                            )
                    );

            auditRepository.init();


            logger.info(
                    "История наказаний и аудит инициализированы"
            );


            /*
             * REGISTRATION SERVICE
             */

            RegistrationService registrationService =
                    new RegistrationService(
                            userRepository,
                            auditRepository,
                            config
                    );


            logger.info(
                    "Сервис регистрации запущен"
            );


            /*
             * VK API
             */

            VkApi vkApi =
                    new VkApi(
                            config
                    );


            logger.info(
                    "VK API готов"
            );


            /*
             * VK COMMUNITY SERVICE
             */

            VkCommunityService communityService =
                    new VkCommunityService(
                            vkApi,
                            config
                    );


            logger.info(
                    "Сервис сообщества VK готов"
            );


            /*
             * ACCESS CONTROL
             */

            AccessControlService accessControl =
                    new AccessControlService(
                            config
                    );


            logger.info(
                    "Система прав готова"
            );


            /*
             * RATE LIMITER
             */

            CommandRateLimiter rateLimiter =
                    new CommandRateLimiter();


            /*
             * COMMAND DISPATCHER
             */

            CommandDispatcher dispatcher =
                    new CommandDispatcher(
                            vkApi,
                            communityService,
                            userRepository,
                            registrationService,
                            chatRepository,
                            punishmentRepository,
                            auditRepository,
                            config,
                            accessControl,
                            rateLimiter
                    );


            /*
             * MESSAGE PROCESSING
             */

            MessageProcessingService messageProcessing =
                    new MessageProcessingService(
                            userRepository,
                            dispatcher,
                            config
                    );


            /*
             * LONG POLL
             */

            LongPollClient longPollClient =
                    new LongPollClient(
                            config,
                            dispatcher,
                            messageProcessing
                    );


            logger.info(
                    "Все системы инициализированы"
            );


            /*
             * PUNISHMENT SERVICE (автоснятие мутов)
             */

            PunishmentService punishmentService =
                    new PunishmentService(
                            punishmentRepository,
                            userRepository,
                            config
                    );

            punishmentService.start();


            logger.info(
                    "Запуск Long Poll"
            );


            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread(
                                    () -> {

                                        longPollClient.stop();

                                        punishmentService.stop();
                                    }
                            )
                    );


            longPollClient.start();


        } catch (
                Exception e
        ) {

            logger.error(
                    "Критическая ошибка запуска",
                    e
            );
        }
    }


    private static void printBanner() {

        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "              VK BOT PRO"
        );

        System.out.println(
                "       Advanced Java VK Bot"
        );

        System.out.println(
                "========================================"
        );

        System.out.println();
    }
}