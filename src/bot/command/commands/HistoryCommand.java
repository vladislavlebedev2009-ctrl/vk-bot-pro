package bot.command.commands;


import bot.command.Command;
import bot.command.CommandContext;
import bot.database.AuditRepository;
import bot.database.PunishmentRepository;
import bot.model.User;
import bot.service.UserTargetResolver;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;


/**
 * История и журнал действий: /history, /history @user, /audit [@user].
 *
 * Без аргумента — журнал последних административных действий.
 * С аргументом — наказания и действия по указанному пользователю.
 * Алиас /audit полностью совместим со старым AuditCommand.
 */
public class HistoryCommand
        implements Command {

    private static final int MAX_RECORDS =
            20;


    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat(
                    "dd.MM.yyyy HH:mm"
            );


    @Override
    public String getName() {

        return "history";
    }


    @Override
    public String getDescription() {

        return "Журнал действий: /history [@user] или /audit";
    }


    @Override
    public List<String> getAliases() {

        return List.of(
                "audit"
        );
    }


    @Override
    public String getPermission() {

        return "history";
    }


    @Override
    public void execute(
            CommandContext context
    ) {

        if (
                !context.requirePermission(
                        "history"
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
                targetId != null
        ) {

            showForUser(
                    context,
                    targetId
            );

            return;
        }

        showJournal(
                context
        );
    }


    private void showJournal(
            CommandContext context
    ) {

        List<AuditRepository.AuditEntry> entries =
                context.getAuditRepository()
                        .getRecent(
                                MAX_RECORDS
                        );

        if (
                entries.isEmpty()
        ) {

            context.reply(
                    "📋 В журнале пока нет записей."
            );

            return;
        }

        StringBuilder message =
                new StringBuilder();

        message.append(
                "📋 ПОСЛЕДНИЕ ДЕЙСТВИЯ\n\n"
        );

        appendEntries(
                message,
                context,
                entries
        );

        context.reply(
                message.toString()
        );
    }


    private void showForUser(
            CommandContext context,
            long targetId
    ) {

        context.getUserRepository()
                .ensureUser(
                        targetId
                );

        User target =
                context.getUserRepository()
                        .findById(
                                targetId
                        );

        List<PunishmentRepository.Punishment> punishments =
                context.getPunishmentRepository()
                        .getHistory(
                                targetId,
                                MAX_RECORDS
                        );

        List<AuditRepository.AuditEntry> actions =
                context.getAuditRepository()
                        .getForUser(
                                targetId,
                                MAX_RECORDS
                        );

        if (
                punishments.isEmpty()
                        && actions.isEmpty()
        ) {

            context.reply(
                    "📋 У пользователя "
                            + safeName(
                            target
                    )
                            + " наказаний и журнал действий пусты."
            );

            return;
        }

        StringBuilder message =
                new StringBuilder();

        message.append(
                "📋 ИСТОРИЯ ПОЛЬЗОВАТЕЛЯ\n\n"
        );

        message.append(
                "👤 "
        );

        message.append(
                safeName(
                        target
                )
        );

        message.append(
                " (@id"
        );

        message.append(
                targetId
        );

        message.append(
                ")\n\n"
        );

        if (
                !punishments.isEmpty()
        ) {

            message.append(
                    "⛔ НАКАЗАНИЯ:\n"
            );

            int number =
                    1;

            for (
                    PunishmentRepository.Punishment record :
                    punishments
            ) {

                message.append(
                        number
                );

                message.append(
                        ". "
                );

                message.append(
                        TIME_FORMAT.format(
                                new Date(
                                        record.createdAt()
                                )
                        )
                );

                message.append(
                        "\n   ⚡ Тип: "
                );

                message.append(
                        record.type()
                                .toUpperCase(
                                        Locale.ROOT
                                )
                );

                message.append(
                        "\n   🏷 Статус: "
                );

                message.append(
                        statusLabel(
                                record.status()
                        )
                );

                if (
                        record.durationMinutes() > 0
                ) {

                    message.append(
                            "\n   ⏱ Срок: "
                    );

                    message.append(
                            record.durationMinutes()
                    );

                    message.append(
                            " мин."
                    );
                }

                if (
                        record.reason() != null
                                && !record.reason()
                                .isBlank()
                ) {

                    message.append(
                            "\n   📝 Причина: "
                    );

                    message.append(
                            record.reason()
                    );
                }

                message.append(
                        "\n"
                );

                number++;
            }

            message.append(
                    "\n"
            );
        }

        if (
                !actions.isEmpty()
        ) {

            message.append(
                    "📜 ЖУРНАЛ ДЕЙСТВИЙ:\n"
            );

            appendEntries(
                    message,
                    context,
                    actions
            );
        }

        context.reply(
                message.toString()
        );
    }


    private void appendEntries(
            StringBuilder message,
            CommandContext context,
            List<AuditRepository.AuditEntry> entries
    ) {

        int number =
                1;

        for (
                AuditRepository.AuditEntry entry :
                entries
        ) {

            message.append(
                    number
            );

            message.append(
                    ". "
            );

            message.append(
                    TIME_FORMAT.format(
                            new Date(
                                    entry.createdAt()
                            )
                    )
            );

            message.append(
                    "\n   🎭 Актор: "
            );

            message.append(
                    nameOrId(
                            context,
                            entry.actorId()
                    )
            );

            message.append(
                    "\n   ⚡ Действие: "
            );

            message.append(
                    entry.action()
            );

            if (
                    entry.targetId() > 0
            ) {

                message.append(
                        "\n   🎯 Цель: "
                );

                message.append(
                        nameOrId(
                                context,
                                entry.targetId()
                        )
                );
            }

            if (
                    entry.detail() != null
                            && !entry.detail()
                            .isBlank()
            ) {

                message.append(
                        "\n   📝 Детали: "
                );

                message.append(
                        entry.detail()
                );
            }

            if (
                    entry.result() != null
                            && !entry.result()
                            .isBlank()
            ) {

                message.append(
                        "\n   ✔️ Результат: "
                );

                message.append(
                        entry.result()
                );
            }

            message.append(
                    "\n"
            );

            number++;
        }
    }


    private String statusLabel(
            String status
    ) {

        if (
                status == null
        ) {

            return "?";
        }

        return switch (
                status
        ) {

            case "ACTIVE" -> "✅ Активно";

            case "EXPIRED" -> "⏰ Истекло";

            case "REVOKED" -> "↩️ Снято";

            default -> status;
        };
    }


    private String nameOrId(
            CommandContext context,
            long id
    ) {

        User user =
                context.getUserRepository()
                        .findById(
                                id
                        );

        if (
                user == null
                        || user.getName() == null
                        || user.getName()
                        .isBlank()
        ) {

            return "@id"
                    + id;
        }

        return "@id"
                + id
                + " ("
                + user.getName()
                + ")";
    }


    private String safeName(
            User user
    ) {

        if (
                user == null
                        || user.getName() == null
                        || user.getName()
                        .isBlank()
        ) {

            return "(пользователь)";
        }

        return user.getName();
    }
}