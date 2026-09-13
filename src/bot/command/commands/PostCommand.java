package bot.command.commands;

import bot.api.VkApiException;
import bot.command.Command;
import bot.command.CommandContext;
import bot.service.VkCommunityService;

import java.util.List;

/**
 * Публикация записи на стене привязанного сообщества (wall.post).
 *
 * Требуется право "post" (см. {@code RankService}). Текст поста
 * ограничивается по длине, а сам вызов — существующим cooldown
 * и rate limit в {@code CommandDispatcher}, поэтому заспамить
 * публикациями через один и тот же механизм нельзя.
 */
public class PostCommand
        implements Command {

    private static final int MAX_POST_LENGTH =
            4000;

    private static final int VK_ERROR_ACCESS_DENIED =
            15;

    private static final int VK_ERROR_NO_RIGHTS =
            214;

    private final VkCommunityService communityService;

    public PostCommand(
            VkCommunityService communityService
    ) {

        this.communityService =
                communityService;
    }

    @Override
    public String getName() {

        return "post";
    }

    @Override
    public String getDescription() {

        return "Опубликовать запись в сообществе";
    }

    @Override
    public List<String> getAliases() {

        return List.of(
                "publish"
        );
    }

    @Override
    public String getPermission() {

        return "post";
    }

    @Override
    public void execute(
            CommandContext context
    ) {

        /*
         * Права проверяются всегда, в том числе для алиаса /publish:
         * обычный пользователь и модератор без права "post"
         * получают отказ.
         */
        if (
                !context.requirePermission(
                        "post"
                )
        ) {

            return;
        }

        String text =
                context.getArgumentsText();

        if (
                text == null
                        || text.isBlank()
        ) {

            context.reply(
                    "❌ Использование:\n"
                            + "/post <текст>\n\n"
                            + "Пример:\n"
                            + "/post Всем привет!"
            );

            return;
        }

        if (
                text.length() > MAX_POST_LENGTH
        ) {

            context.reply(
                    "❌ Сообщение слишком длинное\n"
                            + "(максимум "
                            + MAX_POST_LENGTH
                            + " символов)."
            );

            return;
        }

        try {

            long postId =
                    communityService.post(
                            context.getUserId(),
                            text
                    );

            String link;

            if (
                    postId > 0
            ) {

                link =
                        "\n\n🔗 Ссылка: https://vk.com/wall-"
                                + communityService.getCommunityId()
                                + "_"
                                + postId;

            } else {

                link =
                        "";
            }

            context.reply(
                    "✅ Публикация успешно создана."
                            + link
            );

        } catch (
                VkApiException e
        ) {

            /*
             * Пользователю не показываем технический trace.
             * Подробная ошибка (код и описание VK) уже записана
             * в лог сервисом VkCommunityService.
             */
            int code =
                    e.getErrorCode();

            if (
                    code == VK_ERROR_ACCESS_DENIED
                            || code == VK_ERROR_NO_RIGHTS
            ) {

                context.reply(
                        "⛔ У бота нет необходимых прав "
                                + "для публикации в сообществе."
                );

            } else {

                context.reply(
                        "❌ Не удалось опубликовать сообщение."
                );
            }
        }
    }
}