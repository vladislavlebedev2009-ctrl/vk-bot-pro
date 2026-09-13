package bot.api;


import bot.config.BotConfig;
import bot.service.Logger;
import org.json.JSONArray;
import org.json.JSONObject;


import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;


public class VkApi {


    private static final Logger LOGGER =
            new Logger(
                    VkApi.class
            );

    /**
     * Максимум попыток на один вызов (для временных ошибок).
     * Никаких бесконечных retry.
     */
    private static final int MAX_ATTEMPTS = 3;

    /**
     * VK API error_code, после которых безопасно повторить запрос.
     * 1  — неизвестная ошибка; 6 — слишком много запросов в секунду;
     * 9  — flood control; 10 — внутренняя ошибка; 29 — rate limit.
     * Captcha (14) и ошибки авторизации (5) не ретраим.
     */
    private static final Set<Integer> RETRYABLE_ERROR_CODES =
            Set.of(1, 6, 9, 10, 29);

    /**
     * Счётчик random_id. Начальное значение зависит от времени,
     * поэтому коллизии между перезапусками практически исключены,
     * а внутри процесса значение монотонно растёт.
     */
    private final AtomicLong randomIdCounter =
            new AtomicLong(
                    (System.currentTimeMillis() << 16)
                            ^ (System.nanoTime() & 0xFFFF)
            );


    private final BotConfig config;


    private final HttpClient httpClient;




    public VkApi(
            BotConfig config
    ) {


        this.config =
                config;


        this.httpClient =
                HttpClient.newBuilder()
                        .version(
                                HttpClient.Version.HTTP_1_1
                        )
                        .connectTimeout(
                                Duration.ofSeconds(
                                        config.getHttpTimeoutSeconds()
                                )
                        )
                        .build();
    }


    public BotConfig getConfig() {


        return config;
    }


    /*
     * Универсальный вызов VK API.
     *
     * При транспортной ошибке или ошибке VK API после исчерпания
     * допустимых попыток выбрасывается {@link VkApiException}.
     * Успешный пустой ответ невозможен: ошибка всегда отличима.
     */
    public JSONObject call(
            String method,
            Map<String, String> parameters
    ) {

        int attempt = 0;

        while (true) {

            attempt++;

            try {

                JSONObject json =
                        doCall(
                                method,
                                parameters
                        );

                if (
                        json.has(
                                "error"
                        )
                ) {

                    JSONObject error =
                            json.getJSONObject(
                                    "error"
                            );

                    int code =
                            error.optInt(
                                    "error_code",
                                    -1
                            );

                    String message =
                            error.optString(
                                    "error_msg",
                                    ""
                            );

                    LOGGER.warning(
                            "VK API error ["
                                    + method
                                    + "]: code="
                                    + code
                                    + ", msg="
                                    + message
                    );

                    if (
                            attempt < MAX_ATTEMPTS
                                    && RETRYABLE_ERROR_CODES.contains(
                                    code
                            )
                    ) {

                        LOGGER.info(
                                "Повторная попытка VK API ["
                                        + method
                                        + "] ("
                                        + attempt
                                        + "/"
                                        + MAX_ATTEMPTS
                                        + ")"
                        );

                        sleepBackoff(
                                attempt
                        );

                        continue;
                    }

                    throw new VkApiException(
                            "VK API error ["
                                    + method
                                    + "]: code="
                                    + code
                                    + ", msg="
                                    + message,
                            code
                    );
                }


                return json;

            } catch (
                    IOException
                    | InterruptedException e
            ) {

                if (
                        e instanceof InterruptedException
                ) {

                    Thread.currentThread()
                            .interrupt();
                }

                if (
                        attempt < MAX_ATTEMPTS
                ) {

                    LOGGER.warning(
                            "Транспортная ошибка VK API ["
                                    + method
                                    + "], попытка "
                                    + attempt
                                    + ": "
                                    + e.getClass()
                                    .getSimpleName()
                    );

                    sleepBackoff(
                            attempt
                    );

                    continue;
                }

                throw new VkApiException(
                        "Ошибка сети VK API ["
                                + method
                                + "]: "
                                + e.getClass()
                                .getSimpleName(),
                        e
                );

            } catch (
                    VkApiException e
            ) {

                throw e;

            } catch (
                    RuntimeException e
            ) {

                throw new VkApiException(
                        "Ошибка обработки ответа VK API ["
                                + method
                                + "]",
                        e
                );
            }
        }
    }


