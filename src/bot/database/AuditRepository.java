package bot.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Журнал административных действий (audit log).
 *
 * Хранит, кто и что сделал, над кем, с какими параметрами
 * и каков результат. Используется ботом и Web-панелью.
 */
public class AuditRepository {

    private final Database database;


    public AuditRepository(
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
                    CREATE TABLE IF NOT EXISTS audit_log (

                        id INTEGER PRIMARY KEY AUTOINCREMENT,

                        actor_id INTEGER NOT NULL,

                        action TEXT NOT NULL,

                        target_id INTEGER NOT NULL DEFAULT 0,

                        detail TEXT DEFAULT '',

                        result TEXT DEFAULT '',

                        created_at INTEGER NOT NULL

                    )
                    """
            );

            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_audit_actor "
                            + "ON audit_log(actor_id, created_at)"
            );

            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_audit_target "
                            + "ON audit_log(target_id, created_at)"
            );

            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_audit_created "
                            + "ON audit_log(created_at)"
            );

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка инициализации audit_log",
                    e
            );
        }
    }


    public void log(
            long actorId,
            String action,
            long targetId,
            String detail,
            String result
    ) {

        String sql =
                """
                INSERT INTO audit_log
                (
                    actor_id,
                    action,
                    target_id,
                    detail,
                    result,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (
                Connection connection =
                        database.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        )
        ) {

            statement.setLong(1, actorId);
            statement.setString(2, action);
            statement.setLong(3, targetId);
            statement.setString(4, detail == null ? "" : detail);
            statement.setString(5, result == null ? "" : result);
            statement.setLong(6, System.currentTimeMillis());

            statement.executeUpdate();

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка записи в audit log",
                    e
            );
        }
    }


    public List<AuditEntry> getRecent(
            int limit
    ) {

        return query(
                "SELECT * FROM audit_log ORDER BY id DESC LIMIT ?",
                null,
                limit
        );
    }


    public List<AuditEntry> getForTarget(
            long targetId,
            int limit
    ) {

        return query(
                "SELECT * FROM audit_log WHERE target_id = ? "
                        + "ORDER BY id DESC LIMIT ?",
                targetId,
                limit
        );
    }


    public List<AuditEntry> getForActor(
            long actorId,
            int limit
    ) {

        return query(
                "SELECT * FROM audit_log WHERE actor_id = ? "
                        + "ORDER BY id DESC LIMIT ?",
                actorId,
                limit
        );
    }


    /**
     * Действия, где пользователь выступает либо инициатором,
     * либо целью (полная история участника).
     */
    public List<AuditEntry> getForUser(
            long userId,
            int limit
    ) {

        String sql =
                "SELECT * FROM audit_log "
                        + "WHERE actor_id = ? OR target_id = ? "
                        + "ORDER BY id DESC LIMIT ?";

        List<AuditEntry> result =
                new ArrayList<>();

        try (
                Connection connection =
                        database.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        )
        ) {

            statement.setLong(
                    1,
                    userId
            );

            statement.setLong(
                    2,
                    userId
            );

            statement.setInt(
                    3,
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
                            new AuditEntry(
                                    resultSet.getLong("id"),
                                    resultSet.getLong("actor_id"),
                                    resultSet.getString("action"),
                                    resultSet.getLong("target_id"),
                                    resultSet.getString("detail"),
                                    resultSet.getString("result"),
                                    resultSet.getLong("created_at")
                            )
                    );
                }
            }

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка чтения аудита пользователя",
                    e
            );
        }

        return result;
    }


    private List<AuditEntry> query(
            String sql,
            Long filterId,
            int limit
    ) {

        List<AuditEntry> result =
                new ArrayList<>();

        try (
                Connection connection =
                        database.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        )
        ) {

            int index =
                    1;

            if (
                    filterId != null
            ) {

                statement.setLong(
                        index++,
                        filterId
                );
            }

            statement.setInt(
                    index,
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
                            new AuditEntry(
                                    resultSet.getLong("id"),
                                    resultSet.getLong("actor_id"),
                                    resultSet.getString("action"),
                                    resultSet.getLong("target_id"),
                                    resultSet.getString("detail"),
                                    resultSet.getString("result"),
                                    resultSet.getLong("created_at")
                            )
                    );
                }
            }

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка чтения audit log",
                    e
            );
        }

        return result;
    }


    public record AuditEntry(
            long id,
            long actorId,
            String action,
            long targetId,
            String detail,
            String result,
            long createdAt
    ) {
    }
}