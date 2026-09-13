package bot.command.commands;


import bot.command.Command;
import bot.command.CommandContext;
import bot.service.LinkService;
import bot.service.LinkService.CreateCodeResult;

import java.util.List;


/**
 * Генерация одноразового кода для привязки Discord: /link.
 *
 * Код вводится в Discord командой /link <код>. Одна связь VK ↔ Discord
 * может быть создана строго один раз — повторно код не выдаётся.
 * Сама логика живёт в {@link LinkService} и используется обеими
 * платформами одинаково.
 */
public class LinkCommand
        implements Command {


    private final LinkService linkService;


    public LinkCommand(
            LinkService linkService
    ) {

        this.linkService =
                linkService;
    }


    @Override
    public String getName() {

        return "link";
    }


    @Override
    public String getDescription() {

        return "Привязать Discord: получить одноразовый код";
    }


    @Override
    public List<String> getAliases() {

        return List.of(
                "discord"
        );
    }


    @Override
    public String getPermission() {

        return "link";
    }


    @Override
    public void execute(
            CommandContext context
    ) {

        if (
                !context.requirePermission(
                        "link"
                )
        ) {

            return;
        }

        CreateCodeResult result =
                linkService.createCode(
                        context.getUserId()
                );

        if (
                result.error() != null
        ) {

            context.reply(
                    result.error()
            );

            return;
        }

        context.reply(
                """
                🔗 ПРИВЯЗКА DISCORD

                Твой одноразовый код:

                %s

                В Discord напиши боту:
                /link %s

                Код действителен 15 минут.
                """.formatted(
                        result.code(),
                        result.code()
                )
        );
    }
}