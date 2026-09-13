package bot.service;

import bot.config.BotConfig;
import bot.database.PunishmentRepository;
import bot.database.UserRepository;
import bot.model.User;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Фоновая обработка временных наказаний.
 *
 * Периодически проверяет истёкшие MUTE, переводит их в EXPIRED
 * и снимает mute_end у пользователя. Работает на daemon-потоке,
 * интервал настраивается через VK_TEMP_PUNISHMENT_INTERVAL_SEC.
 */
public class PunishmentService {

    private final PunishmentRepository punishmentRepository;

    private final UserRepository userRepository;

    private final BotConfig config;

    private final Logger logger =
            new Logger(
                    PunishmentService.class
            );

    private volatile ScheduledExecutorService scheduler;


    public PunishmentService(
            PunishmentRepository punishmentRepository,
            UserRepository userRepository,
            BotConfig config
    ) {

        this.punishmentRepository =
                punishmentRepository;

        this.userRepository =
                userRepository;

        this.config =
                config;
    }


    public void start() {

        expireDue();

        long intervalMillis =
                config.getTempPunishmentIntervalMillis();

        scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {

                            Thread thread =
                                    new Thread(
                                            runnable,
                                            "punishment-expiry"
                                    );

                            thread.setDaemon(
                                    true
                            );

                            return thread;
                        }
                );

        scheduler.scheduleWithFixedDelay(
                this::expireDueSafe,
                intervalMillis,
                intervalMillis,
                TimeUnit.MILLISECONDS
        );

        logger.info(
                "Сервис наказаний запущен (интервал "
                        + intervalMillis
                        + " мс)"
        );
    }


    public void stop() {

        ScheduledExecutorService current =
                scheduler;

        if (
                current != null
        ) {

            current.shutdownNow();

            scheduler =
                    null;
        }
    }


    private void expireDueSafe() {

        try {

            expireDue();

        } catch (
                Exception e
        ) {

            logger.error(
                    "Ошибка автоснятия наказаний",
                    e
            );
        }
    }


    /**
     * Переводит истёкшие наказания в EXPIRED и снимает
     * mute_end, если его срок совпал с истечением мута.
     */
    public void expireDue() {

        List<PunishmentRepository.ExpiredMute> expired =
                punishmentRepository.expireDue();

        for (
                PunishmentRepository.ExpiredMute item :
                expired
        ) {

            User user =
                    userRepository.findById(
                            item.targetId()
                    );

            if (
                    user != null
                            && user.getMuteEnd() > 0
                            && user.getMuteEnd()
                            <= item.endsAt()
            ) {

                userRepository.removeMute(
                        item.targetId()
                );

                logger.info(
                        "Автоснятие мута у пользователя "
                                + item.targetId()
                );

            } else {

                logger.info(
                        "Наказание помечено как истёкшее (mute): "
                                + item.targetId()
                );
            }
        }
    }
}