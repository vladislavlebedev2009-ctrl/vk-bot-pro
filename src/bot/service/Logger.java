package bot.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {

    private static final Path LOG_FILE =
            Path.of("bot-log.txt");

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd HH:mm:ss"
            );

    private final String className;


    public Logger(
            Class<?> clazz
    ) {

        this.className =
                clazz.getSimpleName();
    }


    public void info(
            String message
    ) {

        write(
                "INFO",
                message
        );
    }


    public void debug(
            String message
    ) {

        write(
                "DEBUG",
                message
        );
    }


    public void warning(
            String message
    ) {

        write(
                "WARNING",
                message
        );
    }


    public void error(
            String message
    ) {

        write(
                "ERROR",
                message
        );
    }


    public void error(
            String message,
            Throwable throwable
    ) {

        StringBuilder result =
                new StringBuilder();

        result.append(
                message
        );


        if (
                throwable != null
        ) {

            result.append(
                    "\n"
            );

            result.append(
                    throwable
                            .getClass()
                            .getName()
            );

            result.append(
                    ": "
            );

            result.append(
                    throwable.getMessage()
            );


            for (
                    StackTraceElement element :
                    throwable.getStackTrace()
            ) {

                result.append(
                        "\n    at "
                );

                result.append(
                        element
                );
            }
        }


        write(
                "ERROR",
                result.toString()
        );
    }


    private synchronized void write(
            String level,
            String message
    ) {

        String time =
                LocalDateTime.now()
                        .format(
                                FORMATTER
                        );


        String log =
                "["
                        + time
                        + "] ["
                        + level
                        + "] ["
                        + className
                        + "] "
                        + message
                        + System.lineSeparator();


        try {

            Files.writeString(
                    LOG_FILE,
                    log,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );


        } catch (
                IOException e
        ) {

            /*
             * Ничего не выводим в консоль.
             * Если лог-файл недоступен,
             * ошибка просто не отправляется
             * в стандартный вывод.
             */
        }
    }
}