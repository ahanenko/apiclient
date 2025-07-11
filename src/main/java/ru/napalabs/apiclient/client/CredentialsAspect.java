package ru.napalabs.apiclient.client;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class CredentialsAspect {
    private final AuthContext authContext;

    public CredentialsAspect(AuthContext authContext) {
        this.authContext = authContext;
    }

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
