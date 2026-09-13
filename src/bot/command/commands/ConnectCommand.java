package bot.command.commands;

import bot.command.Command;
import bot.command.CommandContext;

public class ConnectCommand
        implements Command {


    @Override
    public String getName() {

        return "connect";
    }


    @Override
    public String getDescription() {

        return "Подключить бота к текущей беседе";
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


        boolean result =
                context.getChatRepository()
                        .connect(
                                context.getPeerId(),
                                context.getUserId()
                        );


        if (
                result
        ) {

            context.reply(
                    "✅ Беседа успешно подключена!\n\n"
                            + "🤖 Теперь бот готов работать здесь."
            );

        } else {

            context.reply(
                    "❌ Не удалось подключить беседу."
            );
        }
    }
}