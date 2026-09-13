package bot.command.commands;

import bot.command.Command;
import bot.command.CommandContext;

public class DisconnectCommand
        implements Command {


    @Override
    public String getName() {

        return "disconnect";
    }


    @Override
    public String getDescription() {

        return "Отключить бота от беседы";
    }


    @Override
    public void execute(
            CommandContext context
    ) {

        if (
                context.getPeerId()
                        <= 2_000_000_000
        ) {

            context.reply(
                    "❌ Команду нужно использовать в беседе."
            );


            return;
        }


        if (
                !context.isPatron()
        ) {

            context.reply(
                    "⛔ Только Вождь клуба может отключить бота."
            );


            return;
        }


        context.getChatRepository()
                .disconnect(
                        context.getPeerId()
                );


        context.reply(
                "✅ Бот отключён от этой беседы."
        );
    }
}