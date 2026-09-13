package bot.config;

/**
 * Конфигурация VK-бота.
 *
 * Все значения читаются из переменных окружения (через {@link Environment})
 * с безопасными значениями по умолчанию. Токен VK обязан быть задан,
 * секретов в коде нет.
 */
public class BotConfig {

    public static final String DEFAULT_DB_FILE =
            "bot.db";

    private final String token;

    private final long groupId;

    private final long patronId;

    private final String apiVersion;

    private final int waitTime;

    private final String databaseFile;

    private final int httpTimeoutSeconds;

    private final long registrationTimeoutMillis;

    private final long reconnectDelayMillis;

    /* === VK BOT PRO 2.0 === */

    private final String commandPrefix;

    private final int rateLimit;

    private final int rateLimitAdmin;

    private final long rateLimitWindowMillis;

    private final long repCooldownMillis;

    private final long tempPunishmentIntervalMillis;

    private final int topLimit;

    private final long commandCooldownMillis;

    private final long adminCommandCooldownMillis;


    public BotConfig() {

        this.token =
                Environment.get(
                        "VK_TOKEN"
                );

        this.groupId =
                parsePositiveLong(
                        "VK_GROUP_ID",
                        200588798L
                );

        this.patronId =
                parsePositiveLong(
                        "VK_PATRON_ID",
                        497479649L
                );

        this.apiVersion =
                Environment.get(
                        "VK_API_VERSION",
                        "5.199"
                );

        this.waitTime =
                parsePositiveInt(
                        "VK_LONGPOLL_WAIT",
                        25
                );

        this.databaseFile =
                Environment.get(
                        "VK_DB_PATH",
                        DEFAULT_DB_FILE
                );

        this.httpTimeoutSeconds =
                parsePositiveInt(
                        "VK_HTTP_TIMEOUT",
                        20
                );

        this.registrationTimeoutMillis =
                parsePositiveLong(
                        "VK_REGISTRATION_TIMEOUT_SEC",
                        600L
                ) * 1000L;

        this.reconnectDelayMillis =
                parsePositiveLong(
                        "VK_LONGPOLL_RECONNECT_DELAY_MS",
                        5000L
                );

        this.commandPrefix =
                defaultString(
                        Environment.get(
                                "VK_COMMAND_PREFIX"
                        ),
                        "/"
                );

        this.rateLimit =
                parsePositiveInt(
                        "VK_RATE_LIMIT",
                        25
                );

        this.rateLimitAdmin =
                parsePositiveInt(
                        "VK_RATE_LIMIT_ADMIN",
                        60
                );

        this.rateLimitWindowMillis =
                parsePositiveLong(
                        "VK_RATE_LIMIT_WINDOW_SEC",
                        60L
                ) * 1000L;

        this.repCooldownMillis =
                parsePositiveLong(
                        "VK_REP_COOLDOWN_SEC",
                        3600L
                ) * 1000L;

        this.tempPunishmentIntervalMillis =
                parsePositiveLong(
                        "VK_TEMP_PUNISHMENT_INTERVAL_SEC",
                        60L
                ) * 1000L;

        this.topLimit =
                parsePositiveInt(
                        "VK_TOP_LIMIT",
                        10
                );

        this.commandCooldownMillis =
                parsePositiveLong(
                        "VK_COMMAND_COOLDOWN_SEC",
                        3L
                ) * 1000L;

        this.adminCommandCooldownMillis =
                parsePositiveLong(
                        "VK_ADMIN_COMMAND_COOLDOWN_SEC",
                        1L
                ) * 1000L;
    }

    public boolean hasToken() {

        return token != null
                && !token.isBlank();
    }

    public String getToken() {

        return token;
    }

    public long getGroupId() {

        return groupId;
    }

    public long getPatronId() {

        return patronId;
    }

    public String getApiVersion() {

        return apiVersion;
    }

    public String getDatabaseFile() {

        return databaseFile;
    }

    public int getWaitTime() {

        return waitTime;
    }

    public int getHttpTimeoutSeconds() {

        return httpTimeoutSeconds;
    }

    public long getRegistrationTimeoutMillis() {

        return registrationTimeoutMillis;
    }

    public long getReconnectDelayMillis() {

        return reconnectDelayMillis;
    }

    /* === VK BOT PRO 2.0 === */

    public String getCommandPrefix() {

        return commandPrefix;
    }

    public int getRateLimit() {

        return rateLimit;
    }

    public int getRateLimitAdmin() {

        return rateLimitAdmin;
    }

    public long getRateLimitWindowMillis() {

        return rateLimitWindowMillis;
    }

    public long getRepCooldownMillis() {

        return repCooldownMillis;
    }

    public long getTempPunishmentIntervalMillis() {

        return tempPunishmentIntervalMillis;
    }

    public int getTopLimit() {

        return topLimit;
    }

    public long getCommandCooldownMillis() {

        return commandCooldownMillis;
    }

    public long getAdminCommandCooldownMillis() {

        return adminCommandCooldownMillis;
    }

    /* === helpers === */

    private static String defaultString(
            String value,
            String defaultValue
    ) {

        return value == null
                || value.isBlank()
                ? defaultValue
                : value.trim();
    }

    private static long parsePositiveLong(
            String key,
            long defaultValue
    ) {

        return parseLong(
                key,
                defaultValue
        );
    }

    private static long parseLong(
            String key,
            long defaultValue
    ) {

        String value =
                Environment.get(
                        key
                );

        if (
                value == null
        ) {

            return defaultValue;
        }

        try {

            long parsed =
                    Long.parseLong(
                            value.trim()
                    );

            return parsed > 0
                    ? parsed
                    : defaultValue;

        } catch (
                NumberFormatException e
        ) {

            return defaultValue;
        }
    }

    private static int parsePositiveInt(
            String key,
            int defaultValue
    ) {

        String value =
                Environment.get(
                        key
                );

        if (
                value == null
        ) {

            return defaultValue;
        }

        try {

            int parsed =
                    Integer.parseInt(
                            value.trim()
                    );

            return parsed > 0
                    ? parsed
                    : defaultValue;

        } catch (
                NumberFormatException e
        ) {

            return defaultValue;
        }
    }
}