    /*
     * Упрощённый вызов API
     */
    public JSONObject call(
            String method
    ) {


        return call(
                method,
                Map.of()
        );
    }


    /*
     * Вызов VK API БЕЗ автоматических повторов (одна попытка).
     *
     * Используется для НЕидемпотентных методов (например, wall.post),
     * где повторная отправка одного и того же запроса может создать
     * дубликат записи. Обработка ошибок такая же, как в {@link #call}:
     * транспортные ошибки и VK API error логируются и пробрасываются
     * как {@link VkApiException} (без токена).
     */
    public JSONObject callNoRetry(
            String method,
            Map<String, String> parameters
    ) {

        try {

            JSONObject json =
                    doCall(
                            method,
                            parameters
                    );

            if (
                    json.has(
                            "error"
                    )
            ) {

                JSONObject error =
                        json.getJSONObject(
                                "error"
                        );

                int code =
                        error.optInt(
                                "error_code",
                                -1
                        );

                String message =
                        error.optString(
                                "error_msg",
                                ""
                        );

                LOGGER.warning(
                        "VK API error ["
                                + method
                                + "]: code="
                                + code
                                + ", msg="
                                + message
                );

                throw new VkApiException(
                        "VK API error ["
                                + method
                                + "]: code="
                                + code
                                + ", msg="
                                + message,
                        code
                );
            }

            return json;

        } catch (
                IOException
                | InterruptedException e
        ) {

            if (
                    e instanceof InterruptedException
            ) {

                Thread.currentThread()
                        .interrupt();
            }

            throw new VkApiException(
                    "Ошибка сети VK API ["
                            + method
                            + "]: "
                            + e.getClass()
                            .getSimpleName(),
                    e
            );

        } catch (
                VkApiException e
        ) {

            throw e;

        } catch (
                RuntimeException e
        ) {

            throw new VkApiException(
                    "Ошибка обработки ответа VK API ["
                            + method
                            + "]",
                    e
            );
        }
    }


    /*
     * Отправка сообщения
     */
    public boolean sendMessage(
            long peerId,
            String message
    ) {


        if (
                message == null
                        || message.isBlank()
        ) {


            return false;
        }


        Map<String, String> params =
                new LinkedHashMap<>();


        params.put(
                "peer_id",
                String.valueOf(
                        peerId
                )
        );


        params.put(
                "message",
                message
        );


        params.put(
                "random_id",
                String.valueOf(
                        nextRandomId()
                )
        );


        try {

            JSONObject response =
                    call(
                            "messages.send",
                            params
                    );


            return response.has(
                    "response"
            );

        } catch (
                VkApiException e
        ) {

            LOGGER.warning(
                    "Не удалось отправить сообщение в "
                            + peerId
            );


            return false;
        }
    }


    /*
     * Отправка сообщения с клавиатурой
     */
    public boolean sendMessage(
            long peerId,
            String message,
            String keyboard
    ) {


        if (
                message == null
                        || message.isBlank()
        ) {


            return false;
        }


        Map<String, String> params =
                new LinkedHashMap<>();


        params.put(
                "peer_id",
                String.valueOf(
                        peerId
                )
        );


        params.put(
                "message",
                message
        );


        params.put(
                "random_id",
                String.valueOf(
                        nextRandomId()
                )
        );


        params.put(
                "keyboard",
                keyboard
        );


        try {

            JSONObject response =
                    call(
                            "messages.send",
                            params
                    );


            return response.has(
                    "response"
            );

        } catch (
                VkApiException e
        ) {

            LOGGER.warning(
                    "Не удалось отправить сообщение с клавиатурой в "
                            + peerId
            );


            return false;
        }
    }


