package ru.napalabs.apiclient.client;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Контекст аутентификации, используемый для хранения данных о текущем пользователе
 * в рамках одного HTTP-запроса.
 * <p>
 * Класс управляется Spring-контейнером как {@link org.springframework.stereotype.Service} с областью видимости {@link org.springframework.web.context.annotation.RequestScope}.
 * Это означает, что для каждого входящего запроса создаётся отдельный экземпляр {@code AuthContext}.
 * </p>
 * <p>
 * Содержит токен авторизации и имя пользователя, полученные, например, из заголовков HTTP-запроса.
 * </p>
 *
 * <p><b>Пример использования:</b></p>
 * <pre>{@code
 * @Autowired
 * private AuthContext authContext;
 *
 * public void someMethod() {
 *     String token = authContext.getToken();
 *     String username = authContext.getUsername();
 * }
 * }</pre>
 *
 * @see org.springframework.stereotype.Service
 * @see org.springframework.web.context.annotation.RequestScope
 * @see jakarta.validation.constraints.NotNull
 */
@Schema(
        name = "AuthContext",
        description = "Контекст аутентификации, содержащий токен и имя текущего пользователя."
)
@Service
@RequestScope
@Validated
@Setter
@Getter
public class AuthContext {
    /**
     * Токен авторизации текущего пользователя.
     * <p>
     * Должен быть непустым и соответствовать формату, принятому в системе (например, JWT).
     * </p>
     */
    @Schema(
            description = "Токен авторизации текущего пользователя (например, JWT).",
            example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
    )
    @NotNull
    private String token;

    /**
     * Имя (логин) текущего пользователя, прошедшего аутентификацию.
     * <p>
     * Обычно используется для логирования и передачи информации в другие сервисы.
     * </p>
     */

    @Schema(
            description = "Имя (логин) текущего пользователя.",
            example = "ivan.petrov"
    )
    @NotNull
    private String username;

    /**
     * Очищает сохранённый токен авторизации.
     * <p>Устанавливает значение {@code token = null}.</p>
     */
    public void clearToken() {
        this.token = null;
    }

    /**
     * Очищает сохранённое имя пользователя.
     * <p>Устанавливает значение {@code username = null}.</p>
     */
    public void clearUsername() { this.username = null; }

    /**
     * Полностью очищает контекст аутентификации:
     * обнуляет токен и имя пользователя.
     */
    public void clear() {
        this.token = null;
        this.username = null;
    }
}