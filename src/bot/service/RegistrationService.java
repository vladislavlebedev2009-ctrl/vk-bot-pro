package bot.service;

import bot.config.BotConfig;
import bot.database.AuditRepository;
import bot.database.UserRepository;
import bot.model.Rank;
import bot.model.User;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Пошаговая регистрация пользователя.
 *
 * Незавершённые сессии автоматически истекают (таймаут из конфига),
 * поэтому «зависшие» регистрации не копятся в памяти.
 */
public class RegistrationService {

    private final UserRepository userRepository;

    private final AuditRepository auditRepository;

    private final BotConfig config;

    private final ConcurrentMap<Long, Registration> registrations =
            new ConcurrentHashMap<>();


    public RegistrationService(
            UserRepository userRepository,
            AuditRepository auditRepository,
            BotConfig config
    ) {

        this.userRepository =
                userRepository;

        this.auditRepository =
                auditRepository;

        this.config =
                config;
    }


    public boolean isRegistered(
            long userId
    ) {

        if (
                userId ==
                        config.getPatronId()
        ) {

            return true;
        }


        return userRepository.isRegistered(
                userId
        );
    }


    public boolean isActive(
            long userId
    ) {

        Registration registration =
                registrations.get(
                        userId
                );

        if (
                registration == null
        ) {

            return false;
        }

        if (
                isExpired(
                        registration
                )
        ) {

            registrations.remove(
                    userId,
                    registration
            );

            return false;
        }

        return true;
    }


    public String start(
            long userId
    ) {

        if (
                isRegistered(
                        userId
                )
        ) {

            return "✅ Ты уже зарегистрирован.";
        }

        User user =
                userRepository.findById(
                        userId
                );

        if (
                user != null
                        && user.isBanned()
        ) {

            return "🚫 Ты заблокирован и не можешь зарегистрироваться.";
        }


        registrations.put(
                userId,
                new Registration(
                        System.currentTimeMillis(),
                        RegistrationState.WAITING_NAME
                )
        );


        return """
                📝 РЕГИСТРАЦИЯ

                Введи своё имя:
                """;
    }


    public String process(
            long userId,
            String text
    ) {

        if (
                text == null
                        || text.isBlank()
        ) {

            return "❌ Значение не может быть пустым.";
        }


        Registration registration =
                registrations.get(
                        userId
                );


        if (
                registration == null
        ) {

            return null;
        }

        if (
                isExpired(
                        registration
                )
        ) {

            registrations.remove(
                    userId,
                    registration
            );

            return """
                    ⏰ Время регистрации истекло.
                    Начни заново: /register
                    """;
        }


        String value =
                text.trim();


        if (
                value.length() > 50
                || value.length() < 2
        ) {

            return "❌ Длина должна быть от 2 до 50 символов.\n"
                    + "Попробуй ещё раз.";
        }


        if (
                value.chars()
                        .allMatch(
                                Character::isDigit
                        )
        ) {

            return "❌ Значение не может состоять только из цифр.\n"
                    + "Попробуй ещё раз.";
        }


        switch (
                registration.state()
        ) {

            case WAITING_NAME -> {

                userRepository.setName(
                        userId,
                        value
                );

                registrations.replace(
                        userId,
                        registration,
                        new Registration(
                                registration.startTime,
                                RegistrationState.WAITING_CALL_SIGN
                        )
                );


                return """
                        ✅ Имя сохранено.

                        📞 Теперь введи свой позывной:
                        """;
            }


            case WAITING_CALL_SIGN -> {

                if (
                        userRepository.isCallSignTaken(
                                userId,
                                value
                        )
                ) {

                    return "⚠ Позывной «"
                            + value
                            + "» уже занят.\n"
                            + "Выбери другой позывной:";
                }

                userRepository.setCallSign(
                        userId,
                        value
                );

                userRepository.setRegisteredAt(
                        userId
                );

                /*
                 * Новый участник получает стартовое звание «Кандидат»,
                 * начальную репутацию 0 и начальный баланс (по умолчанию 100).
                 */
                userRepository.setRank(
                        userId,
                        Rank.KANDIDAT.name()
                );

                registrations.remove(
                        userId
                );


                auditRepository.log(
                        userId,
                        "REGISTER",
                        0L,
                        userRepository.findById(
                                userId
                        ).getName()
                                + " / "
                                + value,
                        "OK"
                );


                return """
                        ✅ РЕГИСТРАЦИЯ ЗАВЕРШЕНА!

                        📛 Имя: %s
                        📞 Позывной: %s
                        🏅 Звание: Кандидат
                        💰 Начальный баланс: %d ₽

                        Теперь тебе доступны команды клуба.
                        Используй /help
                        """.formatted(
                        userRepository.findById(
                                userId
                        ).getName(),
                        value,
                        getUserBalance(
                                userId
                        )
                );
            }
        }


        return null;
    }


    public void cancel(
            long userId
    ) {

        registrations.remove(
                userId
        );
    }


    private int getUserBalance(
            long userId
    ) {

        User user =
                userRepository.findById(
                        userId
                );

        return user == null
                ? 0
                : user.getBalance();
    }


    private boolean isExpired(
            Registration registration
    ) {

        return System.currentTimeMillis()
                - registration.startTime
                > config.getRegistrationTimeoutMillis();
    }


    private record Registration(
            long startTime,
            RegistrationState state
    ) {
    }


    private enum RegistrationState {

        WAITING_NAME,

        WAITING_CALL_SIGN
    }
}