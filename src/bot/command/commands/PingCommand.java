package bot.command.commands;


import bot.command.Command;
import bot.command.CommandContext;


public class PingCommand
        implements Command {


    @Override
    public String getName() {


        return "ping";
    }




    @Override
    public String getDescription() {


        return "Проверить работоспособность бота";
    }




    @Override
    public void execute(
            CommandContext context
    ) {

        long start =
                System.currentTimeMillis();

        context.reply(
                "🏓 Pong!\n"
                        + "⏱ Время ответа: "
                        + (
                        System.currentTimeMillis()
                                - start
                )
                        + " мс"
        );
    }
}