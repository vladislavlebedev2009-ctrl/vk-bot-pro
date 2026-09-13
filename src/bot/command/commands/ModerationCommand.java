package bot.command.commands;


import bot.command.Command;
import bot.command.CommandContext;
import bot.model.User;
import bot.service.Logger;
import bot.service.UserTargetResolver;

import java.util.Locale;


/**
 * Модерация 2.0: ban/unban, warn/unwarn, mute/unmute.
 *
 * Каждое наказание фиксируется в таблице punishments
 * (с причиной и сроком), попадает в audit log и увеличивает
 * счётчики punishments_received (цель) и punishments_given (актор).
 * Снятия наказания (unban/unmute/unwarn) не меняют счётчики.
 */
public class ModerationCommand
        implements Command {


    private static final Logger logger =
            new Logger(
                    ModerationCommand.class
            );


    private final String name;

    private final String description;

    private final String permission;

    private static final long MAX_MUTE_MINUTES =
            10080L;


    public ModerationCommand(
            String name,
            String description,
            String permission
    ) {

        this.name =
                name;

        this.description =
                description;

        this.permission =
                permission;
    }


    @Override
    public String getName() {

        return name;
    }


    @Override
    public String getDescription() {

        return description;
    }


    @Override
    public String getPermission() {

        return permission;
    }


    @Override
    public void execute(
            CommandContext context
    ) {

        if (
                !context.requirePermission(
                        permission
                )
        ) {

            return;
        }


        Long targetId =
                UserTargetResolver.resolve(
                        context.getArgument(
                                0
                        )
                );


        if (
                targetId == null
        ) {

            context.reply(
                    "❌ Укажи пользователя через @ или ID.\n\n"
                            + "Пример:\n"
                            + "/"
                            + name
                            + " @id123456789"
            );

            return;
        }


        context.getUserRepository()
                .ensureUser(
                        targetId
                );


        User target =
                context.getUserRepository()
                        .findById(
                                targetId
                        );


        if (
                !context.getAccessControl()
                        .canPunish(
                                context,
                                target
                        )
        ) {

            context.getAuditRepository()
                    .log(
                            context.getUserId(),
                            name.toUpperCase(
                                    Locale.ROOT
                            ),
                            targetId,
                            argumentsText(
                                    context
                            ),
                            "DENIED"
                    );

            logger.info(
                    "Модерация отклонена: "
                            + name
                            + " актор="
                            + context.getUserId()
                            + " цель="
                            + targetId
            );

            return;
        }


        switch (
                name
        ) {

            case "ban" -> ban(
                    context,
                    targetId
            );

            case "unban" -> unban(
                    context,
                    targetId
            );

            case "warn" -> warn(
                    context,
                    targetId
            );

            case "unwarn" -> unwarn(
                    context,
                    targetId
            );

            case "mute" -> mute(
                    context,
                    targetId
            );

            case "unmute" -> unmute(
                    context,
                    targetId
            );

            default -> context.reply(
                    "❌ Неизвестная команда."
            );
        }
    }


    private void ban(
            CommandContext context,
            long targetId
    ) {

        String reason =
                reason(
                        context,
                        1
                );

        context.getUserRepository()
                .setBanned(
                        targetId,
                        true
                );

        context.getPunishmentRepository()
                .add(
                        targetId,
                        context.getUserId(),
                        "BAN",
                        reason,
                        0L,
                        0L
                );

        countPunishment(
                context,
                targetId
        );

        context.getAuditRepository()
                .log(
                        context.getUserId(),
                        "BAN",
                        targetId,
                        reason,
                        "OK"
                );

        context.reply(
                "🔨 Пользователь заблокирован."
                        + (
                        reason == null
                                || reason.isBlank()
                                ? ""
                                : "\n\n📝 Причина: " + reason
                )
        );


        User target =
                context.getUserRepository()
                        .findById(
                                targetId
                        );

        if (
                target != null
        ) {

            context.getVkApi()
                    .sendMessage(
                            targetId,
                            "🔨 Ты заблокирован в боте.\n\n"
                                    + "📝 Причина: "
                                    + (
                                    reason == null
                                            || reason.isBlank()
                                            ? "не указана"
                                            : reason
                            )
                    );
        }

        context.getVkApi()
                .removeChatUser(
                        context.getPeerId(),
                        targetId
                );
    }


    private void unban(
            CommandContext context,
            long targetId
    ) {

        String reason =
                reason(
                        context,
                        1
                );

        context.getUserRepository()
                .setBanned(
                        targetId,
                        false
                );

        int revoked =
                context.getPunishmentRepository()
                        .revokeActive(
                                targetId,
                                "BAN"
                        );

        context.getAuditRepository()
                .log(
                        context.getUserId(),
                        "UNBAN",
                        targetId,
                        reason,
                        revoked > 0
                                ? "OK"
                                : "NO_ACTIVE"
                );

        context.reply(
                "✅ Бан снят."
        );
    }


    private void warn(
            CommandContext context,
            long targetId
    ) {

        String reason =
                reason(
                        context,
                        1
                );

        context.getUserRepository()
                .addWarn(
                        targetId
                );

        context.getPunishmentRepository()
                .add(
                        targetId,
                        context.getUserId(),
                        "WARN",
                        reason,
                        0L,
                        0L
                );

        countPunishment(
                context,
                targetId
        );

        context.getAuditRepository()
                .log(
                        context.getUserId(),
                        "WARN",
                        targetId,
                        reason,
                        "OK"
                );

        context.reply(
                "⚠ Предупреждение выдано."
                        + (
                        reason == null
                                || reason.isBlank()
                                ? ""
                                : "\n\n📝 Причина: " + reason
                )
        );

        User user =
                context.getUserRepository()
                        .findById(
                                targetId
                        );

        context.getVkApi()
                .sendMessage(
                        targetId,
                        "⚠ Тебе выдано предупреждение.\n\n"
                                + "📝 Причина: "
                                + (
                                reason == null
                                        || reason.isBlank()
                                        ? "не указана"
                                        : reason
                        )
                                + "\n\n📊 Всего предупреждений: "
                                + (
                                user == null
                                        ? "?"
                                        : user.getWarns()
                        )
                );
    }


    private void unwarn(
            CommandContext context,
            long targetId
    ) {

        String reason =
                reason(
                        context,
                        1
                );

        int revoked =
                context.getPunishmentRepository()
                        .revokeLastActive(
                                targetId,
                                "WARN"
                        );

        context.getUserRepository()
                .removeWarn(
                        targetId
                );

        context.getAuditRepository()
                .log(
                        context.getUserId(),
                        "UNWARN",
                        targetId,
                        reason,
                        revoked > 0
                                ? "OK"
                                : "NO_ACTIVE"
                );

        context.reply(
                "✅ Одно предупреждение снято."
        );
    }


    private void mute(
            CommandContext context,
            long targetId
    ) {

        String durationText =
                context.getArgument(
                        1
                );


        if (
                durationText == null
        ) {

            context.reply(
                    "❌ Использование:\n"
                            + "/mute @пользователь <длительность> [причина]\n\n"
                            + "Длительность: число в минутах или 30m, 2h, 1d"
            );

            return;
        }


        Long minutes =
                parseDurationMinutes(
                        durationText
                );


        if (
                minutes == null
        ) {

            context.reply(
                    "❌ Неверный формат длительности. Примеры: "
                            + "30, 30m, 2h, 1d."
            );

            return;
        }


        if (
                minutes <= 0
                        || minutes > MAX_MUTE_MINUTES
        ) {

            context.reply(
                    "❌ Мут может длиться от 1 минуты до 7 дней."
            );

            return;
        }


        String reason =
                reason(
                        context,
                        2
                );

        long muteEnd =
                System.currentTimeMillis()
                        + minutes
                        * 60_000L;


        context.getUserRepository()
                .setMute(
                        targetId,
                        muteEnd
                );

        context.getPunishmentRepository()
                .add(
                        targetId,
                        context.getUserId(),
                        "MUTE",
                        reason,
                        minutes,
                        muteEnd
                );

        countPunishment(
                context,
                targetId
        );

        context.getAuditRepository()
                .log(
                        context.getUserId(),
                        "MUTE",
                        targetId,
                        durationText
                                + " мин"
                                + (
                                reason == null
                                        || reason.isBlank()
                                        ? ""
                                        : "; " + reason
                        ),
                        "OK"
                );

        logger.info(
                "Мут выдан: актор="
                        + context.getUserId()
                        + " цель="
                        + targetId
                        + " длительность="
                        + minutes
                        + " мин"
        );

        context.reply(
                "🔇 Пользователь получил мут на "
                        + minutes
                        + " мин."
                        + (
                        reason == null
                                || reason.isBlank()
                                ? ""
                                : "\n\n📝 Причина: " + reason
                )
        );

        context.getVkApi()
                .sendMessage(
                        targetId,
                        "🔇 Ты получил мут на "
                                + minutes
                                + " мин.\n\n"
                                + "📝 Причина: "
                                + (
                                reason == null
                                        || reason.isBlank()
                                        ? "не указана"
                                        : reason
                        )
                );
    }


    private void unmute(
            CommandContext context,
            long targetId
    ) {

        String reason =
                reason(
                        context,
                        1
                );

        /*
         * Пользователь уже не состоит в муте (включая истёкшие
         * временные муты) — сообщаем об этом без лишних изменений.
         */
        if (
                !context.getUserRepository()
                        .isMuted(
                                targetId
                        )
        ) {

            context.getAuditRepository()
                    .log(
                            context.getUserId(),
                            "UNMUTE",
                            targetId,
                            reason,
                            "NO_ACTIVE"
                    );

            context.reply(
                    "✅ Пользователь не состоит в муте."
            );

            return;
        }

        context.getUserRepository()
                .removeMute(
                        targetId
                );

        int revoked =
                context.getPunishmentRepository()
                        .revokeActive(
                                targetId,
                                "MUTE"
                        );

        context.getAuditRepository()
                .log(
                        context.getUserId(),
                        "UNMUTE",
                        targetId,
                        reason,
                        revoked > 0
                                ? "OK"
                                : "NO_ACTIVE"
                );

        logger.info(
                "Мут снят: актор="
                        + context.getUserId()
                        + " цель="
                        + targetId
                        + " revoked="
                        + revoked
        );

        context.reply(
                "🔊 Мут снят."
        );
    }


    /**
     * Счётчики полученных/выданных наказаний —
     * только для реальных наказаний (ban/warn/mute).
     */
    private void countPunishment(
            CommandContext context,
            long targetId
    ) {

        context.getUserRepository()
                .incrementPunishments(
                        targetId,
                        context.getUserId()
                );
    }


    /**
     * Парсит длительность в минутах: голое число (минуты),
     * либо суффикс m/h/d: 30m, 2h, 1d.
     *
     * @return минуты или null при неверном формате
     */
    private Long parseDurationMinutes(
            String text
    ) {

        if (
                text == null
                        || text.isBlank()
        ) {

            return null;
        }

        String value =
                text.trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        long multiplier =
                1L;

        char last =
                value.charAt(
                        value.length() - 1
                );

        if (
                last == 'm'
                        || last == 'h'
                        || last == 'd'
        ) {

            value =
                    value.substring(
                            0,
                            value.length() - 1
                    );

            multiplier =
                    switch (last) {

                        case 'h' -> 60L;

                        case 'd' -> 1440L;

                        default -> 1L;
                    };
        }

        try {

            long number =
                    Long.parseLong(
                            value
                    );

            if (
                    number < 0
                            || number
                            > Long.MAX_VALUE / multiplier
            ) {

                return null;
            }

            return number * multiplier;

        } catch (
                NumberFormatException e
        ) {

            return null;
        }
    }


    private String reason(
            CommandContext context,
            int startIndex
    ) {

        StringBuilder result =
                new StringBuilder();

        String[] arguments =
                context.getArguments();

        for (
                int i = startIndex;
                i < arguments.length;
                i++
        ) {

            if (
                    result.length() > 0
            ) {

                result.append(
                        " "
                );
            }

            result.append(
                    arguments[i]
            );
        }

        return result.toString();
    }


    private String argumentsText(
            CommandContext context
    ) {

        String text =
                context.getArgumentsText();

        return text == null
                || text.isBlank()
                ? ""
                : text;
    }
}