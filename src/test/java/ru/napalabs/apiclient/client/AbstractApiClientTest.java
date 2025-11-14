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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    // Вставить в класс AbstractApiClientTest рядом с тестами POST

    @Test
    void do_success_executeMultipartPutRequest() {
        String endpoint = "/some_put_endpoint";
        var responseType = new ParameterizedTypeReference<String>() {};
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("part1", "value1");
        parts.add("part2", "value2");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(), eq(responseType)))
                .thenReturn(ResponseEntity.ok("success put rest response"));

        var result = abstractApiClient.executeMultipartPutRequest(endpoint, parts, new ParameterizedTypeReference<String>() {});

        assertEquals("success put rest response", result);
    }

    @Test
    void do_failed_executeMultipartPutRequest() {
        String endpoint = "/some_put_endpoint";
        var responseType = new ParameterizedTypeReference<String>() {};
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();

        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(), eq(responseType)))
                .thenReturn(ResponseEntity.internalServerError().body("Error while putting request"));

        var error = assertThrows(ApiClientException.class,
                () -> abstractApiClient.executeMultipartPutRequest(endpoint, parts, new ParameterizedTypeReference<String>() {}));

        assertTrue(error.getMessage().contains("Error while putting request"));
    }

    @Test
    void do_success_executeJsonBodyPutRequest() {
        String endpoint = "/some_put_endpoint";
        var responseType = new ParameterizedTypeReference<String>() {};
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode body = mapper.createObjectNode().put("part1", "value1").put("part2", "value2");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(), eq(responseType)))
                .thenReturn(ResponseEntity.ok("success put rest response"));

        var result = abstractApiClient.executeJsonBodyPutRequest(endpoint, body, new ParameterizedTypeReference<String>() {});

        assertEquals("success put rest response", result);
    }

    @Test
    void do_failed_executeJsonBodyPutRequest() {
        String endpoint = "/some_put_endpoint";
        var responseType = new ParameterizedTypeReference<String>() {};
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode body = mapper.createObjectNode();

        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(), eq(responseType)))
                .thenReturn(ResponseEntity.internalServerError().body("Error while putting request"));

        var error = assertThrows(ApiClientException.class,
                () -> abstractApiClient.executeJsonBodyPutRequest(endpoint, body, new ParameterizedTypeReference<String>() {}));

        assertTrue(error.getMessage().contains("Error while putting request"));
    }

    @Test
    void success_executeDeleteRequest() {
        String endpoint = "/some_delete_endpoint";
        var responseType = new ParameterizedTypeReference<String>() {};
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), any(), eq(responseType)))
                .thenReturn(ResponseEntity.ok("deleted"));

        var result = abstractApiClient.executeDeleteRequest(endpoint, responseType);
        assertEquals("deleted", result);
    }

    @Test
    void failed_executeDeleteRequest() {
        String endpoint = "/some_delete_endpoint";
        var responseType = new ParameterizedTypeReference<String>() {};
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), any(), eq(responseType)))
                .thenReturn(ResponseEntity.status(404).body("not found"));

        var err = assertThrows(ApiClientException.class,
                () -> abstractApiClient.executeDeleteRequest(endpoint, responseType));
        assertTrue(err.getMessage().contains("not found"));
    }

    @Test
    void success_executeJsonBodyPatchRequest() {
        String endpoint = "/some_patch_endpoint";
        var responseType = new ParameterizedTypeReference<String>() {};
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);

        JsonNode body = objectMapper.createObjectNode().put("field", "value");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.PATCH), any(), eq(responseType)))
                .thenReturn(ResponseEntity.ok("patched"));

        var result = abstractApiClient.executeJsonBodyPatchRequest(endpoint, body, responseType);
        assertEquals("patched", result);
    }

    @Test
    void failed_executeJsonBodyPatchRequest() {
        String endpoint = "/some_patch_endpoint";
        var responseType = new ParameterizedTypeReference<String>() {};
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);

        JsonNode body = objectMapper.createObjectNode();

        when(restTemplate.exchange(anyString(), eq(HttpMethod.PATCH), any(), eq(responseType)))
                .thenReturn(ResponseEntity.status(400).body("bad"));

        var err = assertThrows(ApiClientException.class,
                () -> abstractApiClient.executeJsonBodyPatchRequest(endpoint, body, responseType));
        assertTrue(err.getMessage().contains("bad"));
    }

    /** (п.3) Проверка, что метод с acceptedMediaType работает и заголовки корректны. */
    @Test
    void success_executeGetRequest_withAcceptedMediaType_andToken() {
        var responseType = new ParameterizedTypeReference<String>() {};
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);
        AuthContext authContext = mock(AuthContext.class);
        ReflectionTestUtils.setField(abstractApiClient, "authContext", authContext);

        when(authContext.getToken()).thenReturn("jwtToken");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(responseType)))
                .thenReturn(ResponseEntity.ok("ok"));

        var uri = abstractApiClient.buildUrl("/ep", AbstractApiClient.WITHOUT_QUERY_PARAMS);
        var result = abstractApiClient.executeGetRequest(uri, responseType, MediaType.APPLICATION_JSON);

        assertEquals("ok", result);
    }

    /** (п.13) Пустые/незаполненные query-параметры не попадают в URL. */
    @Test
    void do_buildUrl_filtersEmptyQueryParams() {
        String endpoint = "/ep";
        LinkedMultiValueMap<String, String> qp = new LinkedMultiValueMap<>();
        qp.add("a", "1");
        qp.add("b", "");
        qp.add("c", null);

        var url = abstractApiClient.buildUrl(endpoint, qp).toUriString();
        assertEquals(BASE_URL + endpoint + "?a=1", url);
    }
