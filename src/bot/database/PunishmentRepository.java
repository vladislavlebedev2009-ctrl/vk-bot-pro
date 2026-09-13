package bot.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * История наказаний (VK Bot PRO 2.0).
 *
 * Каждое наказание хранит актора, цель, тип, причину, срок и статус.
 * Временные наказания (MUTE) автоматически переводятся в EXPIRED
 * сервисом {@code PunishmentService}.
 */
public class PunishmentRepository {

    private final Database database;


    public PunishmentRepository(
            Database database
    ) {

        this.database =
                database;
    }


    public void init() {

        try (
                Connection connection =
                        database.getConnection();

                Statement statement =
                        connection.createStatement()
        ) {

            statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS punishments (

                        id INTEGER PRIMARY KEY AUTOINCREMENT,

                        target_id INTEGER NOT NULL,

                        actor_id INTEGER NOT NULL,

                        type TEXT NOT NULL,

                        reason TEXT DEFAULT '',

                        duration_minutes INTEGER DEFAULT 0,

                        ends_at INTEGER DEFAULT 0,

                        status TEXT DEFAULT 'ACTIVE',

                        created_at INTEGER NOT NULL

                    )
                    """
            );

            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_punishments_target "
                            + "ON punishments(target_id, created_at)"
            );

            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_punishments_active "
                            + "ON punishments(status, ends_at)"
            );

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка инициализации таблицы punishments",
                    e
            );
        }
    }


    public long add(
            long targetId,
            long actorId,
            String type,
            String reason,
            long durationMinutes,
            long endsAt
    ) {

        String sql =
                """
                INSERT INTO punishments
                (
                    target_id,
                    actor_id,
                    type,
                    reason,
                    duration_minutes,
                    ends_at,
                    status,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
                """;

        try (
                Connection connection =
                        database.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql,
                                Statement.RETURN_GENERATED_KEYS
                        )
        ) {

            statement.setLong(1, targetId);
            statement.setLong(2, actorId);
            statement.setString(3, type);
            statement.setString(4, reason == null ? "" : reason);
            statement.setLong(5, durationMinutes);
            statement.setLong(6, endsAt);
            statement.setLong(7, System.currentTimeMillis());

            statement.executeUpdate();

            try (
                    ResultSet keys =
                            statement.getGeneratedKeys()
            ) {

                if (
                        keys.next()
                ) {

                    return keys.getLong(1);
                }
            }

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка добавления наказания",
                    e
            );
        }

        return 0L;
    }


    /**
     * Снять все активные наказания указанного типа.
     *
     * @return количество затронутых записей
     */
    public int revokeActive(
            long targetId,
            String type
    ) {

        String sql =
                """
                UPDATE punishments
                SET status = 'REVOKED'
                WHERE target_id = ?
                  AND type = ?
                  AND status = 'ACTIVE'
                """;

        try (
                Connection connection =
                        database.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        )
        ) {

            statement.setLong(1, targetId);
            statement.setString(2, type);

            return statement.executeUpdate();

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка снятия наказания",
                    e
            );
        }
    }


    /**
     * Снять одно последнее активное наказание указанного типа
     * (используется для /unwarn).
     */
    public int revokeLastActive(
            long targetId,
            String type
    ) {

        String sql =
                """
                UPDATE punishments
                SET status = 'REVOKED'
                WHERE id = (
                    SELECT id
                    FROM punishments
                    WHERE target_id = ?
                      AND type = ?
                      AND status = 'ACTIVE'
                    ORDER BY id DESC
                    LIMIT 1
                )
                """;

        try (
                Connection connection =
                        database.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        )
        ) {

            statement.setLong(1, targetId);
            statement.setString(2, type);

            return statement.executeUpdate();

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка снятия предупреждения",
                    e
            );
        }
    }


    /**
     * Количество активных наказаний (WARN/BAN/MUTE со статусом ACTIVE).
     */
    public int countActive() {

        String sql =
                "SELECT COUNT(*) FROM punishments WHERE status = 'ACTIVE'";

        try (
                Connection connection =
                        database.getConnection();

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
                    "Ошибка подсчёта активных наказаний",
                    e
            );
        }

        return 0;
    }


    public boolean hasActive(
            long targetId,
            String type
    ) {

        String sql =
                """
                SELECT 1
                FROM punishments
                WHERE target_id = ?
                  AND type = ?
                  AND status = 'ACTIVE'
                LIMIT 1
                """;

        try (
                Connection connection =
                        database.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        )
        ) {

            statement.setLong(1, targetId);
            statement.setString(2, type);

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
                    "Ошибка проверки наказания",
                    e
            );
        }
    }


    /**
     * Пометить истёкшие временные наказания как EXPIRED.
     *
     * @return список истёкших MUTE (цель + момент окончания),
     *         чтобы вызывающий код снял mute_end у пользователя
     */
    public List<ExpiredMute> expireDue() {

        long now =
                System.currentTimeMillis();

        List<ExpiredMute> expired =
                new ArrayList<>();

        String selectSql =
                """
                SELECT target_id, ends_at
                FROM punishments
                WHERE status = 'ACTIVE'
                  AND ends_at > 0
                  AND ends_at <= ?
                """;

        String updateSql =
                """
                UPDATE punishments
                SET status = 'EXPIRED'
                WHERE status = 'ACTIVE'
                  AND ends_at > 0
                  AND ends_at <= ?
                """;

        try (
                Connection connection =
                        database.getConnection()
        ) {

            try (
                    PreparedStatement select =
                            connection.prepareStatement(
                                    selectSql
                            )
            ) {

                select.setLong(1, now);

                try (
                        ResultSet resultSet =
                                select.executeQuery()
                ) {

                    while (
                            resultSet.next()
                    ) {

                        expired.add(
                                new ExpiredMute(
                                        resultSet.getLong("target_id"),
                                        resultSet.getLong("ends_at")
                                )
                        );
                    }
                }
            }

            try (
                    PreparedStatement update =
                            connection.prepareStatement(
                                    updateSql
                            )
            ) {

                update.setLong(1, now);
                update.executeUpdate();
            }

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка истечения наказаний",
                    e
            );
        }

        return expired;
    }


    public List<Punishment> getHistory(
            long targetId,
            int limit
    ) {

        String sql =
                """
                SELECT *
                FROM punishments
                WHERE target_id = ?
                ORDER BY id DESC
                LIMIT ?
                """;

        List<Punishment> result =
                new ArrayList<>();

        try (
                Connection connection =
                        database.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        )
        ) {

            statement.setLong(1, targetId);
            statement.setInt(2, limit);

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                while (
                        resultSet.next()
                ) {

                    result.add(
                            map(
                                    resultSet
                            )
                    );
                }
            }

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка получения истории наказаний",
                    e
            );
        }

        return result;
    }


    private Punishment map(
            ResultSet resultSet
    )
            throws SQLException {

        return new Punishment(
                resultSet.getLong("id"),
                resultSet.getLong("target_id"),
                resultSet.getLong("actor_id"),
                resultSet.getString("type"),
                resultSet.getString("reason"),
                resultSet.getLong("duration_minutes"),
                resultSet.getLong("ends_at"),
                resultSet.getString("status"),
                resultSet.getLong("created_at")
        );
    }


    public record Punishment(
            long id,
            long targetId,
            long actorId,
            String type,
            String reason,
            long durationMinutes,
            long endsAt,
            String status,
            long createdAt
    ) {
    }


    public record ExpiredMute(
            long targetId,
            long endsAt
    ) {
    }
}