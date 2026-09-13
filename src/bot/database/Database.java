package bot.database;


import bot.service.Logger;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;


/**
 * Единый источник подключений к SQLite для бота и Web-панели.
 *
 * При каждом открытии соединения включаются:
 *  - WAL (журнал) — параллельные читатели и один писатель,
 *    меньше блокировок при одновременной работе бота и панели;
 *  - busy_timeout — ожидание освобождения файла вместо мгновенной
 *    ошибки "database is locked".
 */
public class Database {


    private static final Logger logger =
            new Logger(Database.class);

    private static final int BUSY_TIMEOUT_MILLIS =
            5000;


    private final String url;


    public Database(String databaseName) {


        this.url =
                "jdbc:sqlite:" + databaseName;


        loadDriver();
    }


    private void loadDriver() {


        try {


            Class.forName(
                    "org.sqlite.JDBC"
            );


            logger.info(
                    "SQLite JDBC драйвер загружен"
            );


        } catch (ClassNotFoundException e) {


            logger.error(
                    "SQLite JDBC драйвер не найден",
                    e
            );


            throw new RuntimeException(
                    "Добавь sqlite-jdbc в зависимости проекта",
                    e
            );
        }
    }


    public Connection getConnection()
            throws SQLException {


        Connection connection =
                DriverManager.getConnection(
                        url
                );

        try (
                Statement statement =
                        connection.createStatement()
        ) {

            statement.execute(
                    "PRAGMA busy_timeout = "
                            + BUSY_TIMEOUT_MILLIS
            );

            statement.execute(
                    "PRAGMA journal_mode = WAL"
            );

        } catch (
                SQLException e
        ) {

            // PRAGMA недоступен — соединение всё равно используем
            logger.warning(
                    "Не удалось применить PRAGMA к SQLite: "
                            + e.getMessage()
            );
        }


        return connection;
    }
}