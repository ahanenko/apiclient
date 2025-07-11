package ru.napalabs.apiclient.client;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.context.annotation.RequestScope;

@Service
@RequestScope
@Validated
@Setter
@Getter
public class AuthContext {
    @NotNull
    private String token;
    @NotNull
    private String username;

    public void clearToken() {
        this.token = null;
    }
    public void clearUsername() { this.username = null; }
    public void clear() {
        this.token = null;
        this.username = null;
    }
}