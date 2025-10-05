package ru.napalabs.apiclient.client;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Аспект, обеспечивающий автоматическое заполнение контекста авторизации {@link AuthContext}
 * на основе HTTP-заголовков входящего запроса.
 * <p>
 * Перехватывает вызовы методов, помеченных аннотацией {@link AutoSetCredentials},
 * извлекает токен и/или имя пользователя из заголовков запроса
 * и записывает их в текущий {@link AuthContext}.
 * </p>
 *
 * <p>Пример использования:</p>
 *
 * <pre>{@code
 * @RestController
 * public class ExampleController {
 *
 *     private final AuthContext authContext;
 *
 *     @AutoSetCredentials(token = true, username = true)
 *     @GetMapping("/profile")
 *     public ResponseEntity<?> getProfile() {
 *         // authContext.getToken() и authContext.getUsername() уже заполнены
 *         ...
 *     }
 * }
 * }</pre>
 *
 * @see AutoSetCredentials
 * @see AuthContext
 */
@Aspect
@Component
public class CredentialsAspect {
    private final AuthContext authContext;

    /**
     * Создаёт экземпляр аспекта, который будет использовать указанный {@link AuthContext}
     * для установки данных аутентификации.
     *
     * @param authContext контекст авторизации, в который будут подставляться токен и имя пользователя
     */
    public CredentialsAspect(AuthContext authContext) {
        this.authContext = authContext;
    }

    /**
     * Перехватывает методы, аннотированные {@link AutoSetCredentials},
     * извлекает из запроса заголовки с токеном и именем пользователя,
     * и записывает их в {@link AuthContext}.
     *
     * @param joinPoint точка выполнения перехватываемого метода
     * @param autoSetCredentials аннотация с настройками обработки заголовков
     * @return результат выполнения исходного метода
     * @throws Throwable если при выполнении исходного метода произошла ошибка
     */
    @Around("@annotation(autoSetCredentials)")
    public Object autoSetCredentials(ProceedingJoinPoint joinPoint, AutoSetCredentials autoSetCredentials) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();

        if (autoSetCredentials.token()) {
            // Получаем токен из заголовков запроса
            String jwtToken = request.getHeader(autoSetCredentials.tokenHeader());
            if (jwtToken != null && !jwtToken.isEmpty()) {
                authContext.setToken(jwtToken.replace("Bearer ", ""));
            }
        }

        if (autoSetCredentials.username()) {
            // Получаем username из заголовков запроса
            String username = request.getHeader(autoSetCredentials.usernameParam());
            if (username != null && !username.isEmpty()) {
                authContext.setUsername(username);
            }
        }

        return joinPoint.proceed();
    }
}