    /*
     * Получение Long Poll сервера
     */
    public JSONObject getLongPollServer() {


        Map<String, String> params =
                new LinkedHashMap<>();


        params.put(
                "group_id",
                String.valueOf(
                        config.getGroupId()
                )
        );


        return call(
                "groups.getLongPollServer",
                params
        );
    }


    /*
     * Получение информации о сообществе
     */
    public JSONObject getGroupById() {


        Map<String, String> params =
                new LinkedHashMap<>();


        params.put(
                "group_id",
                String.valueOf(
                        config.getGroupId()
                )
        );


        return call(
                "groups.getById",
                params
        );
    }


    /*
     * Публикация текстовой записи на стене сообщества (wall.post).
     *
     * Отдельный метод от messages.send: тут создаётся публикация
     * на стене сообщества, а не сообщение пользователю/беседе.
     * owner_id указывается отрицательным (сообщество); публикация
     * выполняется от имени сообщества (from_group=1).
     *
     * Метод НЕидемпотентен (повторный вызов создаёт дубликат),
     * поэтому выполняется БЕЗ автоматических повторов
     * (через {@link #callNoRetry}). Токен в исключениях и логах
     * отсутствует.
     *
     * @return идентификатор созданной записи (post_id)
     * @throws VkApiException при ошибке сети или VK API
     */
    public long postToCommunity(
            String message
    ) {

        if (
                message == null
                        || message.isBlank()
        ) {

            throw new VkApiException(
                    "Текст публикации пуст"
            );
        }

        Map<String, String> params =
                new LinkedHashMap<>();

        params.put(
                "owner_id",
                "-"
                        + config.getGroupId()
        );

        params.put(
                "from_group",
                "1"
        );

        params.put(
                "message",
                message
        );

        JSONObject response =
                callNoRetry(
                        "wall.post",
                        params
                );

        JSONObject data =
                response.optJSONObject(
                        "response"
                );

        if (
                data == null
        ) {

            throw new VkApiException(
                    "wall.post вернул пустой ответ"
            );
        }

        return data.optLong(
                "post_id",
                -1L
        );
    }


    /*
     * Проверка токена
     */
    public boolean checkToken() {


        try {

            JSONObject response =
                    getGroupById();


            return response.has(
                    "response"
            );

        } catch (
                VkApiException e
        ) {

            LOGGER.warning(
                    "Проверка токена завершилась ошибкой"
            );


            return false;
        }
    }


    /*
     * Удаление сообщения замьюченного пользователя.
     *
     * delete_for_all=1 убирает сообщение у всех участников беседы.
     * При отсутствии прав (или иной ошибке VK) не падает:
     * ошибка логируется и возвращается false.
     */
    public boolean deleteMessage(
            int messageId
    ) {

        if (
                messageId <= 0
        ) {

            return false;
        }

        Map<String, String> params =
                new LinkedHashMap<>();

        params.put(
                "message_ids",
                String.valueOf(
                        messageId
                )
        );

        params.put(
                "delete_for_all",
                "1"
        );

        try {

            JSONObject response =
                    call(
                            "messages.delete",
                            params
                    );

            return response.has(
                    "response"
            );

        } catch (
                VkApiException e
        ) {

            LOGGER.warning(
                    "Не удалось удалить сообщение "
                            + messageId
                            + " замьюченного пользователя"
            );

            return false;
        }
    }


