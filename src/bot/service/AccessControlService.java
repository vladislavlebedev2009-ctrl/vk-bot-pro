package bot.service;

import bot.command.CommandContext;
import bot.config.BotConfig;
import bot.model.Rank;
import bot.model.User;

/**
 * Централизованная проверка прав и ранговых ограничений.
 *
 * Единственное место, где принимаются решения «может ли актор
 * выполнить действие над целью». Сообщения для ответа в VK
 * формируются здесь же, чтобы команды не дублировали проверки.
 */
public class AccessControlService {

    private final BotConfig config;

    public AccessControlService(
            BotConfig config
    ) {

        this.config =
                config;
    }

    public BotConfig getConfig() {

        return config;
    }

    public boolean isPatron(
            long userId
    ) {

        return userId ==
                config.getPatronId();
    }

    /** Проверка, что актор может использовать команду (без ответа). */
    public boolean canUse(
            CommandContext context,
            String permission
    ) {

        if (
                context.isPatron()
        ) {

            return true;
        }

        Rank rank =
                context.getRank();

        return rank != null
                && RankService.canUse(
                context.getUserId(),
                rank,
                permission,
                config
        );
    }


    /**
     * Проверка, что актор имеет право на команду (permission).
     * При отказе пользователю отправляется понятное сообщение.
     */
    public boolean requirePermission(
            CommandContext context,
            String permission
    ) {

        if (
                context.isPatron()
        ) {

            return true;
        }

        User actor =
                context.getUser();

        if (
                actor == null
        ) {

            context.reply(
                    "⛔ Пользователь не зарегистрирован."
            );

            return false;
        }

        Rank rank =
                actor.getRankEnum();

        if (
                rank == null
                        || !RankService.canUse(
                        actor.getId(),
                        rank,
                        permission,
                        config
                )
        ) {

            context.reply(
                    "⛔ Недостаточно прав для этой команды."
            );

            return false;
        }

        return true;
    }

    /**
     * Можно ли применить наказание (модерацию) к цели.
     *
     * Иерархия: наказывать можно только тех, кто рангом ниже
     * актора. Звание выше или равное актору — неприкосновенно.
     * Вождь клуба (патрон) и сам актор защищены в
     * {@link #canPunish(CommandContext, User)}.
     */
    public boolean canModerate(
            CommandContext context,
            User target
    ) {

        if (
                context.isPatron()
                        || target == null
        ) {

            return true;
        }

        Rank actorRank =
                context.getRank();

        Rank targetRank =
                target.getRankEnum();

        if (
                actorRank == null
                        || targetRank == null
        ) {

            return true;
        }

        if (
                targetRank.getLevel()
                        >= actorRank.getLevel()
        ) {

            context.reply(
                    "⛔ Нельзя применять наказание к пользователю "
                            + "с рангом выше или равным твоему."
            );

            return false;
        }

        return true;
    }

    /**
     * Комплексная проверка перед наказанием цели:
     * Вождь клуба и сам актор защищены, иерархия — через
     * {@link #canModerate(CommandContext, User)}.
     */
    public boolean canPunish(
            CommandContext context,
            User target
    ) {

        if (
                context.isPatron()
                        || target == null
        ) {

            return true;
        }

        if (
                isPatron(
                        target.getId()
                )
        ) {

            context.reply(
                    "👑 Вождь клуба защищён от наказаний."
            );

            return false;
        }

        if (
                target.getId() ==
                        context.getUserId()
        ) {

            context.reply(
                    "❌ Нельзя применить наказание к себе."
            );

            return false;
        }

        return canModerate(
                context,
                target
        );
    }

    /**
     * Можно ли изменять звание цели.
     *
     * Вождь клуба неуязвим, себе менять звание нельзя,
     * целиться «вверх» (в звание выше или равное своему) нельзя.
     */
    public boolean canChangeRank(
            CommandContext context,
            User target
    ) {

        if (
                context.isPatron()
        ) {

            return true;
        }

        if (
                target == null
        ) {

            return true;
        }

        if (
                isPatron(
                        target.getId()
                )
        ) {

            context.reply(
                    "👑 Вождя клуба невозможно понизить или изменить."
            );

            return false;
        }

        if (
                target.getId() ==
                        context.getUserId()
        ) {

            context.reply(
                    "⛔ Нельзя изменить звание самому себе."
            );

            return false;
        }

        Rank actorRank =
                context.getRank();

        Rank targetRank =
                target.getRankEnum();

        if (
                actorRank == null
                        || targetRank == null
        ) {

            return true;
        }

        if (
                targetRank.getLevel()
                        >= actorRank.getLevel()
        ) {

            context.reply(
                    "⛔ Нельзя изменять звание пользователя "
                            + "выше или равного своего ранга."
            );

            return false;
        }

        return true;
    }
}