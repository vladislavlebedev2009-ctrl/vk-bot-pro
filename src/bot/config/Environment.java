package bot.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Единая точка доступа к конфигурации процесса.
 *
 * Приоритет значений:
 *  1. настоящие переменные окружения (System.getenv);
 *  2. файл .env в рабочей директории (только для отсутствующих ключей);
 *  3. значения по умолчанию, задаваемые вызывающим кодом.
 */
public final class Environment {

    private static final String ENV_FILE = ".env";

    private static final Map<String, String> VARIABLES =
            load();

    private Environment() {
    }

    public static String get(String key) {
        return VARIABLES.get(key);
    }

    public static String get(String key, String defaultValue) {
        String value = VARIABLES.get(key);
        return value == null || value.isBlank()
                ? defaultValue
                : value;
    }

    private static Map<String, String> load() {

        Map<String, String> result =
                new HashMap<>();

        for (
                Map.Entry<String, String> entry :
                System.getenv().entrySet()
        ) {

            result.put(
                    entry.getKey(),
                    entry.getValue()
            );
        }

        Path envFile =
                Path.of(
                        ENV_FILE
                );

        if (
                !Files.exists(
                        envFile
                )
        ) {

            return result;
        }

        try {

            for (
                    String rawLine :
                    Files.readAllLines(
                            envFile
                    )
            ) {

                String line =
                        rawLine.trim();

                if (
                        line.isEmpty()
                                || line.startsWith(
                                "#"
                        )
                ) {

                    continue;
                }

                int separator =
                        line.indexOf(
                                '='
                        );

                if (
                        separator <= 0
                ) {

                    continue;
                }

                String key =
                        line.substring(
                                0,
                                separator
                        ).trim();

                String value =
                        line.substring(
                                separator + 1
                        ).trim();

                if (
                        value.length() >= 2
                                && value.startsWith(
                                "\""
                        )
                                && value.endsWith(
                                "\""
                        )
                ) {

                    value =
                            value.substring(
                                    1,
                                    value.length() - 1
                            );
                }

                if (
                        !result.containsKey(
                                key
                        )
                ) {

                    result.put(
                            key,
                            value
                    );
                }
            }

        } catch (
                IOException ignored
        ) {
            // .env недоступен — используем только переменные окружения
        }

        return result;
    }
}