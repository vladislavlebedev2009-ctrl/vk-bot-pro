package bot.service;

import bot.api.VkApi;
import bot.api.VkApiException;
import bot.config.BotConfig;
import org.json.JSONObject;

/**
 * Сервис операций с привязанным сообществом VK.
 *
 * Основной идентификатор сообщества — VK_GROUP_ID из конфигурации.
 * Публикации идут через официальный VK API (wall.post) в обёртке
 * {@link VkApi#postToCommunity(String)}. Никакой логики Long Poll
 * здесь нет. Токен доступа нигде не логируется.
 */
public class VkCommunityService {

    private static final Logger logger =
            new Logger(
                    VkCommunityService.class
            );

    private final VkApi vkApi;

    private final BotConfig config;

    public VkCommunityService(
            VkApi vkApi,
            BotConfig config
    ) {

        this.vkApi =
                vkApi;

        this.config =
                config;
    }

    public long getCommunityId() {

        return config.getGroupId();
    }

    public JSONObject getCommunityInfo() {

        return vkApi.getGroupById();
    }

    /**
     * Публикация текстовой записи на стене привязанного сообщества.
     *
     * @param actorId VK ID пользователя, инициировавшего публикацию
     * @param text    текст записи (не пустой, проверяется в API)
     * @return идентификатор созданной записи (post_id)
     * @throws VkApiException при ошибке VK API/сети (токен отсутствует)
     */
    public long post(
            long actorId,
            String text
    ) {

        long communityId =
                config.getGroupId();

        logger.info(
                "Попытка публикации в сообществе: "
                        + "community_id="
                        + communityId
                        + ", actor="
                        + actorId
                        + ", text_len="
                        + (
                        text == null
                                ? 0
                                : text.length()
                )
        );

        try {

            long postId =
                    vkApi.postToCommunity(
                            text
                    );

            logger.info(
                    "Публикация успешна: "
                            + "community_id="
                            + communityId
                            + ", actor="
                            + actorId
                            + ", post_id="
                            + postId
            );

            return postId;

        } catch (
                VkApiException e
        ) {

            logger.error(
                    "Ошибка публикации: "
                            + "community_id="
                            + communityId
                            + ", actor="
                            + actorId
                            + ", action=wall.post"
                            + ", vk_error_code="
                            + e.getErrorCode()
                            + ", error="
                            + e.getMessage()
            );

            throw e;
        }
    }
}