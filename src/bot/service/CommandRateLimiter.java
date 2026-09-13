package bot.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Rate limiter и cooldown для команд (VK Bot PRO 2.0).
 *
 * 1. Fixed-window лимит: ограничивает количество команд пользователя
 *    за окно времени ({@link #allow}).
 * 2. Cooldown одной команды: минимальная пауза между повторным
 *    запуском ОДНОЙ И ТОЙ ЖЕ команды (per-user, per-command).
 *    Обычная переписка и регистрация не затрагиваются.
 */
public class CommandRateLimiter {

    /**
     * Хранить cooldown-состояния не дольше 10 минут после
     * последней активности, чтобы карты не росли бесконечно.
     */
    private static final long COOLDOWN_RETENTION_MILLIS =
            600_000L;

    /**
     * userId -> (команда -> последний запуск и последнее сообщение о блоке).
     */
    private final ConcurrentMap<Long, ConcurrentMap<String, CooldownState>> cooldowns =
            new ConcurrentHashMap<>();

    private final ConcurrentMap<Long, Window> windows =
            new ConcurrentHashMap<>();

    private volatile long lastCleanup =
            System.currentTimeMillis();


    /**
     * @return true, если команду можно выполнить
     */
    public boolean allow(
            long userId,
            int limit,
            long windowMillis
    ) {

        long now =
                System.currentTimeMillis();


        Window window =
                windows.compute(
                        userId,
                        (
                                key,
                                existing
                        ) -> {

                            if (
                                    existing == null
                                            || now - existing.start
                                            >= windowMillis
                            ) {

                                return new Window(
                                        now,
                                        1
                                );
                            }


                            existing.count++;

                            return existing;
                        }
                );


        cleanup(
                now,
                windowMillis
        );


        return window.count
                <= limit;
    }


    private void cleanup(
            long now,
            long windowMillis
    ) {

        if (
                now - lastCleanup
                        < 60_000L
        ) {

            return;
        }


        lastCleanup =
                now;

        long threshold =
                now
                        - windowMillis
                        * 2;


        windows.entrySet()
                .removeIf(
                        entry ->
                                entry.getValue().start
                                        < threshold
                );


        cleanupCooldowns(
                now
        );
    }


    /**
     * Удаляет cooldown-записи пользователей, у которых последняя
     * активность была больше COOLDOWN_RETENTION_MILLIS назад.
     */
    private void cleanupCooldowns(
            long now
    ) {

        long threshold =
                now
                        - COOLDOWN_RETENTION_MILLIS;

        cooldowns.entrySet()
                .removeIf(
                        entry -> {

                            for (
                                    CooldownState state :
                                    entry.getValue().values()
                            ) {

                                synchronized (
                                        state
                                ) {

                                    if (
                                            Math.max(
                                                    state.lastExecMillis,
                                                    state.lastBlockMillis
                                            ) >= threshold
                                    ) {

                                        return false;
                                    }
                                }
                            }

                            return true;
                        }
                );
    }


    /**
     * Cooldown одной команды для пользователя.
     *
     * @return 0 — можно выполнить; &gt;0 — нужно подождать (количество мс),
     *         сообщение пользователю ещё не отправлялось в этом окне;
     *         -1 — подождать, но про это окно пользователь уже предупреждён
     *         (дополнительное сообщение не отправлять)
     */
    public long commandCooldownMillis(
            long userId,
            String command,
            long cooldownMillis
    ) {

        if (
                command == null
                        || cooldownMillis <= 0
        ) {

            return 0L;
        }

        long now =
                System.currentTimeMillis();

        ConcurrentMap<String, CooldownState> byCommand =
                cooldowns.computeIfAbsent(
                        userId,
                        key ->
                                new ConcurrentHashMap<>()
                );

        CooldownState state =
                byCommand.computeIfAbsent(
                        command,
                        key ->
                                new CooldownState()
                );

        synchronized (
                state
        ) {

            long remaining =
                    cooldownMillis
                            - (
                            now
                                    - state.lastExecMillis
                    );

            if (
                    remaining <= 0
            ) {

                return 0L;
            }

            if (
                    state.lastBlockMillis != 0
                            && now
                            - state.lastBlockMillis
                            < cooldownMillis
            ) {

                return -1L;
            }

            state.lastBlockMillis =
                    now;

            return remaining;
        }
    }


    /**
     * Фиксирует успешный запуск команды: именно с этого момента
     * начинает отсчитываться cooldown для повторного ввода.
     */
    public void recordCommandExecution(
            long userId,
            String command
    ) {

        if (
                command == null
        ) {

            return;
        }

        ConcurrentMap<String, CooldownState> byCommand =
                cooldowns.computeIfAbsent(
                        userId,
                        key ->
                                new ConcurrentHashMap<>()
                );

        CooldownState state =
                byCommand.computeIfAbsent(
                        command,
                        key ->
                                new CooldownState()
                );

        long now =
                System.currentTimeMillis();

        synchronized (
                state
        ) {

            state.lastExecMillis =
                    now;
        }
    }


    private static final class Window {

        private final long start;

        private int count;

        private Window(
                long start,
                int count
        ) {

            this.start =
                    start;

            this.count =
                    count;
        }
    }


    private static final class CooldownState {

        private long lastExecMillis;

        private long lastBlockMillis;
    }
}