    /*
     * Исключение пользователя из беседы
     */
    public boolean removeChatUser(
            int peerId,
            long userId
    ) {

        if (
                peerId <= 2_000_000_000
        ) {


            LOGGER.warning(
                    "Нельзя исключить пользователя: сообщение "
                            + "было не из беседы. Peer ID = "
                            + peerId
            );


            return false;
        }


        int chatId =
                peerId
                        - 2_000_000_000;


        Map<String, String> params =
                new LinkedHashMap<>();


        params.put(
                "chat_id",
                String.valueOf(
                        chatId
                )
        );


        params.put(
                "member_id",
                String.valueOf(
                        userId
                )
        );


        try {

            JSONObject response =
                    call(
                            "messages.removeChatUser",
                            params
                    );


            return response.has(
                    "response"
            );

        } catch (
                VkApiException e
        ) {

            LOGGER.warning(
                    "Не удалось исключить пользователя "
                            + userId
                            + " из беседы "
                            + peerId
            );


            return false;
        }
    }


    /*
     * Добавление пользователя в беседу
     */
    public boolean addChatUser(
            int chatId,
            long userId
    ) {


        Map<String, String> params =
                new LinkedHashMap<>();


        params.put(
                "chat_id",
                String.valueOf(
                        chatId
                )
        );


        params.put(
                "user_id",
                String.valueOf(
                        userId
                )
        );


        try {

            JSONObject response =
                    call(
                            "messages.addChatUser",
                            params
                    );


            return response.has(
                    "response"
            );

        } catch (
                VkApiException e
        ) {

            LOGGER.warning(
                    "Не удалось добавить пользователя "
                            + userId
            );


            return false;
        }
    }


    /*
     * Получение участников беседы
     */
    public JSONArray getChatMembers(
            int chatId
    ) {


        Map<String, String> params =
                new LinkedHashMap<>();


        params.put(
                "chat_id",
                String.valueOf(
                        chatId
                )
        );


        try {

            JSONObject response =
                    call(
                            "messages.getConversationMembers",
                            params
                    );

            if (
                    !response.has(
                            "response"
                    )
            ) {

                return new JSONArray();
            }


            JSONArray items =
                    response.getJSONObject(
                            "response"
                    ).optJSONArray(
                            "items"
                    );


            return items != null
                    ? items
                    : new JSONArray();

        } catch (
                VkApiException e
        ) {

            LOGGER.warning(
                    "Не удалось получить участников беседы "
                            + chatId
            );


            return new JSONArray();
        }
    }


    /*
     * Формирование и выполнение HTTP-запроса.
     */
    private JSONObject doCall(
            String method,
            Map<String, String> parameters
    )
            throws IOException,
            InterruptedException {


        Map<String, String> params =
                new LinkedHashMap<>(
                        parameters
                );

        params.put(
                "access_token",
                config.getToken()
        );

        params.put(
                "v",
                config.getApiVersion()
        );


        StringBuilder body =
                new StringBuilder();


        for (
                Map.Entry<String, String> entry :
                params.entrySet()
        ) {


            if (
                    body.length() > 0
            ) {


                body.append(
                        "&"
                );
            }


            body.append(
                    URLEncoder.encode(
                            entry.getKey(),
                            StandardCharsets.UTF_8
                    )
            );


            body.append(
                    "="
            );


            body.append(
                    URLEncoder.encode(
                            entry.getValue(),
                            StandardCharsets.UTF_8
                    )
            );
        }


        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        "https://api.vk.com/method/"
                                                + method
                                )
                        )
                        .timeout(
                                Duration.ofSeconds(
                                        config.getHttpTimeoutSeconds()
                                )
                        )
                        .header(
                                "Content-Type",
                                "application/x-www-form-urlencoded"
                        )
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        body.toString()
                                )
                        )
                        .build();


        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );


        if (
                response.statusCode() >= 500
        ) {


            throw new IOException(
                    "HTTP " + response.statusCode()
            );
        }


        return new JSONObject(
                response.body()
        );
    }


    private long nextRandomId() {

        return randomIdCounter.incrementAndGet();
    }


    private void sleepBackoff(
            int attempt
    ) {

        try {

            Thread.sleep(
                    1000L * attempt
            );

        } catch (
                InterruptedException e
        ) {

            Thread.currentThread()
                    .interrupt();
        }
    }
}