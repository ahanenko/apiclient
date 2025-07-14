package ru.napalabs.apiclient.client;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CredentialsAspectTest {
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private AuthContext authContext;
    @Mock
    private AutoSetCredentials autoSetCredentials;
    @Mock
    private RequestContextHolder requestContextHolder;
    @InjectMocks
    private CredentialsAspect credentialsAspect;

    @Test
    void do_credentialsAspect_with_allArguments() throws Throwable {
        setServletRequestContext(true, true);

        // Настраиваем поведение аннотации
        when(autoSetCredentials.token()).thenReturn(true);
        when(autoSetCredentials.tokenHeader()).thenReturn("Authorization");
        when(autoSetCredentials.username()).thenReturn(true);
        when(autoSetCredentials.usernameParam()).thenReturn("username");
        when(joinPoint.proceed()).thenReturn(null);

        credentialsAspect.autoSetCredentials(joinPoint, autoSetCredentials);

        verify(authContext, times(1)).setToken("testToken");
        verify(authContext, times(1)).setUsername("testUser");
        verify(joinPoint).proceed();
    }

    @Test
    void do_credentialsAspect_with_token_only() throws Throwable {
        setServletRequestContext(true, false);

        // Настраиваем поведение аннотации
        when(autoSetCredentials.token()).thenReturn(true);
        when(autoSetCredentials.tokenHeader()).thenReturn("Authorization");
        when(autoSetCredentials.username()).thenReturn(false);
        when(joinPoint.proceed()).thenReturn(null);

        credentialsAspect.autoSetCredentials(joinPoint, autoSetCredentials);

        verify(authContext, times(1)).setToken("testToken");
        verify(authContext, never()).setUsername("testUser");
        verify(joinPoint).proceed();
    }

    @Test
    void do_credentialsAspect_with_username_only() throws Throwable {
        setServletRequestContext(false, true);

        // Настраиваем поведение аннотации
        when(autoSetCredentials.token()).thenReturn(false);
        when(autoSetCredentials.username()).thenReturn(true);
        when(autoSetCredentials.usernameParam()).thenReturn("username");
        when(joinPoint.proceed()).thenReturn(null);

        credentialsAspect.autoSetCredentials(joinPoint, autoSetCredentials);

        verify(authContext, never()).setToken("testToken");
        verify(authContext, times(1)).setUsername("testUser");
        verify(joinPoint).proceed();
    }

    private void setServletRequestContext(boolean token, boolean username) {
        // Создаем mock запроса
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (token) { request.addHeader("Authorization", "Bearer testToken"); }
        if (username) { request.addHeader("username", "testUser"); }

        // Устанавливаем mock атрибуты запроса в RequestContextHolder
        ServletRequestAttributes requestAttributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(requestAttributes);
    }
}