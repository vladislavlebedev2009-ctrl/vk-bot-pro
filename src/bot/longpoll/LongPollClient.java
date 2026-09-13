package bot.longpoll;


import bot.api.VkApi;
import bot.command.CommandDispatcher;
import bot.config.BotConfig;
import bot.service.Logger;
import bot.service.MessageProcessingService;
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


/**
 * Транспортный слой Long Poll API.
 *
 * Занимается подключением, получением сервера, опросом обновлений
 * и переподключением. Бизнес-правила обработки сообщений вынесены
 * в {@link MessageProcessingService}.
 */
public class LongPollClient {


    private static final Logger logger =
            new Logger(LongPollClient.class);


    private final BotConfig config;
    private final CommandDispatcher dispatcher;
    private final MessageProcessingService messageProcessing;
    private final VkApi vkApi;
    private final HttpClient httpClient;


    private volatile boolean running;


    private String server;
    private String key;
    private String ts;


    public LongPollClient(
            BotConfig config,
            CommandDispatcher dispatcher,
            MessageProcessingService messageProcessing
    ) {
        this.config = config;
        this.dispatcher = dispatcher;
        this.messageProcessing = messageProcessing;
        this.vkApi = dispatcher.getVkApi();


        this.httpClient =
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(
                                Duration.ofSeconds(
                                        config.getHttpTimeoutSeconds()
                                )
                        )
                        .build();
    }


    public void start() {


        if (running) {
            logger.warning(
                    "Long Poll уже запущен"
            );


            return;
        }


        running = true;


        logger.info(
                "Long Poll запущен"
        );


        while (running) {


            try {


                if (
                        server == null
                                || key == null
                                || ts == null
                ) {
                    connect();
                }


                JSONObject response =
                        waitForUpdates();


                processResponse(
                        response
                );


            } catch (
                    Exception e
            ) {


                logger.error(
                        "Ошибка Long Poll",
                        e
                );


                resetConnection();


                sleep(
                        config.getReconnectDelayMillis()
                );
            }
        }


        logger.info(
                "Long Poll остановлен"
        );
    }


    public void run() {
        start();
    }


    public void stop() {


        running = false;


        logger.info(
                "Остановка Long Poll..."
        );
    }


    private void connect() {


        logger.info(
                "Получение Long Poll сервера..."
        );


        JSONObject response =
                vkApi.getLongPollServer();


        logger.info(
                "Ответ groups.getLongPollServer получен"
        );


        if (
                !response.has(
                        "response"
                )
        ) {


            throw new RuntimeException(
                    "VK не вернул Long Poll Server"
            );
        }


        JSONObject data =
                response.getJSONObject(
                        "response"
                );


        server =
                data.optString(
                        "server",
                        null
                );


        key =
                data.optString(
                        "key",
                        null
                );


        ts =
                data.optString(
                        "ts",
                        null
                );


        if (
                server == null
                        || key == null
                        || ts == null
        ) {


            throw new RuntimeException(
                    "Некорректный ответ Long Poll"
            );
        }


        logger.info(
                "Long Poll подключён"
        );
    }


    private JSONObject waitForUpdates()
            throws IOException,
            InterruptedException {


        String url =
                server
                        + "?act=a_check"
                        + "&key="
                        + encode(
                        key
                )
                        + "&wait="
                        + config.getWaitTime()
                        + "&ts="
                        + encode(
                        ts
                )
                        + "&version=3";


        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        url
                                )
                        )
                        .timeout(
                                Duration.ofSeconds(
                                        (long) config.getWaitTime() + 20
                                )
                        )
                        .GET()
                        .build();


        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );


        if (
                response.statusCode()
                        != 200
        ) {


            throw new IOException(
                    "Long Poll HTTP ошибка: "
                            + response.statusCode()
            );
        }


        return new JSONObject(
                response.body()
        );
    }


    private void processResponse(
            JSONObject response
    ) {


        if (
                response.has(
                        "failed"
                )
        ) {


            int failed =
                    response.getInt(
                            "failed"
                    );

            if (
                    failed == 1
            ) {


                /*
                 * Ошибка 1: истёк ts.
                 * Обновляем ts из ответа и продолжаем опрос.
                 */
                logger.info(
                        "Long Poll failed=1: обновляем ts"
                );

                String newTs =
                        response.optString(
                                "ts",
                                null
                        );

                if (
                        newTs != null
                ) {

                    ts =
                            newTs;
                }


                return;
            }


            handleFailed(
                    failed
            );


            return;
        }


        String newTs =
                response.optString(
                        "ts",
                        null
                );


        if (
                newTs != null
        ) {


            ts =
                    newTs;
        }


        JSONArray updates =
                response.optJSONArray(
                        "updates"
                );


        if (
                updates == null
        ) {


            logger.info(
                    "Обновлений нет"
            );


            return;
        }


        logger.info(
                "Получено обновлений: "
                        + updates.length()
        );


        for (
                int i = 0;
                i < updates.length();
                i++
        ) {


            JSONObject update =
                    updates.optJSONObject(
                            i
                    );


            if (
                    update == null
            ) {


                continue;
            }


            try {

                processUpdate(
                        update
                );

            } catch (
                    Exception e
            ) {

                logger.error(
                        "Ошибка обработки обновления #"
                                + i,
                        e
                );
            }
        }
    }


    private void handleFailed(
            int failed
    ) {


        logger.warning(
                "Long Poll failed: "
                        + failed
        );


        switch (
                failed
        ) {


            case 2 -> {


                logger.info(
                        "Ошибка 2: требуется новый key"
                );


                resetConnection();
            }


            case 3, 4 -> {


                logger.warning(
                        "Требуется полное переподключение"
                );


                resetConnection();
            }


            default -> {


                logger.warning(
                        "Неизвестная ошибка Long Poll"
                );


                resetConnection();
            }
        }
    }


    private void processUpdate(
            JSONObject update
    ) {

        String type =
                update.optString(
                        "type",
                        ""
                );

        if (
                !"message_new".equals(
                        type
                )
        ) {


            return;
        }

        JSONObject messageObject =
                update.optJSONObject(
                        "object"
                );

        if (
                messageObject == null
        ) {


            logger.warning(
                    "У message_new отсутствует object"
            );


            return;
        }

        /*
         * В новой структуре VK объект сообщения лежит
         * внутри object.message, со старой — object сам
         * является сообщением.
         */
        JSONObject message =
                messageObject.optJSONObject(
                        "message"
                );

        if (
                message == null
        ) {

            message =
                    messageObject;
        }

        long fromId =
                message.optLong(
                        "from_id",
                        0
                );

        int peerId =
                message.optInt(
                        "peer_id",
                        0
                );

        int messageId =
                message.optInt(
                        "id",
                        0
                );

        String text =
                message.optString(
                        "text",
                        ""
                );

        messageProcessing.process(
                fromId,
                peerId,
                messageId,
                text
        );
    }


    private void resetConnection() {


        server = null;
        key = null;
        ts = null;


        logger.info(
                "Соединение Long Poll сброшено"
        );
    }


    private String encode(
            String value
    ) {


        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }


    private void sleep(
            long millis
    ) {


        try {


            Thread.sleep(
                    millis
            );


        } catch (
                InterruptedException e
        ) {


            Thread.currentThread()
                    .interrupt();
        }
    }
}