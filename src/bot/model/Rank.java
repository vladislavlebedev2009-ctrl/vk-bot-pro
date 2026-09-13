package bot.model;

/**
 * Иерархия званий «Клуба имени Иосифа Сталина».
 *
 * Всего 10 званий. Чем больше {@code level}, тем ранг выше:
 * Кандидат (1) — самый низкий, Вождь клуба (10) — высший.
 * Кроме названия хранит римский номер (для /ranks) и
 * краткое описание.
 */
public enum Rank {

    KANDIDAT(
            1,
            "Кандидат",
            "Новый участник",
            "I"
    ),

    TOVARISHCH(
            2,
            "Товарищ",
            "Полноправный участник",
            "II"
    ),

    AKTIVIST(
            3,
            "Активист",
            "Активный участник",
            "III"
    ),

    ORGANIZATOR(
            4,
            "Организатор",
            "Младший руководящий состав",
            "IV"
    ),

    KOMANDIR_OTRIADA(
            5,
            "Командир отряда",
            "Руководитель группы",
            "V"
    ),

    STARSHII_INSTRUKTOR(
            6,
            "Старший инструктор",
            "Старший административный состав",
            "VI"
    ),

    NARODNYI_KOMISSAR(
            7,
            "Народный комиссар",
            "Руководитель подразделения",
            "VII"
    ),

    CHLEN_PREZIDIUMA(
            8,
            "Член Президиума",
            "Высший руководящий состав",
            "VIII"
    ),

    PERVYI_SEKRETAR(
            9,
            "Первый секретарь",
            "Главный заместитель",
            "IX"
    ),

    VOZHD_CLUBA(
            10,
            "Вождь клуба",
            "Высшее руководство",
            "X"
    );

    private final int level;

    private final String displayName;

    private final String description;

    private final String roman;

    Rank(
            int level,
            String displayName,
            String description,
            String roman
    ) {

        this.level =
                level;

        this.displayName =
                displayName;

        this.description =
                description;

        this.roman =
                roman;
    }

    public int getLevel() {

        return level;
    }

    public String getDisplayName() {

        return displayName;
    }

    public String getDescription() {

        return description;
    }

    public String getRoman() {

        return roman;
    }

    /**
     * Поиск звания по имени (идентификатору) или русскому названию.
     * Например: "TOVARISHCH", "Товарищ", "товарищ".
     */
    public static Rank fromString(
            String value
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {

            return null;
        }

        String normalized =
                value
                        .trim()
                        .toUpperCase()
                        .replace(
                                " ",
                                "_"
                        );

        for (
                Rank rank :
                values()
        ) {

            if (
                    rank.name()
                            .equals(
                                    normalized
                            )
            ) {

                return rank;
            }
        }

        for (
                Rank rank :
                values()
        ) {

            if (
                    rank.getDisplayName()
                            .equalsIgnoreCase(
                                    value.trim()
                            )
            ) {

                return rank;
            }
        }

        return null;
    }

    public static Rank fromLevel(
            int level
    ) {

        for (
                Rank rank :
                values()
        ) {

            if (
                    rank.getLevel()
                            == level
            ) {

                return rank;
            }
        }

        return null;
    }
}