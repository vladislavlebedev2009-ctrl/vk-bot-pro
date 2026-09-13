package bot.service;


import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class UserTargetResolver {

    /**
     * VK-упоминание: [id123456789|Имя] или [id123456789|Имя Фамилия].
     */
    private static final Pattern VK_MENTION_PATTERN =
            Pattern.compile(
                    "\\[id(\\d+)\\|[^]]+]"
            );

    /**
     * @id123456789 или id123456789 (без "склейки" с соседними символами).
     */
    private static final Pattern ID_PREFIX_PATTERN =
            Pattern.compile(
                    "(?<![A-Za-z0-9_])@?id(\\d+)"
            );

    /**
     * Голый числовой VK ID.
     */
    private static final Pattern BARE_ID_PATTERN =
            Pattern.compile(
                    "(?<![A-Za-z0-9_])(\\d{5,})(?![A-Za-z0-9_])"
            );


    private UserTargetResolver() {
    }


    /**
     * Ищет первое вхождение VK ID в тексте. Поддерживаются:
     * [idN|Имя], @idN, @idN (Имя), idN и голый числовой ID.
     *
     * @return ID пользователя или null, если ни один вариант не найден
     */
    public static Long resolve(
            String value
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {

            return null;
        }

        String target =
                value.trim();

        String bestId = null;

        long bestIndex =
                Long.MAX_VALUE;

        Matcher mention =
                VK_MENTION_PATTERN.matcher(
                        target
                );

        if (
                mention.find()
        ) {

            bestId =
                    mention.group(
                            1
                    );

            bestIndex =
                    mention.start();
        }

        Matcher prefix =
                ID_PREFIX_PATTERN.matcher(
                        target
                );

        if (
                prefix.find()
                        && prefix.start()
                        < bestIndex
        ) {

            bestId =
                    prefix.group(
                            1
                    );

            bestIndex =
                    prefix.start();
        }

        Matcher bare =
                BARE_ID_PATTERN.matcher(
                        target
                );

        if (
                bare.find()
                        && bare.start()
                        < bestIndex
        ) {

            bestId =
                    bare.group(
                            1
                    );

            bestIndex =
                    bare.start();
        }

        if (
                bestId == null
        ) {

            return null;
        }

        return parseId(
                bestId
        );
    }


    private static Long parseId(
            String value
    ) {

        try {

            long id =
                    Long.parseLong(
                            value
                    );

            if (
                    id <= 0
            ) {

                return null;
            }

            return id;

        } catch (
                NumberFormatException e
        ) {

            return null;
        }
    }
}