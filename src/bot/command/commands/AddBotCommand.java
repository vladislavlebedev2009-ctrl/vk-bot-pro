package bot.command.commands;

import bot.command.Command;
import bot.command.CommandContext;

public class AddBotCommand
        implements Command {


    @Override
    public String getName() {

        return "addbot";
    }


    @Override
    public String getDescription() {

        return "Инструкция по добавлению бота в беседу";
    }


    @Override
    public void execute(
            CommandContext context
    ) {

        if (
                !context.requirePermission(
                        "addbot"
                )
        ) {

            return;
        }


        context.reply(
                """
                🤖 ДОБАВЛЕНИЕ БОТА В БЕСЕДУ

                1️⃣ Добавьте сообщество в нужную беседу.

                2️⃣ Убедитесь, что бот имеет доступ к сообщениям.

                3️⃣ В самой беседе отправьте:

                /connect

                ✅ После этого беседа будет подключена к системе бота.
                """
        );
    }
}