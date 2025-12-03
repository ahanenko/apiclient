package ru.napalabs.apiclient.client;

import org.springframework.http.HttpHeaders;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static ru.napalabs.apiclient.client.AuthContext.X_AUTH_USER;

/**
 * Аннотация для автоматического извлечения и установки данных аутентификации
 * (токена и логина пользователя) в {@link AuthContext}.
 * <p>
 * Применяется к методам контроллеров или сервисов, где требуется автоматически
 * подставлять данные из HTTP-заголовков текущего запроса.
 * </p>
 *
 * <p>Обрабатывается аспектом {@link CredentialsAspect}.</p>
 *
 * <pre>{@code
 * @RestController
 * public class ExampleController {
 *
 *     @AutoSetCredentials(token = true, username = true)
 *     @GetMapping("/secure/data")
 *     public ResponseEntity<?> getSecureData() {
 *         // Внутри метода authContext уже содержит токен и имя пользователя
 *         ...
 *     }
 * }
 * }</pre>
 *
 * @see CredentialsAspect
 * @see AuthContext
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoSetCredentials {
    /**
     * Указывает, нужно ли автоматически устанавливать токен авторизации.
     * <p>
     * Если значение {@code true} (по умолчанию), токен будет извлечён из заголовка,
     * указанного в {@link #tokenHeader()}.
     * </p>
     *
     * @return {@code true}, если токен должен быть установлен в {@link AuthContext}
     */
    boolean token() default true;

    /**
     * Указывает, нужно ли автоматически устанавливать логин пользователя.
     * <p>
     * Если значение {@code true}, имя будет извлечено из заголовка,
     * указанного в {@link #usernameParam()}.
     * </p>
     *
     * @return {@code true}, если логи пользователя должен быть установлен.
     */
    boolean username() default false;

    /**
     * Имя HTTP-заголовка, из которого извлекается токен авторизации.
     * <p>
     * По умолчанию используется {@code Authorization}.
     * </p>
     *
     * @return имя заголовка для токена
     */
    String tokenHeader() default HttpHeaders.AUTHORIZATION;

    /**
     * Имя HTTP-заголовка, из которого извлекается логин пользователя.
     * <p>
     * По умолчанию используется {@code X-Auth-User}.
     * </p>
     *
     * @return имя заголовка для имени пользователя
     */
    String usernameParam() default X_AUTH_USER;
}