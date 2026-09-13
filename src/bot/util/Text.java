package bot.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Небольшие утилиты форматирования для команд бота.
 */
public final class Text {

    private Text() {
    }

    /**
     * Группирует число пробелами: 1000000 -> "1 000 000".
     */
    public static String number(
            long value
    ) {

        return String.format(
                Locale.ROOT,
                "%,d",
                value
        ).replace(
                ",",
                " "
        );
    }

    public static String safe(
            String value,
            String defaultValue
    ) {

        return value == null
                || value.isBlank()
                ? defaultValue
                : value;
    }

    public static String date(
            long millis
    ) {

        if (
                millis <= 0
        ) {

            return "Не установлено";
        }

        return new SimpleDateFormat(
                "dd.MM.yyyy"
        ).format(
                new Date(
                        millis
                )
        );
    }

    public static String dateTime(
            long millis
    ) {

        if (
                millis <= 0
        ) {

            return "Не установлено";
        }

        return new SimpleDateFormat(
                "dd.MM.yyyy HH:mm"
        ).format(
                new Date(
                        millis
                )
        );
    }

    public static String clockTime(
            long millis
    ) {

        return new SimpleDateFormat(
                "dd.MM HH:mm"
        ).format(
                new Date(
                        millis
                )
        );
    }
}