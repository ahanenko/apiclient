package ru.napalabs.apiclient.client;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Аннотация для автоматического извлечения и установки данных аутентификации
 * (токена и имени пользователя) в {@link ru.napalabs.apiclient.client.AuthContext}.
 * <p>
 * Применяется к методам контроллеров или сервисов, где требуется автоматически
 * подставлять данные из HTTP-заголовков текущего запроса.
 * </p>
 *
 * <p>Обрабатывается аспектом {@link ru.napalabs.apiclient.client.CredentialsAspect}.</p>
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
 * @see ru.napalabs.apiclient.client.CredentialsAspect
 * @see ru.napalabs.apiclient.client.AuthContext
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
     * Указывает, нужно ли автоматически устанавливать имя пользователя.
     * <p>
     * Если значение {@code true}, имя будет извлечено из заголовка,
     * указанного в {@link #usernameParam()}.
     * </p>
     *
     * @return {@code true}, если имя пользователя должно быть установлено
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
    String tokenHeader() default "Authorization";

    /**
     * Имя HTTP-заголовка, из которого извлекается имя пользователя.
     * <p>
     * По умолчанию используется {@code X-Auth-Username}.
     * </p>
     *
     * @return имя заголовка для имени пользователя
     */
    String usernameParam() default "X-Auth-Username";
}