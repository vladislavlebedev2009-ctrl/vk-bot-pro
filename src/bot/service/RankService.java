package bot.service;

import bot.config.BotConfig;
import bot.model.Rank;

/**
 * Единая матрица прав по званиям клуба.
 *
 * Иерархия (level больше = звание выше, максимум 10 у Вождя клуба).
 * Вождь клуба ({@code VK_PATRON_ID}) всегда имеет все права.
 * Права pсть категориями: PROFILE, USERS, RANK_MANAGEMENT,
 * MODERATION, REPUTATION, ECONOMY, HISTORY, POST, ADMINISTRATION.
 */
public final class RankService {

    private RankService() {
    }

    public static boolean isPatron(
            long userId,
            BotConfig config
    ) {

        return userId ==
                config.getPatronId();
    }

    /**
     * Может ли пользователь (userId с званием rank) выполнить
     * действие с указанным правом (permission).
     *
     * Вождь клуба имеет ВСЕ права. Для остальных проверяется
     * минимальный уровень звания, необходимый для категории.
     */
    public static boolean canUse(
            long userId,
            Rank rank,
            String permission,
            BotConfig config
    ) {

        if (
                isPatron(
                        userId,
                        config
                )
        ) {

            return true;
        }

        if (
                rank == null
                        || permission == null
        ) {

            return false;
        }

        int level =
                rank.getLevel();

        return switch (
                permission.toLowerCase()
        ) {

            /*
             * Доступно всем зарегистрированным (PROFILE, USER,
             * REPUTATION, ECONOMY, /connect, /rank, /ranks, /top)
             */
            case "profile",
                 "me",
                 "myinfo",
                 "info",
                 "users",
                 "rep",
                 "pay",
                 "balance",
                 "top",
                 "toprep",
                 "topbalance",
                 "rank",
                 "ranks",
                 "connect",
                 "link",
                 "register",
                 "start",
                 "help",
                 "ping",
                 "probe" ->
                    level
                            >= Rank.KANDIDAT
                            .getLevel();

            /*
             * MODERATION и HISTORY (младший руководящий состав)
             */
            case "ban",
                 "unban",
                 "mute",
                 "unmute",
                 "warn",
                 "unwarn",
                 "history" ->
                    level
                            >= Rank.ORGANIZATOR
                            .getLevel();

            /*
             * RANK_MANAGEMENT: назначение званий
             */
            case "setrank" ->
                    level
                            >= Rank.STARSHII_INSTRUKTOR
                            .getLevel();

            /*
             * POST: публикация в сообществе
             */
            case "post",
                 "publish" ->
                    level
                            >= Rank.NARODNYI_KOMISSAR
                            .getLevel();

            /*
             * ADMINISTRATION: аудит, статистика, инструкции
             */
            case "audit",
                 "addbot",
                 "stats",
                 "administration" ->
                    level
                            >= Rank.CHLEN_PREZIDIUMA
                            .getLevel();

            default -> false;
        };
    }
}