package bot.database;

import bot.config.BotConfig;
import bot.service.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ChatRepository {

    private static final Logger logger =
            new Logger(
                    ChatRepository.class
            );

    private final BotConfig config;

    private final Database database;


    public ChatRepository(
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
                        database.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                CREATE TABLE IF NOT EXISTS chats (
                                    peer_id INTEGER PRIMARY KEY,
                                    connected_by INTEGER NOT NULL,
                                    connected_at INTEGER NOT NULL
                                )
                                """
                        )
        ) {

            statement.executeUpdate();


            logger.info(
                    "Таблица chats готова"
            );


        } catch (
                SQLException e
        ) {

            throw new RuntimeException(
                    "Ошибка инициализации таблицы chats",
                    e
            );
        }
    }


    public boolean isConnected(
            long peerId
    ) {

        String sql =
                """
                SELECT 1
                FROM chats
                WHERE peer_id = ?
                """;


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
                    peerId
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

            logger.error(
                    "Ошибка проверки подключения беседы",
                    e
            );


            return false;
        }
    }


    public boolean connect(
            long peerId,
            long userId
    ) {

        String sql =
                """
                INSERT OR REPLACE INTO chats
                (
                    peer_id,
                    connected_by,
                    connected_at
                )
                VALUES (?, ?, ?)
                """;


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
                    peerId
            );


            statement.setLong(
                    2,
                    userId
            );


            statement.setLong(
                    3,
                    System.currentTimeMillis()
            );


            statement.executeUpdate();


            return true;


        } catch (
                SQLException e
        ) {

            logger.error(
                    "Ошибка подключения беседы",
                    e
            );


            return false;
        }
    }


    public boolean disconnect(
            long peerId
    ) {

        String sql =
                """
                DELETE FROM chats
                WHERE peer_id = ?
                """;


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
                    peerId
            );


            statement.executeUpdate();


            return true;


        } catch (
                SQLException e
        ) {

            logger.error(
                    "Ошибка отключения беседы",
                    e
            );


            return false;
        }
    }


    public int getConnectedChatsCount(
            long userId
    ) {

        String sql =
                """
                SELECT COUNT(*)
                FROM chats
                WHERE connected_by = ?
                """;


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

            logger.error(
                    "Ошибка получения количества бесед",
                    e
            );
        }


        return 0;
    }
}