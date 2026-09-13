package bot.service;

import bot.command.CommandDispatcher;
import bot.config.BotConfig;
import bot.database.UserRepository;
import bot.model.User;

/**
 * Обработка входящего сообщения: бизнес-правила, которые раньше
 * жили в LongPollClient.
 *
 * Здесь происходит валидация отправителя, «защита Вождя клуба»,
 * фильтрация банов/мутов и передача текста в CommandDispatcher.
 * Транспорт (Long Poll) больше не содержит бизнес-логики.
 */
public class MessageProcessingService {

    private final UserRepository userRepository;

    private final CommandDispatcher dispatcher;

    private final BotConfig config;

    private final Logger logger;

    public MessageProcessingService(
            UserRepository userRepository,
            CommandDispatcher dispatcher,
            BotConfig config
    ) {

        this.userRepository =
                userRepository;

        this.dispatcher =
                dispatcher;

        this.config =
                config;

        this.logger =
                new Logger(
                        MessageProcessingService.class
                );
    }

    public void process(
            long fromId,
            int peerId,
            int messageId,
            String text
    ) {

        logger.info(
                "Обработка сообщения: fromId="
                        + fromId
                        + ", peerId="
                        + peerId
        );

        if (
                fromId <= 0
        ) {

            logger.warning(
                    "Некорректный from_id: "
                            + fromId
            );


            return;
        }

        if (
                peerId <= 0
        ) {

            logger.warning(
                    "Некорректный peer_id: "
                            + peerId
            );


            return;
        }

        if (
                text == null
                        || text.isBlank()
        ) {

            logger.info(
                    "Пустое сообщение"
            );


            return;
        }

        /*
         * Не обрабатываем сообщения, отправленные самим сообществом.
         * В VK from_id сообщества может быть отрицательным.
         */
        if (
                fromId < 0
        ) {

            logger.info(
                    "Сообщение отправлено сообществом"
            );


            return;
        }

        logger.info(
                "Создание/проверка пользователя: "
                        + fromId
        );

        userRepository.ensureUser(
                fromId
        );

        User user =
                userRepository.findById(
                        fromId
                );

        if (
                user == null
        ) {

            logger.warning(
                    "Пользователь не найден после ensureUser: "
                            + fromId
            );


            return;
        }

        /*
         * Автоматическая защита Вождя клуба.
         * Патрон всегда имеет высший ранг (в клубе — «Вождь клуба»).
         * Имя и позывной не принуждаются — берутся из профиля.
         */
        if (
                fromId ==
                        config.getPatronId()
        ) {

            logger.info(
                    "Обнаружен Вождь клуба: "
                            + fromId
            );

            userRepository.setRank(
                    fromId,
                    "VOZHD_CLUBA"
            );

            user =
                    userRepository.findById(
                            fromId
                    );
        }

        if (
                user.isBanned()
        ) {

            logger.info(
                    "Сообщение забаненного пользователя игнорируется"
            );


            return;
        }

        /*
         * Ленивое снятие истёкшего мута: если mute_end уже в прошлом,
         * пользователь больше не считается замьюченным, а stale-значение
         * в базе очищается без планировщика (работает и после рестарта).
         */
        if (
                user.getMuteEnd() > 0
                        && user.getMuteEnd()
                        <= System.currentTimeMillis()
        ) {

            userRepository.removeMute(
                    fromId
            );

            logger.info(
                    "Автоснятие истёкшего мута (лениво): "
                            + fromId
            );
        }

        if (
                user.getMuteEnd()
                        > System.currentTimeMillis()
        ) {

            logger.info(
                    "Сообщение замьюченного пользователя игнорируется: "
                            + fromId
            );

            /*
             * Команды замьюченного не выполняются вовсе:
             * до CommandDispatcher сообщение не доходит.
             */
            if (
                    text.trim()
                            .startsWith(
                                    config.getCommandPrefix()
                            )
            ) {

                logger.info(
                        "Попытка команды замьюченного пользователя "
                                + fromId
                                + ": "
                                + text.trim()
                );
            }

            if (
                    messageId > 0
            ) {

                boolean deleted =
                        dispatcher.getVkApi()
                                .deleteMessage(
                                        messageId
                                );

                logger.info(
                        "Попытка удаления сообщения замьюченного "
                                + fromId
                                + ": id="
                                + messageId
                                + ", результат="
                                + deleted
                );
            }

            return;
        }

        /*
         * Учёт обычного сообщения (активность). Заблокированные
         * и замьюченные пользователи выше не считаются.
         */
        userRepository.recordActivity(
                fromId,
                false
        );

        logger.info(
                "Передача команды в CommandDispatcher: "
                        + text
        );

        dispatcher.dispatch(
                text,
                fromId,
                peerId
        );
    }
}