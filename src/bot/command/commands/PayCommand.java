package bot.command.commands;


import bot.command.Command;
import bot.command.CommandContext;
import bot.database.UserRepository;
import bot.model.User;
import bot.service.UserTargetResolver;


/**
 * Перевод средств: /pay @user сумма.
 *
 * Средства защищены от переполнения и недостатка, перевод
 * выполняется одной транзакцией и фиксируется в audit log.
 */
public class PayCommand
        implements Command {


    private static final long MAX_PAY =
            100_000_000L;


    @Override
    public String getName() {


        return "pay";
    }


    @Override
    public String getDescription() {


        return "Перевести средства: /pay @user сумма";
    }


    @Override
    public void execute(
            CommandContext context
    ) {


        Long targetId =
                UserTargetResolver.resolve(
                        context.getArgument(
                                0
                        )
                );


        String amountText =
                context.getArgument(
                        1
                );


        if (
                targetId == null
                        || amountText == null
        ) {


            context.reply(
                    "❌ Использование:\n"
                            + "/pay @пользователь сумма\n\n"
                            + "Пример:\n"
                            + "/pay @id123456789 500"
            );


            return;
        }


        long amount;


        try {


            amount =
                    Long.parseLong(
                            amountText
                    );


        } catch (
                NumberFormatException e
        ) {


            context.reply(
                    "❌ Сумма должна быть числом."
            );


            return;
        }


        if (
                amount <= 0
                        || amount > MAX_PAY
        ) {


            context.reply(
                    "❌ Сумма должна быть от 1 до "
                            + MAX_PAY
                            + "."
            );


            return;
        }


        if (
                targetId ==
                        context.getUserId()
        ) {


            context.reply(
                    "❌ Нельзя перевести средства самому себе."
            );


            return;
        }


        context.getUserRepository()
                .ensureUser(
                        targetId
                );


        UserRepository.TransferResult result =
                context.getUserRepository()
                        .transfer(
                                context.getUserId(),
                                targetId,
                                amount
                        );


        String message =
                switch (
                        result
                ) {

                    case OK -> "💰 Перевод выполнен.\n\n"
                            + "➡️ Сумма: "
                            + bot.util.Text.number(
                            amount
                    )
                            + " ₽\n"
                            + "👤 Получатель: "
                            + safeName(
                            targetName(
                                    context,
                                    targetId
                            )
                    );

                    case INSUFFICIENT_FUNDS ->
                            "❌ Недостаточно средств на балансе.";

                    case OVERFLOW ->
                            "❌ Перевод не выполнен: "
                                    + "переполнение баланса получателя.";

                    case SELF_TRANSFER ->
                            "❌ Нельзя перевести средства самому себе.";

                    case INVALID_AMOUNT ->
                            "❌ Некорректная сумма перевода.";

                    default ->
                            "❌ Получатель не найден.";
                };


        if (
                result == UserRepository.TransferResult.OK
        ) {


            context.getAuditRepository()
                    .log(
                            context.getUserId(),
                            "PAY",
                            targetId,
                            String.valueOf(
                                    amount
                            ),
                            "OK"
                    );


            context.getUserRepository()
                    .findById(
                            context.getUserId()
                    );


            User target =
                    context.getUserRepository()
                            .findById(
                                    targetId
                            );


            context.getVkApi()
                    .sendMessage(
                            targetId,
                            "💰 Тебе переведено "
                                    + bot.util.Text.number(
                                    amount
                            )
                                    + " ₽ от @id"
                                    + context.getUserId()
                                    + ".\n\n"
                                    + "💼 Твой баланс: "
                                    + (
                                    target == null
                                            ? "?"
                                            : bot.util.Text.number(
                                            target.getBalance()
                                    )
                            )
                                    + " ₽"
                    );
        }


        context.reply(
                message
        );
    }


    private String targetName(
            CommandContext context,
            long targetId
    ) {


        User target =
                context.getUserRepository()
                        .findById(
                                targetId
                        );


        if (
                target == null
                        || target.getName() == null
                        || target.getName()
                        .isBlank()
        ) {


            return String.valueOf(
                    targetId
            );
        }


        return target.getName();
    }


    private String safeName(
            String value
    ) {


        return value == null
                || value.isBlank()
                ? "Не указано"
                : value;
    }
}