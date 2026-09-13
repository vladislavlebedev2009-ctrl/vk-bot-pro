package bot.database;

import bot.config.BotConfig;
import bot.model.Rank;
import bot.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Единый data-слой для таблиц users, rep_changes, balance_history.
 *
 * Помимо базового CRUD предоставляет переводы баланса, смену
 * репутации с кулдауном и TOP-запросы.
 */
public class UserRepository {

    /**
     * Общий набор колонок для чтения строки таблицы users.
     */
    private static final String USER_COLUMNS =
            "id, balance, rep, rank, banned, mute_end, warns, name, "
                    + "call_sign, registered_at, last_activity, "
                    + "messages_count, commands_count, "
                    + "punishments_received, punishments_given";

    /**
     * Пользователь считается «онлайн», если его последняя активность
     * была не позже этого окна (используется фильтром /users online).
     */
    private static final long ONLINE_ACTIVE_WINDOW_MILLIS =
            10 * 60_000L;

    private final BotConfig config;

    private final Database database;


    public UserRepository(
            BotConfig config
    ) {

        this.config =
                config;

        this.database =
                new Database(
                        config.getDatabaseFile()
                );
    }


    public void init() {

        try (
                Connection connection =
                        getConnection();

                Statement statement =
                        connection.createStatement()
        ) {

            statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS users (

                        id INTEGER PRIMARY KEY,

                        balance INTEGER DEFAULT 100,

                        rep INTEGER DEFAULT 0,

                        rank TEXT DEFAULT '',

                        banned INTEGER DEFAULT 0,

                        mute_end INTEGER DEFAULT 0,

                        warns INTEGER DEFAULT 0,

                        name TEXT DEFAULT '',

                        call_sign TEXT DEFAULT '',

                        registered_at INTEGER DEFAULT 0,

                        last_activity INTEGER DEFAULT 0,

                        messages_count INTEGER DEFAULT 0,

                        commands_count INTEGER DEFAULT 0,

                        punishments_received INTEGER DEFAULT 0,

                        punishments_given INTEGER DEFAULT 0

                    )
                    """
            );

            statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS rep_changes (

                        id INTEGER PRIMARY KEY AUTOINCREMENT,

                        target_id INTEGER NOT NULL,

                        actor_id INTEGER NOT NULL,

                        change INTEGER NOT NULL,

                        created_at INTEGER NOT NULL

                    )
                    """
            );

            statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS balance_history (

                        id INTEGER PRIMARY KEY AUTOINCREMENT,

                        actor_id INTEGER NOT NULL,

                        target_id INTEGER NOT NULL,

                        amount INTEGER NOT NULL,

                        created_at INTEGER NOT NULL

                    )
                    """
            );

            /*
             * Миграции старых баз данных
             */

            addColumnIfNotExists(
                    connection,
                    "users",
                    "balance",
                    "INTEGER DEFAULT 100"
            );

            addColumnIfNotExists(
                    connection,
                    "users",
                    "rep",
                    "INTEGER DEFAULT 0"
            );

            addColumnIfNotExists(
                    connection,
                    "users",
                    "rank",
                    "TEXT DEFAULT ''"
            );

            addColumnIfNotExists(
                    connection,
                    "users",
                    "banned",
                    "INTEGER DEFAULT 0"
            );

            addColumnIfNotExists(
                    connection,
                    "users",
                    "mute_end",
                    "INTEGER DEFAULT 0"
            );

            addColumnIfNotExists(
                    connection,
                    "users",
                    "warns",
                    "INTEGER DEFAULT 0"
            );

            addColumnIfNotExists(
                    connection,
                    "users",
                    "name",
                    "TEXT DEFAULT ''"
            );

            addColumnIfNotExists(
                    connection,
                    "users",
                    "call_sign",
                    "TEXT DEFAULT ''"
            );

            addColumnIfNotExists(
                    connection,
                    "users",
                    "registered_at",
                    "INTEGER DEFAULT 0"
            );

            addColumnIfNotExists(
                    connection,
                    "users",
                    "last_activity",
                    "INTEGER DEFAULT 0"
            );

            addColumnIfNotExists(
                    connection,
                    "users",
                    "messages_count",
                    "INTEGER DEFAULT 0"
            );

            addColumnIfNotExists(
                    connection,
                    "users",
                    "commands_count",
                    "INTEGER DEFAULT 0"
            );

            addColumnIfNotExists(
                    connection,
                    "users",
                    "punishments_received",
                    "INTEGER DEFAULT 0"
            );

            addColumnIfNotExists(
                    connection,
                    "users",
                    "punishments_given",
                    "INTEGER DEFAULT 0"
            );

            /*
             * Индексы для часто используемых запросов (TOP, фильтры,
             * активность, история). Создание идемпотентно (IF NOT EXISTS).
             */

            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_users_rank "
                            + "ON users(rank)"
            );

            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_users_balance "
                            + "ON users(balance)"
            );

            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_users_rep "
                            + "ON users(rep)"
            );

            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_users_messages_count "
                            + "ON users(messages_count)"
            );

            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_users_last_activity "
                            + "ON users(last_activity)"
            );

            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_rep_changes_actor "
                            + "ON rep_changes(actor_id, target_id, created_at)"
            );

            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_balance_history_actor "
                            + "ON balance_history(actor_id, created_at)"
            );

            /*
             * Безопасная миграция старых званий в новую шкалу клуба.
             * Идемпотентно: повторные запуски ничего не меняют.
             */
            migrateRanks(
                    statement
            );

            /*
             * Зарегистрированные пользователи без звания получают
             * «Кандидат». Незарегистрированные остаются без звания.
             */
            statement.executeUpdate(
                    "UPDATE users SET rank = 'KANDIDAT' "
                            + "WHERE (rank IS NULL OR rank = '') "
                            + "AND TRIM(name) != '' "
                            + "AND TRIM(call_sign) != ''"
            );

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка инициализации базы данных",
                    e
            );
        }
    }


    private Connection getConnection()
            throws SQLException {

        return database.getConnection();
    }


    /*
     * Создание пользователя
     */

    public void ensureUser(
            long userId
    ) {

        if (
                userId <= 0
        ) {

            return;
        }


        String sql =
                """
                INSERT OR IGNORE INTO users
                (
                    id,
                    balance,
                    rep,
                    rank,
                    banned,
                    mute_end,
                    warns,
                    name,
                    call_sign
                )
                VALUES
                (
                    ?,
                    100,
                    0,
                    '',
                    0,
                    0,
                    0,
                    '',
                    ''
                )
                """;


        try (
                Connection connection =
                        getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        )
        ) {

            statement.setLong(
                    1,
                    userId
            );

            statement.executeUpdate();

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка создания пользователя: "
                            + userId,
                    e
            );
        }
    }


    /**
     * Алиас для старого кода.
     */
    public void initUser(
            long userId
    ) {

        ensureUser(
                userId
        );
    }


    /*
     * Получение пользователя
     */

    public User findById(
            long userId
    ) {

        String sql =
                "SELECT "
                        + USER_COLUMNS
                        + " FROM users WHERE id = ?";

        try (
                Connection connection =
                        getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        )
        ) {

            statement.setLong(
                    1,
                    userId
            );

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                if (
                        resultSet.next()
                ) {

                    return mapUser(
                            resultSet
                    );
                }
            }

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка поиска пользователя: "
                            + userId,
                    e
            );
        }

        return null;
    }


    private User mapUser(
            ResultSet resultSet
    )
            throws SQLException {

        return new User(
                resultSet.getLong("id"),
                resultSet.getInt("balance"),
                resultSet.getInt("rep"),
                resultSet.getString("rank"),
                resultSet.getBoolean("banned"),
                resultSet.getLong("mute_end"),
                resultSet.getInt("warns"),
                resultSet.getString("name"),
                resultSet.getString("call_sign"),
                resultSet.getLong("registered_at"),
                resultSet.getLong("last_activity"),
                resultSet.getInt("messages_count"),
                resultSet.getInt("commands_count"),
                resultSet.getInt("punishments_received"),
                resultSet.getInt("punishments_given")
        );
    }


    /*
     * Проверка регистрации.
     */

    public boolean isRegistered(
            long userId
    ) {

        User user =
                findById(
                        userId
                );

        if (
                user == null
        ) {

            return false;
        }

        return user.getName() != null
                && !user.getName().isBlank()
                && user.getCallSign() != null
                && !user.getCallSign().isBlank();
    }


    /*
     * Зарегистрированные пользователи.
     */

    public List<User> getRegisteredUsers() {

        return queryUsers(
                null,
                null,
                null,
                null,
                0,
                0
        );
    }


    /**
     * Поиск зарегистрированных пользователей с фильтрами.
     *
     * @param query  подстрока по имени/позывному/ID, null — без фильтра
     * @param rank   фильтр по рангу, null — без фильтра
     * @param status banned | muted | active | null — без фильтра
     * @param sort   name|rank|messages|rep|balance|registered|activity
     * @param limit  максимум строк (0 — без лимита)
     * @param offset смещение для пагинации
     */
    public List<User> queryUsers(
            String query,
            Rank rank,
            String status,
            String sort,
            int limit,
            int offset
    ) {

        List<User> users =
                new ArrayList<>();

        StringBuilder sql =
                new StringBuilder(
                        "SELECT "
                                + USER_COLUMNS
                                + " FROM users "
                                + "WHERE name IS NOT NULL "
                                + "AND TRIM(name) != '' "
                                + "AND call_sign IS NOT NULL "
                                + "AND TRIM(call_sign) != ''"
                );

        List<Object> params =
                new ArrayList<>();

        if (
                query != null
                        && !query.isBlank()
        ) {

            sql.append(
                    " AND (name LIKE ? "
                            + "OR call_sign LIKE ? "
                            + "OR CAST(id AS TEXT) LIKE ?)"
            );

            String pattern =
                    "%"
                            + query.trim()
                            + "%";

            params.add(
                    pattern
            );

            params.add(
                    pattern
            );

            params.add(
                    pattern
            );
        }

        if (
                rank != null
        ) {

            sql.append(
                    " AND (rank = ? OR rank = ?)"
            );

            params.add(
                    rank.name()
            );

            params.add(
                    rank.getDisplayName()
            );
        }

        if (
                "banned".equalsIgnoreCase(
                        status
                )
        ) {

            sql.append(
                    " AND banned = 1"
            );

        } else if (
                "muted".equalsIgnoreCase(
                        status
                )
        ) {

            sql.append(
                    " AND mute_end > ?"
            );

            params.add(
                    System.currentTimeMillis()
            );

        } else if (
                "active".equalsIgnoreCase(
                        status
                )
        ) {

            sql.append(
                    " AND banned = 0 AND mute_end <= ?"
            );

            params.add(
                    System.currentTimeMillis()
            );
        } else if (
                "online".equalsIgnoreCase(
                        status
                )
        ) {

            sql.append(
                    " AND banned = 0 AND mute_end <= ? "
                            + "AND last_activity >= ?"
            );

            params.add(
                    System.currentTimeMillis()
            );

            params.add(
                    System.currentTimeMillis()
                            - ONLINE_ACTIVE_WINDOW_MILLIS
            );
        }

        sql.append(
                " ORDER BY "
        );

        sql.append(
                orderBy(
                        sort
                )
        );

        if (
                limit > 0
        ) {

            sql.append(
                    " LIMIT ?"
            );

            params.add(
                    limit
            );

            if (
                    offset >= 0
            ) {

                sql.append(
                        " OFFSET ?"
                );

                params.add(
                        offset
                );
            }
        }

        try (
                Connection connection =
                        getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql.toString()
                        )
        ) {

            for (
                    int i = 0;
                    i < params.size();
                    i++
            ) {

                statement.setObject(
                        i + 1,
                        params.get(
                                i
                        )
                );
            }

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                while (
                        resultSet.next()
                ) {

                    users.add(
                            mapUser(
                                    resultSet
                            )
                    );
                }
            }

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка поиска пользователей",
                    e
            );
        }

        return users;
    }


    /**
     * Количество зарегистрированных пользователей с теми же
     * фильтрами, что у {@link #queryUsers}.
     */
    public int queryUsersCount(
            String query,
            Rank rank,
            String status
    ) {

        StringBuilder sql =
                new StringBuilder(
                        "SELECT COUNT(*)"
                                + " FROM users "
                                + "WHERE name IS NOT NULL "
                                + "AND TRIM(name) != '' "
                                + "AND call_sign IS NOT NULL "
                                + "AND TRIM(call_sign) != ''"
                );

        List<Object> params =
                new ArrayList<>();

        if (
                query != null
                        && !query.isBlank()
        ) {

            sql.append(
                    " AND (name LIKE ? "
                            + "OR call_sign LIKE ? "
                            + "OR CAST(id AS TEXT) LIKE ?)"
            );

            String pattern =
                    "%"
                            + query.trim()
                            + "%";

            params.add(
                    pattern
            );

            params.add(
                    pattern
            );

            params.add(
                    pattern
            );
        }

        if (
                rank != null
        ) {

            sql.append(
                    " AND (rank = ? OR rank = ?)"
            );

            params.add(
                    rank.name()
            );

            params.add(
                    rank.getDisplayName()
            );
        }

        if (
                "banned".equalsIgnoreCase(
                        status
                )
        ) {

            sql.append(
                    " AND banned = 1"
            );

        } else if (
                "muted".equalsIgnoreCase(
                        status
                )
        ) {

            sql.append(
                    " AND mute_end > ?"
            );

            params.add(
                    System.currentTimeMillis()
            );

        } else if (
                "active".equalsIgnoreCase(
                        status
                )
        ) {

            sql.append(
                    " AND banned = 0 AND mute_end <= ?"
            );

            params.add(
                    System.currentTimeMillis()
            );
        } else if (
                "online".equalsIgnoreCase(
                        status
                )
        ) {

            sql.append(
                    " AND banned = 0 AND mute_end <= ? "
                            + "AND last_activity >= ?"
            );

            params.add(
                    System.currentTimeMillis()
            );

            params.add(
                    System.currentTimeMillis()
                            - ONLINE_ACTIVE_WINDOW_MILLIS
            );
        }

        try (
                Connection connection =
                        getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql.toString()
                        )
        ) {

            for (
                    int i = 0;
                    i < params.size();
                    i++
            ) {

                statement.setObject(
                        i + 1,
                        params.get(
                                i
                        )
                );
            }

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                if (
                        resultSet.next()
                ) {

                    return resultSet.getInt(
                            1
                    );
                }
            }

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка подсчёта пользователей",
                    e
            );
        }

        return 0;
    }


    /**
     * Количество зарегистрированных участников клуба.
     */
    public int countRegistered() {

        String sql =
                "SELECT COUNT(*) FROM users "
                        + "WHERE TRIM(name) != '' "
                        + "AND TRIM(call_sign) != ''";

        return queryInt(
                sql
        );
    }


    /**
     * Средняя репутация зарегистрированных участников.
     */
    public double averageRep() {

        String sql =
                "SELECT AVG(rep) FROM users "
                        + "WHERE TRIM(name) != '' "
                        + "AND TRIM(call_sign) != ''";

        try (
                Connection connection =
                        getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        );

                ResultSet resultSet =
                        statement.executeQuery()
        ) {

            if (
                    resultSet.next()
                            && !resultSet.wasNull()
            ) {

                return resultSet.getDouble(
                        1
                );
            }

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка расчёта средней репутации",
                    e
            );
        }

        return 0.0;
    }


    /**
     * Суммарный баланс всех зарегистрированных участников.
     */
    public long totalBalance() {

        String sql =
                "SELECT COALESCE(SUM(balance), 0) FROM users "
                        + "WHERE TRIM(name) != '' "
                        + "AND TRIM(call_sign) != ''";

        try (
                Connection connection =
                        getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        );

                ResultSet resultSet =
                        statement.executeQuery()
        ) {

            if (
                    resultSet.next()
            ) {

                return resultSet.getLong(
                        1
                );
            }

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка расчёта суммарного баланса",
                    e
            );
        }

        return 0L;
    }


    /**
     * Распределение зарегистрированных участников по званиям,
     * отсортированное по убыванию уровня (высший ранг первым).
     */
    public List<RankCount> rankDistribution() {

        String sql =
                "SELECT rank, COUNT(*) AS cnt FROM users "
                        + "WHERE TRIM(name) != '' "
                        + "AND TRIM(call_sign) != '' "
                        + "GROUP BY rank";

        List<RankCount> result =
                new ArrayList<>();

        try (
                Connection connection =
                        getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        );

                ResultSet resultSet =
                        statement.executeQuery()
        ) {

            while (
                    resultSet.next()
            ) {

                String rankName =
                        resultSet.getString(
                                "rank"
                        );

                Rank rank =
                        Rank.fromString(
                                rankName
                        );

                result.add(
                        new RankCount(
                                rank == null
                                        ? "Без звания"
                                        : rank.getDisplayName(),
                                rank == null
                                        ? -1
                                        : rank.getLevel(),
                                resultSet.getInt(
                                        "cnt"
                                )
                        )
                );
            }

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка расчёта распределения по званиям",
                    e
            );
        }

        result.sort(
                (a, b) -> Integer.compare(
                        b.level(),
                        a.level()
                )
        );

        return result;
    }


    private int queryInt(
            String sql
    ) {

        try (
                Connection connection =
                        getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        );

                ResultSet resultSet =
                        statement.executeQuery()
        ) {

            if (
                    resultSet.next()
            ) {

                return resultSet.getInt(
                        1
                );
            }

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка выполнения запроса",
                    e
            );
        }

        return 0;
    }


    private String orderBy(
            String sort
    ) {

        if (
                sort == null
        ) {

            /*
             * По умолчанию — по званию (высший ранг выше, DESC)
             */
            return rankOrder()
                    + " DESC, name COLLATE NOCASE";
        }

        return switch (
                sort.toLowerCase(
                        Locale.ROOT
                )
        ) {

            case "name" -> "name COLLATE NOCASE, "
                    + rankOrder()
                    + " DESC";

            case "messages" -> "messages_count DESC, "
                    + rankOrder()
                    + " DESC, name COLLATE NOCASE";

            case "rep" -> "rep DESC, "
                    + rankOrder()
                    + " DESC, name COLLATE NOCASE";

            case "balance" -> "balance DESC, "
                    + rankOrder()
                    + " DESC, name COLLATE NOCASE";

            case "registered" -> "registered_at DESC, "
                    + rankOrder()
                    + " DESC, name COLLATE NOCASE";

            case "activity" -> "last_activity DESC, "
                    + rankOrder()
                    + " DESC, name COLLATE NOCASE";

            default -> rankOrder()
                    + " DESC, name COLLATE NOCASE";
        };
    }


    /**
     * SQL CASE, переводящий строковый ранг (имя или displayName)
     * в уровень иерархии для сортировки.
     */
    private String rankOrder() {

        StringBuilder order =
                new StringBuilder(
                        "CASE"
                );

        for (
                Rank value :
                Rank.values()
        ) {

            order.append(
                    " WHEN rank = '"
                            + value.name()
                            + "' THEN "
                            + value.getLevel()
            );

            order.append(
                    " WHEN rank = '"
                            + value.getDisplayName()
                            .replace(
                                    "'",
                                    "''"
                            )
                            + "' THEN "
                            + value.getLevel()
            );
        }

        order.append(
                " ELSE 99 END"
        );

        return order.toString();
    }


    /*
     * Активность (счётчики сообщений и команд).
     */

    public void recordActivity(
            long userId,
            boolean isCommand
    ) {

        String sql =
                """
                UPDATE users
                SET messages_count = messages_count + 1,
                    commands_count = commands_count + ?,
                    last_activity = ?
                WHERE id = ?
                """;

        try (
                Connection connection =
                        getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        )
        ) {

            statement.setInt(
                    1,
                    isCommand
                            ? 1
                            : 0
            );

            statement.setLong(
                    2,
                    System.currentTimeMillis()
            );

            statement.setLong(
                    3,
                    userId
            );

            statement.executeUpdate();

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка обновления активности: "
                            + userId,
                    e
            );
        }
    }


    /**
     * Счётчик выполненных команд (без увеличения счётчика сообщений).
     */
    public void incrementCommandCount(
            long userId
    ) {

        String sql =
                """
                UPDATE users
                SET commands_count = commands_count + 1,
                    last_activity = ?
                WHERE id = ?
                """;

        try (
                Connection connection =
                        getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        )
        ) {

            statement.setLong(
                    1,
                    System.currentTimeMillis()
            );

            statement.setLong(
                    2,
                    userId
            );

            statement.executeUpdate();

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка подсчёта команд: "
                            + userId,
                    e
            );
        }
    }


    public void setRegisteredAt(
            long userId
    ) {

        updateLong(
                "registered_at",
                System.currentTimeMillis(),
                userId
        );
    }


    /**
     * Счётчики наказаний: цель +1 полученных, актор +1 выданных.
     * Единой транзакцией, чтобы не расходились.
     */
    public void incrementPunishments(
            long targetId,
            long actorId
    ) {

        String sql =
                """
                UPDATE users
                SET punishments_received = punishments_received + 1
                WHERE id = ?
                """;

        String sqlActor =
                """
                UPDATE users
                SET punishments_given = punishments_given + 1
                WHERE id = ?
                """;

        try (
                Connection connection =
                        getConnection()
        ) {

            connection.setAutoCommit(
                    false
            );

            try (
                    PreparedStatement target =
                            connection.prepareStatement(
                                    sql
                            );

                    PreparedStatement actor =
                            connection.prepareStatement(
                                    sqlActor
                            )
            ) {

                target.setLong(
                        1,
                        targetId
                );

                target.executeUpdate();

                actor.setLong(
                        1,
                        actorId
                );

                actor.executeUpdate();
            }

            connection.commit();

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка обновления счётчиков наказаний",
                    e
            );
        }
    }


    /*
     * Имя / Позывной / Ранг
     */

    public void setName(long userId, String name) {

        ensureUser(userId);
        updateString("name", name, userId);
    }

    public String getName(long userId) {

        User user = findById(userId);
        return user == null ? null : user.getName();
    }

    public void setCallSign(long userId, String callSign) {

        ensureUser(userId);
        updateString("call_sign", callSign, userId);
    }

    public String getCallSign(long userId) {

        User user = findById(userId);
        return user == null ? null : user.getCallSign();
    }

    /**
     * Занят ли позывной другим пользователем.
     * Регистрирующемуся себе (userId) занятым не считается.
     */
    public boolean isCallSignTaken(
            long userId,
            String callSign
    ) {

        if (
                callSign == null
                        || callSign.isBlank()
        ) {

            return false;
        }

        String sql =
                """
                SELECT 1
                FROM users
                WHERE call_sign = ? COLLATE NOCASE
                  AND id != ?
                LIMIT 1
                """;

        try (
                Connection connection =
                        getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        )
        ) {

            statement.setString(
                    1,
                    callSign.trim()
            );

            statement.setLong(
                    2,
                    userId
            );

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                return resultSet.next();
            }

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка проверки позывного",
                    e
            );
        }
    }

    public void setRank(long userId, String rank) {

        ensureUser(userId);
        updateString("rank", rank, userId);
    }

    public String getRank(long userId) {

        User user = findById(userId);
        return user == null ? null : user.getRank();
    }


    /*
     * Баланс
     */

    public int getBalance(long userId) {

        User user = findById(userId);
        return user == null ? 0 : user.getBalance();
    }

    public void setBalance(long userId, int balance) {

        ensureUser(userId);
        updateInt("balance", balance, userId);
    }

    public void addBalance(long userId, int amount) {

        setBalance(
                userId,
                getBalance(userId) + amount
        );
    }


    /**
     * Перевод баланса между пользователями. Выполняется одной
     * транзакцией с проверкой средств и защитой от переполнения.
     */
    public TransferResult transfer(
            long fromId,
            long toId,
            long amount
    ) {

        if (
                amount <= 0
        ) {

            return TransferResult.INVALID_AMOUNT;
        }

        if (
                amount
                        > Integer.MAX_VALUE
        ) {

            return TransferResult.INVALID_AMOUNT;
        }

        if (
                fromId == toId
        ) {

            return TransferResult.SELF_TRANSFER;
        }

        ensureUser(
                fromId
        );

        ensureUser(
                toId
        );

        int amountInt =
                (int) amount;

        String selectSql =
                "SELECT balance FROM users WHERE id = ?";

        String updateSql =
                "UPDATE users SET balance = ? WHERE id = ?";

        String historySql =
                """
                INSERT INTO balance_history
                (actor_id, target_id, amount, created_at)
                VALUES (?, ?, ?, ?)
                """;

        try (
                Connection connection =
                        getConnection()
        ) {

            connection.setAutoCommit(
                    false
            );

            try (
                    PreparedStatement select =
                            connection.prepareStatement(
                                    selectSql
                            );

                    PreparedStatement update =
                            connection.prepareStatement(
                                    updateSql
                            );

                    PreparedStatement history =
                            connection.prepareStatement(
                                    historySql
                            )
            ) {

                select.setLong(1, fromId);

                int fromBalance;

                try (
                        ResultSet rs =
                                select.executeQuery()
                ) {

                    if (
                            !rs.next()
                    ) {

                        connection.rollback();

                        return TransferResult.NOT_FOUND;
                    }

                    fromBalance =
                            rs.getInt("balance");
                }

                select.setLong(1, toId);

                int toBalance;

                try (
                        ResultSet rs =
                                select.executeQuery()
                ) {

                    if (
                            !rs.next()
                    ) {

                        connection.rollback();

                        return TransferResult.NOT_FOUND;
                    }

                    toBalance =
                            rs.getInt("balance");
                }

                if (
                        fromBalance < amountInt
                ) {

                    connection.rollback();

                    return TransferResult.INSUFFICIENT_FUNDS;
                }

                if (
                        toBalance
                                > Integer.MAX_VALUE
                                - amountInt
                ) {

                    connection.rollback();

                    return TransferResult.OVERFLOW;
                }

                update.setInt(1, fromBalance - amountInt);
                update.setLong(2, fromId);
                update.executeUpdate();

                update.setInt(1, toBalance + amountInt);
                update.setLong(2, toId);
                update.executeUpdate();

                history.setLong(1, fromId);
                history.setLong(2, toId);
                history.setInt(3, amountInt);
                history.setLong(4, System.currentTimeMillis());
                history.executeUpdate();

                connection.commit();

                return TransferResult.OK;

            } catch (
                    SQLException e
            ) {

                connection.rollback();

                throw e;
            }

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка перевода баланса",
                    e
            );
        }
    }


    /*
     * Репутация
     */

    public int getRep(long userId) {

        User user = findById(userId);
        return user == null ? 0 : user.getRep();
    }

    public void setRep(long userId, int rep) {

        ensureUser(userId);
        updateInt("rep", rep, userId);
    }

    public void addRep(long userId, int amount) {

        setRep(
                userId,
                getRep(userId) + amount
        );
    }


    /**
     * Смена репутации: +1/-1 с записью в историю (rep_changes).
     *
     * <p>Проверка кулдауна выполняется в той же транзакции, что и
     * обновление, поэтому одновременные запросы не смогут обойти
     * ограничение (атомарно).
     *
     * @return false, если актор на цели ещё "на кулдауне"
     */
    public boolean changeRep(
            long targetId,
            long actorId,
            int delta
    ) {

        if (
                delta != 1
                        && delta != -1
        ) {

            return false;
        }

        String cooldownSql =
                """
                SELECT MAX(created_at)
                FROM rep_changes
                WHERE actor_id = ?
                  AND target_id = ?
                """;

        String repSql =
                "UPDATE users SET rep = rep + ? WHERE id = ?";

        String historySql =
                """
                INSERT INTO rep_changes
                (target_id, actor_id, change, created_at)
                VALUES (?, ?, ?, ?)
                """;

        try (
                Connection connection =
                        getConnection()
        ) {

            connection.setAutoCommit(
                    false
            );

            try (
                    PreparedStatement cooldown =
                            connection.prepareStatement(
                                    cooldownSql
                            );

                    PreparedStatement rep =
                            connection.prepareStatement(
                                    repSql
                            );

                    PreparedStatement history =
                            connection.prepareStatement(
                                    historySql
                            )
            ) {

                cooldown.setLong(1, actorId);
                cooldown.setLong(2, targetId);

                try (
                        ResultSet resultSet =
                                cooldown.executeQuery()
                ) {

                    if (
                            resultSet.next()
                                    && !resultSet.wasNull()
                    ) {

                        long last =
                                resultSet.getLong(
                                        1
                                );

                        long remaining =
                                config.getRepCooldownMillis()
                                        - (
                                        System.currentTimeMillis()
                                                - last
                                );

                        if (
                                remaining > 0
                        ) {

                            connection.rollback();

                            return false;
                        }
                    }
                }

                rep.setInt(1, delta);
                rep.setLong(2, targetId);
                rep.executeUpdate();

                history.setLong(1, targetId);
                history.setLong(2, actorId);
                history.setInt(3, delta);
                history.setLong(4, System.currentTimeMillis());
                history.executeUpdate();

                connection.commit();

                return true;

            } catch (
                    SQLException e
                ) {

                connection.rollback();

                throw e;
            }

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка изменения репутации",
                    e
            );
        }
    }


    /**
     * Сколько миллисекунд осталось до возможности снова изменить
     * репутацию цели актором. 0/negative — кулдауна нет.
     */
    public long repCooldownRemainingMillis(
            long actorId,
            long targetId
    ) {

        String sql =
                """
                SELECT MAX(created_at)
                FROM rep_changes
                WHERE actor_id = ?
                  AND target_id = ?
                """;

        try (
                Connection connection =
                        getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        )
        ) {

            statement.setLong(
                    1,
                    actorId
            );

            statement.setLong(
                    2,
                    targetId
            );

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                if (
                        resultSet.next()
                                && !resultSet.wasNull()
                ) {

                    long last =
                            resultSet.getLong(
                                    1
                            );

                    long remaining =
                            config.getRepCooldownMillis()
                                    - (
                                    System.currentTimeMillis()
                                            - last
                            );

                    return Math.max(
                            remaining,
                            0L
                    );
                }
            }

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка проверки кулдауна репутации",
                    e
            );
        }

        return 0L;
    }


    /*
     * Предупреждения
     */

    public int getWarns(long userId) {

        User user = findById(userId);
        return user == null ? 0 : user.getWarns();
    }

    public void addWarn(long userId) {

        ensureUser(userId);
        updateInt(
                "warns",
                getWarns(userId) + 1,
                userId
        );
    }

    public void removeWarn(long userId) {

        int warns = getWarns(userId);

        if (
                warns <= 0
        ) {

            return;
        }

        updateInt(
                "warns",
                warns - 1,
                userId
        );
    }

    public void clearWarns(long userId) {

        updateInt(
                "warns",
                0,
                userId
        );
    }


    /*
     * Бан
     */

    public boolean isBanned(long userId) {

        User user = findById(userId);
        return user != null
                && user.isBanned();
    }

    public void setBanned(long userId, boolean banned) {

        ensureUser(userId);
        updateInt(
                "banned",
                banned ? 1 : 0,
                userId
        );
    }


    /*
     * Мут
     */

    public void setMute(long userId, long muteEnd) {

        ensureUser(userId);
        updateLong("mute_end", muteEnd, userId);
    }

    public void removeMute(long userId) {

        setMute(userId, 0L);
    }

    public boolean isMuted(long userId) {

        User user = findById(userId);
        return user != null
                && user.getMuteEnd()
                > System.currentTimeMillis();
    }


    /*
     * TOP
     */

    public List<TopEntry> top(
            String metric,
            int limit
    ) {

        String column;

        switch (
                metric.toLowerCase()
        ) {

            case "rep" ->
                    column = "rep";

            case "messages" ->
                    column = "messages_count";

            default ->
                    column = "balance";
        }

        String sql =
                "SELECT id, name, call_sign, "
                        + column
                        + " AS value "
                        + "FROM users "
                        + "WHERE TRIM(name) != '' "
                        + "AND TRIM(call_sign) != '' "
                        + "ORDER BY value DESC, id "
                        + "LIMIT ?";

        List<TopEntry> result =
                new ArrayList<>();

        try (
                Connection connection =
                        getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        )
        ) {

            statement.setInt(
                    1,
                    limit
            );

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                while (
                        resultSet.next()
                ) {

                    result.add(
                            new TopEntry(
                                    resultSet.getLong("id"),
                                    safe(
                                            resultSet.getString("name")
                                    ),
                                    safe(
                                            resultSet.getString("call_sign")
                                    ),
                                    resultSet.getInt("value")
                            )
                    );
                }
            }

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка получения TOP",
                    e
            );
        }

        return result;
    }


    /*
     * Вспомогательные методы.
     */

    private void updateString(String column, String value, long userId) {

        String sql =
                "UPDATE users SET "
                        + column
                        + " = ? WHERE id = ?";

        try (
                Connection connection =
                        getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        )
        ) {

            statement.setString(1, value);
            statement.setLong(2, userId);
            statement.executeUpdate();

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка обновления поля: "
                            + column,
                    e
            );
        }
    }

    private void updateInt(String column, int value, long userId) {

        String sql =
                "UPDATE users SET "
                        + column
                        + " = ? WHERE id = ?";

        try (
                Connection connection =
                        getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        )
        ) {

            statement.setInt(1, value);
            statement.setLong(2, userId);
            statement.executeUpdate();

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка обновления поля: "
                            + column,
                    e
            );
        }
    }

    private void updateLong(String column, long value, long userId) {

        String sql =
                "UPDATE users SET "
                        + column
                        + " = ? WHERE id = ?";

        try (
                Connection connection =
                        getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        )
        ) {

            statement.setLong(1, value);
            statement.setLong(2, userId);
            statement.executeUpdate();

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка обновления поля: "
                            + column,
                    e
            );
        }
    }


    /*
     * Миграция колонок старой базы.
     */

    /**
     * Перенос званий старой шкалы (0-7, меньший уровень = выше ранг)
     * в новую шкалу клуба (1-10, больший уровень = выше ранг).
     */
    private void migrateRanks(
            Statement statement
    )
            throws SQLException {

        statement.executeUpdate(
                "UPDATE users SET rank = 'VOZHD_CLUBA' WHERE rank = 'EL_PATRON'"
        );

        statement.executeUpdate(
                "UPDATE users SET rank = 'PERVYI_SEKRETAR' "
                        + "WHERE rank = 'CONSIGLIERE_ESTERNO'"
        );

        statement.executeUpdate(
                "UPDATE users SET rank = 'CHLEN_PREZIDIUMA' "
                        + "WHERE rank = 'LOS_SECRETARIOS'"
        );

        statement.executeUpdate(
                "UPDATE users SET rank = 'NARODNYI_KOMISSAR' "
                        + "WHERE rank = 'GRAN_MAGISTER'"
        );

        statement.executeUpdate(
                "UPDATE users SET rank = 'STARSHII_INSTRUKTOR' "
                        + "WHERE rank = 'MAGISTER'"
        );

        statement.executeUpdate(
                "UPDATE users SET rank = 'KOMANDIR_OTRIADA' "
                        + "WHERE rank = 'COMANDANTE'"
        );

        statement.executeUpdate(
                "UPDATE users SET rank = 'AKTIVIST' WHERE rank = 'CABALLERO'"
        );

        statement.executeUpdate(
                "UPDATE users SET rank = 'KANDIDAT' WHERE rank = 'NOVICIO'"
        );
    }


    private void addColumnIfNotExists(
            Connection connection,
            String tableName,
            String columnName,
            String columnDefinition
    )
            throws SQLException {

        try (
                ResultSet resultSet =
                        connection.getMetaData()
                                .getColumns(
                                        null,
                                        null,
                                        tableName,
                                        columnName
                                )
        ) {

            if (
                    resultSet.next()
            ) {

                return;
            }
        }

        try (
                Statement statement =
                        connection.createStatement()
        ) {

            statement.executeUpdate(
                    "ALTER TABLE "
                            + tableName
                            + " ADD COLUMN "
                            + columnName
                            + " "
                            + columnDefinition
            );
        }
    }


    private String safe(String value) {

        return value == null
                || value.isBlank()
                ? "Не указано"
                : value;
    }


    /*
     * Результат перевода баланса.
     */
    public enum TransferResult {

        OK,

        INVALID_AMOUNT,

        SELF_TRANSFER,

        INSUFFICIENT_FUNDS,

        OVERFLOW,

        NOT_FOUND
    }


    /*
     * Строка TOP-выдачи.
     */
    public record TopEntry(
            long id,
            String name,
            String callSign,
            int value
    ) {
    }

    /*
     * Строка распределения по званиям.
     */
    public record RankCount(
            String rankName,
            int level,
            int count
    ) {
    }
}