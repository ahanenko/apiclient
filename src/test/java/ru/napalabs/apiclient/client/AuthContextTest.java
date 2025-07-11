package ru.napalabs.apiclient.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@ActiveProfiles("test")
public class AuthContextTest {
    @MockBean
    private ResourceLoader resourceLoader;
    @Autowired
    private AuthContext authContext;

    @Test
    void testSetAndGetToken() {
        String token = "test-token-123";
        authContext.setToken(token);
        assertEquals(token, authContext.getToken(), "Token should match the set value");
    }

    @Test
    void testClearToken() {
        String token = "test-token-123";
        authContext.setToken(token);
        authContext.clear();
        assertNull(authContext.getToken(), "Token should be null after clear");
    }

    @Test
    void testDifferentTokensInDifferentScopes() {
        // First scope
        String token1 = "token-scope-1";
        authContext.setToken(token1);
        String retrievedToken1 = authContext.getToken();

        // Create new instance to simulate new request scope
        AuthContext newAuthContext = new AuthContext();
        String token2 = "token-scope-2";
        newAuthContext.setToken(token2);

        // Verify tokens are different
        assertEquals(token1, retrievedToken1, "First scope should retain its token");
        assertEquals(token2, newAuthContext.getToken(), "Second scope should have different token");
        assertNotEquals(retrievedToken1, newAuthContext.getToken(), "Tokens from different scopes should not match");
    }
}