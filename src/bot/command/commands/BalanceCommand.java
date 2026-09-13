package bot.command.commands;


import bot.command.Command;
import bot.command.CommandContext;
import bot.model.User;


public class BalanceCommand
        implements Command {


    @Override
    public String getName() {

        return "balance";
    }


    @Override
    public String getDescription() {

        return "Показать свой баланс";
    }


    @Override
    public void execute(
            CommandContext context
    ) {

        User user =
                context.getUser();

        if (
                user == null
        ) {

            context.reply(
                    "❌ Пользователь не найден."
            );

            return;
        }

        context.reply(
                "💰 Твой баланс: "
                        + bot.util.Text.number(
                        user.getBalance()
                )
                        + " ₽\n"
                        + "⭐ Репутация: "
                        + user.getRep()
        );
    }
}