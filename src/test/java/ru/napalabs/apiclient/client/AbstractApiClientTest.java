package ru.napalabs.apiclient.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import ru.napalabs.apiclient.exception.ApiClientException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractApiClientTest {
    private final String BASE_URL = "http://localhost:8080";
    private AbstractApiClient abstractApiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        abstractApiClient = new AbstractApiClient(null, null, BASE_URL) {};
    }

    @ParameterizedTest
    @NullAndEmptySource
    void do_buildUrl_withoutParams(String queryParamStr) {
        String endpoint = "/some_endpoint";
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (queryParamStr == null) params = AbstractApiClient.WITHOUT_QUERY_PARAMS;
        var url = abstractApiClient.buildUrl(endpoint, params);
        assertEquals(BASE_URL + endpoint, url.toString());
    }

    @Test
    void do_buildUrl_withParams() {
        String endpoint = "/some_endpoint";
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.add("someFlag", "true");
        queryParams.add("someValue", "1");
        var url = abstractApiClient.buildUrl(endpoint, queryParams);
        assertEquals(BASE_URL + endpoint + "?someFlag=true&someValue=1", url.toString());
    }

    @Test
    void do_buildUrl_withNulls() {
        ReflectionTestUtils.setField(abstractApiClient, "baseUrl", null);
        assertThrows(IllegalArgumentException.class, () -> abstractApiClient.buildUrl(null, null));
    }

    @Test
    void success_test_validateConfig() {
        String[] endpoints = {"/endpoint1", "/endpoint2", "/endpoint3"};
        assertDoesNotThrow(() -> abstractApiClient.validateConfig(endpoints));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void do_validateConfig_fail_when_someEndpointIsNullOrEmpty(String endpoint) {
        String[] endpoints = {"/endpoint1", endpoint, "/endpoint3"};
        var error = assertThrows(IllegalStateException.class, () -> abstractApiClient.validateConfig(endpoints));
        assertTrue(error.getMessage().startsWith("Endpoint is not set for "));
    }

    @Test
    void success_autoValidateEndpoints() {
        // Создаем анонимный класс с тестовыми полями
        AbstractApiClient client = new AbstractApiClient(null, null, BASE_URL) {
            @Value("${valid.endpoint1}")
            private String validEndpoint1 = "/api/valid1";
            @Value("${valid.endpoint2}")
            private String validEndpoint2 = "/api/valid2";
            @Value("${valid.endpoint3}")
            private String validEndpoint3;
        };

        assertDoesNotThrow(client::autoValidateEndpoints);
    }

    @Test
    void do_autoValidateEndpoints_fail() {
        // Создаем анонимный класс с тестовыми полями
        AbstractApiClient client = new AbstractApiClient(null, null, BASE_URL) {
            @Value("${valid.endpoint1}")
            private Integer endpointValidEndpoint1 = 1;
        };

        var error = assertThrows(IllegalStateException.class, client::autoValidateEndpoints);
        assertEquals("Failed to access endpoint field", error.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void do_autoValidateEndpoints_fail_when_baseUrlIsNullOrEmpty(String baseUrl) {
        ReflectionTestUtils.setField(abstractApiClient, "baseUrl", baseUrl);
        var error = assertThrows(IllegalStateException.class, () -> abstractApiClient.autoValidateEndpoints());
        assertTrue(error.getMessage().startsWith("Base URL is not set for "));
    }

    @Test
    void success_executeGetRequest() {
        String endpoint = "/some_endpoint";
        var responseType = new ParameterizedTypeReference<String>() {};
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(responseType)))
                .thenReturn(ResponseEntity.ok("success rest response"));

        var result = abstractApiClient.executeGetRequest(endpoint, responseType, AbstractApiClient.WITHOUT_QUERY_PARAMS);
        assertEquals("success rest response", result);
    }

    @Test
    void success_executeGetRequest_withoutQueryParams() {
        String endpoint = "/some_endpoint";
        var responseType = new ParameterizedTypeReference<String>() {};
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(responseType)))
                .thenReturn(ResponseEntity.ok("success rest response"));

        var result = abstractApiClient.executeGetRequest(endpoint, responseType);
        assertEquals("success rest response", result);
    }

    @Test
    void success_withToken_executeGetRequest() {
        String endpoint = "/some_endpoint";
        var responseType = new ParameterizedTypeReference<String>() {};
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);
        AuthContext authContext = mock(AuthContext.class);
        ReflectionTestUtils.setField(abstractApiClient, "authContext", authContext);

        when(authContext.getToken()).thenReturn("jwtToken");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(responseType)))
                .thenReturn(ResponseEntity.ok("success rest response"));

        ArgumentCaptor<HttpEntity<?>> httpEntityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        var result = abstractApiClient.executeGetRequest(endpoint, responseType, AbstractApiClient.WITHOUT_QUERY_PARAMS);

        verify(restTemplate).exchange(
                anyString(),
                eq(HttpMethod.GET),
                httpEntityCaptor.capture(),
                eq(responseType)
        );
        HttpEntity<?> capturedEntity = httpEntityCaptor.getValue();
        HttpHeaders capturedHeaders = capturedEntity.getHeaders();

        // Проверяем заголовки
        assertEquals("Bearer jwtToken", capturedHeaders.getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals(MediaType.APPLICATION_JSON, capturedHeaders.getAccept().get(0));
        assertEquals("success rest response", result);
    }

    @Test
    void restError_when_executeGetRequest() {
        String endpoint = "/some_endpoint";
        var responseType = new ParameterizedTypeReference<String>() {};
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(responseType)))
                .thenReturn(ResponseEntity.internalServerError().body("Error while getting request"));

        var error = assertThrows(ApiClientException.class,
                () -> abstractApiClient.executeGetRequest(endpoint, responseType, AbstractApiClient.WITHOUT_QUERY_PARAMS));
        assertTrue(error.getMessage().contains("Error while getting request"));
    }

    @Test
    void do_success_executeMultipartPostRequest() {
        String endpoint = "/some_post_endpoint";
        var responseType = new ParameterizedTypeReference<String>() {};
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("part1", "value1");
        parts.add("part2", "value2");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(responseType)))
                .thenReturn(ResponseEntity.ok("success post rest response"));

        var result = abstractApiClient.executeMultipartPostRequest(endpoint, parts, new ParameterizedTypeReference<String>() {});

        assertEquals("success post rest response", result);
    }

    @Test
    void do_failed_executeMultipartPostRequest() {
        String endpoint = "/some_post_endpoint";
        var responseType = new ParameterizedTypeReference<String>() {};
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(responseType)))
                .thenReturn(ResponseEntity.internalServerError().body("Error while getting request"));

        var error = assertThrows(ApiClientException.class,
                () -> abstractApiClient.executeMultipartPostRequest(endpoint, parts, new ParameterizedTypeReference<String>() {}));

        assertTrue(error.getMessage().contains("Error while getting request"));
    }

    @Test
    void do_success_executeJsonBodyPostRequest() {
        // Given
        String endpoint = "/some_post_endpoint";
        var responseType = new ParameterizedTypeReference<String>() {};
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);

        String jsonBody = "{\"part1\":\"value1\",\"part2\":\"value2\"}";
        JsonNode body = objectMapper.valueToTree(jsonBody);

        // When
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(responseType)))
                .thenReturn(ResponseEntity.ok("success post rest response"));

        var result = abstractApiClient.executeJsonBodyPostRequest(endpoint, body, new ParameterizedTypeReference<String>() {});

        // Then
        assertEquals("success post rest response", result);
    }

    @Test
    void do_failed_executeJsonBodyPostRequest() {
        String endpoint = "/some_post_endpoint";
        var responseType = new ParameterizedTypeReference<String>() {};
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);
        JsonNode body = objectMapper.createObjectNode();

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(responseType)))
                .thenReturn(ResponseEntity.internalServerError().body("Error while getting request"));

        var error = assertThrows(ApiClientException.class,
                () -> abstractApiClient.executeJsonBodyPostRequest(endpoint, body, new ParameterizedTypeReference<String>() {}));

        assertTrue(error.getMessage().contains("Error while getting request"));
    }
}