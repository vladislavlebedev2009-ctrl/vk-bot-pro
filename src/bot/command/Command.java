package bot.command;


import java.util.List;


public interface Command {


    String getName();


    String getDescription();


    default List<String> getAliases() {


        return List.of();
    }


    /**
     * Право (permission), необходимое для выполнения команды.
     * Если null — команда доступна всем зарегистрированным.
     */
    default String getPermission() {


        return null;
    }


    /**
     * Требуется ли команде зарегистрированный пользователь.
     * Публичные команды (/register, /start, /help) возвращают false —
     * они работают и до регистрации. Все остальные по умолчанию
     * проходят через registration gate в {@link CommandDispatcher}.
     */
    default boolean requiresRegistration() {


        return true;
    }


    void execute(
            CommandContext context
    );
}