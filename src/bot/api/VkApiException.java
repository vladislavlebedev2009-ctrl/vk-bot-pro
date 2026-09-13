package bot.api;

/**
 * Ошибка взаимодействия с VK API: транспортная (сеть, таймаут)
 * либо ошибка самого API (error_code/error_msg).
 *
 * Никогда не содержит токен доступа.
 */
public class VkApiException extends RuntimeException {

    /**
     * VK API error_code. Для транспортных ошибок и ошибок
     * обработки ответа равно -1.
     */
    private final int errorCode;

    public VkApiException(String message) {
        this(message, -1);
    }

    public VkApiException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = -1;
    }

    public VkApiException(String message, int errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public VkApiException(String message, int errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}