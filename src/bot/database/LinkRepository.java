package bot.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Связка VK ↔ Discord (таблица account_links).
 *
 * ВАЖНО: связь строго 1:1 в обе стороны — один VK-аккаунт может быть
 * привязан максимум к одному Discord и наоборот. Discord ID никогда
 * не попадает в таблицу users (она ключуется по VK ID), поэтому
 * Discord-команды работают только через этот маппинг.
 *
 * Код привязки одноразовый и истекает через срок из LinkService.
 */
public class LinkRepository {

    private final Database database;


    public LinkRepository(
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
                    CREATE TABLE IF NOT EXISTS account_links (

                        id INTEGER PRIMARY KEY AUTOINCREMENT,

                        vk_user_id INTEGER NOT NULL,

                        discord_user_id INTEGER,

                        code TEXT NOT NULL DEFAULT '',

                        code_created_at INTEGER NOT NULL DEFAULT 0,

                        code_expires_at INTEGER NOT NULL DEFAULT 0,

                        linked_at INTEGER NOT NULL DEFAULT 0

                    )
                    """
            );

            statement.executeUpdate(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_account_links_vk "
                            + "ON account_links(vk_user_id)"
            );

            statement.executeUpdate(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_account_links_discord "
                            + "ON account_links(discord_user_id)"
            );

            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_account_links_code "
                            + "ON account_links(code)"
            );

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка инициализации таблицы account_links",
                    e
            );
        }
    }


    public LinkRow findByVk(
            long vkUserId
    ) {

        return queryOne(
                "SELECT * FROM account_links WHERE vk_user_id = ?",
                vkUserId
        );
    }


    public LinkRow findByCode(
            String code
    ) {

        if (
                code == null
                        || code.isBlank()
        ) {

            return null;
        }

        try (
                Connection connection =
                        database.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT * FROM account_links "
                                        + "WHERE code = ? LIMIT 1"
                        )
        ) {

            statement.setString(
                    1,
                    code
            );

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                if (
                        resultSet.next()
                ) {

                    return map(
                            resultSet
                    );
                }
            }

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка поиска кода привязки",
                    e
            );
        }

        return null;
    }


    public Long findVkByDiscord(
            long discordUserId
    ) {

        LinkRow row =
                queryOne(
                        "SELECT * FROM account_links "
                                + "WHERE discord_user_id = ? LIMIT 1",
                        discordUserId
                );

        return row == null
                ? null
                : row.vkUserId();
    }


    /**
     * Привязан ли профиль к Discord (row уже имеет discord_user_id).
     */
    public boolean isLinked(
            long vkUserId
    ) {

        LinkRow row =
                findByVk(
                        vkUserId
                );

        return row != null
                && row.discordUserId() != null;
    }


    /**
     * Сохранить (или обновить) код привязки для VK-пользователя.
     * Повторная генерация для ещё не привязанного профиля перезаписывает
     * старый код — это подтверждается в LinkService.
     */
    public void saveCode(
            long vkUserId,
            String code,
            long expiresAt
    ) {

        LinkRow existing =
                findByVk(
                        vkUserId
                );

        if (
                existing == null
        ) {

            try (
                    Connection connection =
                            database.getConnection();

                    PreparedStatement statement =
                            connection.prepareStatement(
                                    """
                                    INSERT INTO account_links
                                    (
                                        vk_user_id,
                                        discord_user_id,
                                        code,
                                        code_created_at,
                                        code_expires_at,
                                        linked_at
                                    )
                                    VALUES (?, NULL, ?, ?, 0, 0)
                                    """
                            )
            ) {

                long now =
                        System.currentTimeMillis();

                statement.setLong(1, vkUserId);
                statement.setString(2, code);
                statement.setLong(3, now);
                statement.setLong(4, expiresAt);

                statement.executeUpdate();

                return;

            } catch (
                    SQLException e
            ) {

                throw new RuntimeException(
                        "Ошибка сохранения кода привязки (новый): "
                                + vkUserId,
                        e
                );
            }
        }

        try (
                Connection connection =
                        database.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                UPDATE account_links
                                SET code = ?,
                                    code_created_at = ?,
                                    code_expires_at = ?
                                WHERE vk_user_id = ?
                                """
                        )
        ) {

            statement.setString(1, code);
            statement.setLong(2, System.currentTimeMillis());
            statement.setLong(3, expiresAt);
            statement.setLong(4, vkUserId);

            statement.executeUpdate();

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка обновления кода привязки: "
                            + vkUserId,
                    e
            );
        }
    }


    /**
     * Завершить привязку: дискорд привязан, код погашен,
     * новый код больше генерировать нельзя.
     */
    public void bind(
            long vkUserId,
            long discordUserId
    ) {

        try (
                Connection connection =
                        database.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                UPDATE account_links
                                SET discord_user_id = ?,
                                    linked_at = ?,
                                    code = '',
                                    code_expires_at = 0
                                WHERE vk_user_id = ?
                                """
                        )
        ) {

            statement.setLong(1, discordUserId);
            statement.setLong(2, System.currentTimeMillis());
            statement.setLong(3, vkUserId);

            statement.executeUpdate();

        } catch (
                SQLException e
        ) {

            /*
             * Строки одного Discord с двумя VK исключает тройной
             * уникальный индекс, поэтому до такого не дойдёт — но
             * защита всё равно нужна на уровне запроса.
             */
            throw new RuntimeException(
                    "Ошибка привязки Discord к VK: "
                            + vkUserId,
                    e
            );
        }
    }


    private LinkRow queryOne(
            String sql,
            Long id
    ) {

        try (
                Connection connection =
                        database.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        )
        ) {

            if (
                    id != null
            ) {

                statement.setLong(
                        1,
                        id
                );
            }

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                if (
                        resultSet.next()
                ) {

                    return map(
                            resultSet
                    );
                }
            }

        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка чтения account_links",
                    e
            );
        }

        return null;
    }


    private LinkRow map(
            ResultSet resultSet
    )
            throws SQLException {

        long discordId =
                resultSet.getLong(
                        "discord_user_id"
                );

        return new LinkRow(
                resultSet.getLong("id"),
                resultSet.getLong("vk_user_id"),
                resultSet.wasNull()
                        ? null
                        : discordId,
                resultSet.getString("code"),
                resultSet.getLong("code_created_at"),
                resultSet.getLong("code_expires_at"),
                resultSet.getLong("linked_at")
        );
    }


    public record LinkRow(
            long id,
            long vkUserId,
            Long discordUserId,
            String code,
            long codeCreatedAt,
            long codeExpiresAt,
            long linkedAt
    ) {
    }
}