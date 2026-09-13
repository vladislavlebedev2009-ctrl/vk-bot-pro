package bot.command.commands;

import bot.command.Command;
import bot.command.CommandContext;
import bot.service.RegistrationService;

import java.util.List;

public class RegisterCommand
        implements Command {


    private final RegistrationService registrationService;


    public RegisterCommand(
            RegistrationService registrationService
    ) {

        this.registrationService =
                registrationService;
    }


    @Override
    public String getName() {

        return "register";
    }


    @Override
    public String getDescription() {

        return "Пройти регистрацию";
    }


    @Override
    public List<String> getAliases() {

        return List.of(
                "start"
        );
    }


    @Override
    public boolean requiresRegistration() {

        return false;
    }


    @Override
    public void execute(
            CommandContext context
    ) {

        context.reply(
                registrationService.start(
                        context.getUserId()
                )
        );
    }
}