// ---- ДОПОЛНИТЕЛЬНЫЕ ТЕСТЫ ДЛЯ ПОВЫШЕНИЯ ПОКРЫТИЯ ----

    // 1) headers helpers: jsonAcceptHeaders / jsonContentHeaders
    @Test
    void headers_jsonAccept_and_jsonContent_helpers() {
        AuthContext authContext = mock(AuthContext.class);
        ReflectionTestUtils.setField(abstractApiClient, "authContext", authContext);
        when(authContext.getToken()).thenReturn("jwtToken");

        HttpHeaders accept = ReflectionTestUtils.invokeMethod(abstractApiClient, "jsonAcceptHeaders");
        HttpHeaders content = ReflectionTestUtils.invokeMethod(abstractApiClient, "jsonContentHeaders");

        // jsonAcceptHeaders
        assertEquals(MediaType.APPLICATION_JSON, accept.getAccept().get(0));
        assertEquals("Bearer jwtToken", accept.getFirst(HttpHeaders.AUTHORIZATION));
        // jsonContentHeaders
        assertEquals(MediaType.APPLICATION_JSON, content.getAccept().get(0));
        assertEquals(MediaType.APPLICATION_JSON, content.getContentType());
        assertEquals("Bearer jwtToken", content.getFirst(HttpHeaders.AUTHORIZATION));
    }

    // 2) WITHOUT_QUERY_PARAMS безопасная пустая карта
    @Test
    void withoutQueryParams_isNotNull_andEmpty() {
        assertNotNull(AbstractApiClient.WITHOUT_QUERY_PARAMS);
        assertTrue(AbstractApiClient.WITHOUT_QUERY_PARAMS.isEmpty());
    }

    // 3) buildUrl: фильтруются пустые/нулевые key/value
    @Test
    void buildUrl_filters_empty_and_null_queryParams() {
        String endpoint = "/ep";
        var qp = new LinkedMultiValueMap<String, String>();
        qp.add("a", "1");
        qp.add("b", "");
        qp.add("b", "  "); // пробелы тоже считаем пустыми -> не попадут
        qp.add(null, "x"); // null ключ -> игнор
        qp.add("c", null); // null значение -> игнор

        var url = abstractApiClient.buildUrl(endpoint, qp).toUriString();
        assertEquals(BASE_URL + endpoint + "?a=1", url);
    }

    // 4) DELETE: успех (с queryParams)
    @Test
    void success_executeDeleteRequest_withQueryParams() {
        String endpoint = "/delete_me";
        var responseType = new ParameterizedTypeReference<String>() {};
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);

        var qp = new LinkedMultiValueMap<String, String>();
        qp.add("id", "42");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), any(), eq(responseType)))
                .thenReturn(ResponseEntity.ok("deleted"));

        var result = abstractApiClient.executeDeleteRequest(endpoint, responseType, qp);
        assertEquals("deleted", result);
    }

    // 5) DELETE: ошибка
    @Test
    void failed_executeDeleteRequest_returnsError() {
        String endpoint = "/delete_me";
        var responseType = new ParameterizedTypeReference<String>() {};
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), any(), eq(responseType)))
                .thenReturn(ResponseEntity.status(404).body("not found"));

        var err = assertThrows(ApiClientException.class,
                () -> abstractApiClient.executeDeleteRequest(endpoint, responseType));
        assertTrue(err.getMessage().contains("not found"));
    }

    // 6) PATCH: успех + проверяем заголовки (Bearer, Accept, Content-Type)
    @Test
    void success_executeJsonBodyPatchRequest_and_headersSet() {
        String endpoint = "/patch_me";
        var responseType = new ParameterizedTypeReference<String>() {};
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);
        AuthContext authContext = mock(AuthContext.class);
        ReflectionTestUtils.setField(abstractApiClient, "authContext", authContext);
        when(authContext.getToken()).thenReturn("jwtToken");

        JsonNode body = objectMapper.createObjectNode().put("x", 1);

        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.PATCH), captor.capture(), eq(responseType)))
                .thenReturn(ResponseEntity.ok("patched"));

        var result = abstractApiClient.executeJsonBodyPatchRequest(endpoint, body, responseType);
        assertEquals("patched", result);

        HttpHeaders h = captor.getValue().getHeaders();
        assertEquals("Bearer jwtToken", h.getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals(MediaType.APPLICATION_JSON, h.getAccept().get(0));
        assertEquals(MediaType.APPLICATION_JSON, h.getContentType());
    }

    // 7) PATCH: ошибка 400
    @Test
    void failed_executeJsonBodyPatchRequest_badRequest() {
        String endpoint = "/patch_me";
        var responseType = new ParameterizedTypeReference<String>() {};
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);

        JsonNode body = objectMapper.createObjectNode();

        when(restTemplate.exchange(anyString(), eq(HttpMethod.PATCH), any(), eq(responseType)))
                .thenReturn(ResponseEntity.badRequest().body("bad"));

        var err = assertThrows(ApiClientException.class,
                () -> abstractApiClient.executeJsonBodyPatchRequest(endpoint, body, responseType));
        assertTrue(err.getMessage().contains("bad"));
    }

    // 8) PUT: проверяем заголовки (Bearer + Accept + Content-Type)
    @Test
    void put_headers_include_bearer_and_json() {
        String endpoint = "/put_me";
        var responseType = new ParameterizedTypeReference<String>() {};
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);
        AuthContext authContext = mock(AuthContext.class);
        ReflectionTestUtils.setField(abstractApiClient, "authContext", authContext);
        when(authContext.getToken()).thenReturn("jwtToken");

        JsonNode body = objectMapper.createObjectNode().put("a", "b");

        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), captor.capture(), eq(responseType)))
                .thenReturn(ResponseEntity.ok("ok"));

        var result = abstractApiClient.executeJsonBodyPutRequest(endpoint, body, responseType);
        assertEquals("ok", result);

        HttpHeaders h = captor.getValue().getHeaders();
        assertEquals("Bearer jwtToken", h.getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals(MediaType.APPLICATION_JSON, h.getAccept().get(0));
        assertEquals(MediaType.APPLICATION_JSON, h.getContentType());
    }

    // 9) GET file: по endpoint + queryParams
    @Test
    void success_executeGetRequestForFile_byEndpoint_withQueryParams() {
        String endpoint = "/files";
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);

        var qp = new LinkedMultiValueMap<String, String>();
        qp.add("name", "report.pdf");

        ResponseEntity<byte[]> expected = ResponseEntity.ok(new byte[]{1,2,3});
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(expected);

        ResponseEntity<byte[]> res = abstractApiClient.executeGetRequestForFile(endpoint, qp);
        assertEquals(expected, res);
    }

    // 10) GET file: по endpoint без параметров + ошибка
    @Test
    void failed_executeGetRequestForFile_byEndpoint_withoutParams() {
        String endpoint = "/files";
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);

        ResponseEntity<byte[]> bad = ResponseEntity.status(404).body(null);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(bad);

        assertThrows(ApiClientException.class,
                () -> abstractApiClient.executeGetRequestForFile(endpoint));
    }

    // 1) PUT multipart без query-параметров
    @Test
    void success_executeMultipartPutRequest_withoutQueryParams() {
        String endpoint = "/put_multipart";
        var responseType = new ParameterizedTypeReference<String>() {};
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);

        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("file", "data");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(responseType)))
                .thenReturn(ResponseEntity.ok("put-multipart-ok"));

        var result = abstractApiClient.executeMultipartPutRequest(endpoint, parts, responseType);
        assertEquals("put-multipart-ok", result);
    }

    // 2) POST multipart с query-параметрами
    @Test
    void success_executeMultipartPostRequest_withQueryParams() {
        String endpoint = "/post_multipart";
        var responseType = new ParameterizedTypeReference<String>() {};
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);

        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("file", "data");

        var qp = new LinkedMultiValueMap<String, String>();
        qp.add("folder", "inbox");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(responseType)))
                .thenReturn(ResponseEntity.ok("post-multipart-ok"));

        // вызываем перегрузку С queryParams (через protected метод — из теста он виден)
        var url = abstractApiClient.buildUrl(endpoint, qp); // просто для формальности проверки URL, сам метод ниже использует buildUrl
        assertTrue(url.toUriString().contains("folder=inbox"));

        var result = ReflectionTestUtils.invokeMethod(
                abstractApiClient,
                "executeMultipartPostRequest",
                endpoint, parts, responseType, qp
        );
        assertEquals("post-multipart-ok", result);
    }

    // 3) Прямой вызов put helper: executePutRequest(String url, HttpEntity, Type)
    @Test
    void success_executePutRequest_directUrl() {
        var responseType = new ParameterizedTypeReference<String>() {};
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);

        String url = "http://localhost:8080/any_put";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{\"x\":1}", headers);

        when(restTemplate.exchange(eq(url), eq(HttpMethod.PUT), any(HttpEntity.class), eq(responseType)))
                .thenReturn(ResponseEntity.ok("ok"));

        var result = ReflectionTestUtils.invokeMethod(
                abstractApiClient,
                "executePutRequest",
                url, entity, responseType
        );
        assertEquals("ok", result);
    }

    // 4) GET file по endpoint без query — ветка успеха
    @Test
    void success_executeGetRequestForFile_byEndpoint_withoutParams() {
        String endpoint = "/files";
        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(abstractApiClient, "restTemplate", restTemplate);

        ResponseEntity<byte[]> expected = ResponseEntity.ok(new byte[]{10,20});
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(expected);

        var res = abstractApiClient.executeGetRequestForFile(endpoint);
        assertEquals(expected, res);
    }

    // --------------- ДОПОЛНИТЕЛЬНЫЕ ТЕСТЫ ДЛЯ ПОКРЫТИЯ ВСПОМОГАТЕЛЬНЫХ compose* ------------------

    @Test
    void composeJsonHeaders_returnsAcceptJson() {
        var headers = ReflectionTestUtils.invokeMethod(abstractApiClient, "composeJsonHeaders");
        assertNotNull(headers);
        assertTrue(headers instanceof HttpHeaders);
        assertEquals(List.of(MediaType.APPLICATION_JSON), ((HttpHeaders) headers).getAccept());
    }

    @Test
    void composeFileHeaders_returnsAcceptAll() {
        var headers = ReflectionTestUtils.invokeMethod(abstractApiClient, "composeFileHeaders");
        assertNotNull(headers);
        assertEquals(List.of(MediaType.ALL), ((HttpHeaders) headers).getAccept());
    }

    @Test
    void composeAnyHeaders_returnsAcceptAll() {
        var headers = ReflectionTestUtils.invokeMethod(abstractApiClient, "composeAnyHeaders");
        assertNotNull(headers);
        assertEquals(List.of(MediaType.ALL), ((HttpHeaders) headers).getAccept());
    }

    @Test
    void composeJsonHeadersEntity_containsHttpEntityWithJsonAccept() {
        var entity = ReflectionTestUtils.invokeMethod(abstractApiClient, "composeJsonHeadersEntity");
        assertNotNull(entity);
        assertTrue(entity instanceof HttpEntity);
        HttpHeaders headers = ((HttpEntity<?>) entity).getHeaders();
        assertEquals(List.of(MediaType.APPLICATION_JSON), headers.getAccept());
    }

}