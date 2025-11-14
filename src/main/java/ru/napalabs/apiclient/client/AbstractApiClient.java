package ru.napalabs.apiclient.client;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import ru.napalabs.apiclient.exception.ApiClientException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Базовый универсальный клиент для взаимодействия с REST API других сервисов.
 * <p>
 * Инкапсулирует повторяющуюся логику формирования URL, заголовков и выполнения HTTP-запросов.
 * Поддерживает типовые методы: {@code GET}, {@code POST}, {@code PUT}, {@code PATCH}, {@code DELETE}.
 * Также включает вспомогательные методы для multipart и бинарных запросов (файлы).
 * </p>
 *
 * <h3>Основные возможности:</h3>
 * <ul>
 *   <li>Автоматическая подстановка токена авторизации из {@link AuthContext}</li>
 *   <li>Единый метод {@link #exchange(String, HttpMethod, HttpEntity, ParameterizedTypeReference)} для обмена с сервером</li>
 *   <li>Поддержка передачи query-параметров, JSON и multipart-запросов</li>
 *   <li>Автоматическая валидация конфигурации эндпоинтов при инициализации</li>
 *   <li>Гибкое логирование запросов и ответов</li>
 * </ul>
 *
 * <p><b>Пример использования:</b></p>
 * <pre>{@code
 * @Service
 * public class ExampleClient extends AbstractApiClient {
 *
 *     @Value("${example.api.endpoint}")
 *     private String endpointGetData;
 *
 *     public ExampleClient(RestTemplate restTemplate, AuthContext authContext,
 *                          @Value("${example.api.url}") String baseUrl) {
 *         super(restTemplate, authContext, baseUrl);
 *     }
 *
 *     public ExampleResponse getExampleData(String id) {
 *         return executeGetRequest(
 *             endpointGetData,
 *             new ParameterizedTypeReference<ExampleResponse>() {},
 *             Map.of("id", id)
 *         );
 *     }
 * }
 * }</pre>
 *
 * @author Napalabs
 * @version 2.0
 */
@Slf4j
public abstract class AbstractApiClient {
    /** HTTP клиент, используемый для выполнения запросов. */
    protected final RestTemplate restTemplate;

    /** Контекст авторизации, содержащий токен и имя пользователя. */
    protected final AuthContext authContext;

    /** Базовый URL удалённого сервиса. */
    protected final String baseUrl;

    /** Константа, обозначающая отсутствие query-параметров. */
    public static final MultiValueMap<String, String> WITHOUT_QUERY_PARAMS = new LinkedMultiValueMap<>();

    /**
     * Создаёт экземпляр базового клиента.
     *
     * @param restTemplate клиент {@link RestTemplate} для HTTP-вызовов
     * @param authContext  контекст авторизации
     * @param baseUrl      базовый URL удалённого API
     */
    protected AbstractApiClient(RestTemplate restTemplate, AuthContext authContext, String baseUrl) {
        this.restTemplate = restTemplate;
        this.authContext = authContext;
        this.baseUrl = baseUrl;
    }

    /**
     * Проверяет корректность конфигурации эндпоинтов после создания бина.
     * Проверяются поля, начинающиеся с {@code endpoint} и аннотированные {@link org.springframework.beans.factory.annotation.Value}.
     */
    @PostConstruct
    protected final void autoValidateEndpoints() {
        if (this.baseUrl == null || this.baseUrl.isBlank()) {
            throw new IllegalStateException("Base URL is not set for " + getClass().getSimpleName());
        }

        List<String> endpoints = new ArrayList<>();
        for (Field field : this.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(org.springframework.beans.factory.annotation.Value.class) &&
                    field.getName().startsWith("endpoint")) {
                field.setAccessible(true);
                try {
                    String endpoint = (String) field.get(this);
                    if (endpoint != null) {
                        endpoints.add(endpoint);
                    }
                } catch (IllegalAccessException | ClassCastException e) {
                    throw new IllegalStateException("Failed to access endpoint field", e);
                }
            }
        }

        if (!endpoints.isEmpty()) {
            validateConfig(endpoints.toArray(new String[0]));
        }
    }

    /**
     * Проверяет, что все эндпоинты заданы и не пусты.
     *
     * @param endpoints список эндпоинтов для проверки
     * @throws IllegalStateException если какой-либо эндпоинт не задан
     */
    protected final void validateConfig(String... endpoints) {
        for (String endpoint : endpoints) {
            if (endpoint == null || endpoint.isBlank()) {
                throw new IllegalStateException("Endpoint is not set for " + getClass().getSimpleName());
            }
        }
    }

    /**
     * Отфильтровывает пустые и {@code null} значения из карты query-параметров.
     *
     * @param in исходные параметры
     * @return очищенные параметры
     */
    protected final MultiValueMap<String, String> filterQueryParams(MultiValueMap<String, String> in) {
        if (in == null) return WITHOUT_QUERY_PARAMS;
        MultiValueMap<String, String> out = new LinkedMultiValueMap<>();
        in.forEach((k, values) -> {
            if (k != null && !k.isBlank() && values != null) {
                for (String v : values) {
                    if (v != null && !v.isBlank()) {
                        out.add(k, v);
                    }
                }
            }
        });
        return out;
    }

    /**
     * Формирует полный URL на основе базового адреса и query-параметров.
     *
     * @param endpoint относительный путь эндпоинта
     * @param queryParams карта параметров запроса
     * @return объект {@link UriComponents}, представляющий итоговый URL
     */
    protected final UriComponents buildUrl(String endpoint, MultiValueMap<String, String> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path(endpoint);
        MultiValueMap<String, String> qp = filterQueryParams(queryParams);
        if (!qp.isEmpty()) builder.queryParams(qp);
        return builder.build();
    }

    /**
     * Формирует заголовки HTTP-запроса, включая авторизацию и список поддерживаемых MIME-типов.
     *
     * @param acceptedMediaTypes список типов контента, которые клиент может принимать
     * @return заголовки {@link HttpHeaders}
     */
    protected final HttpHeaders composeHeaders(List<MediaType> acceptedMediaTypes) {
        var headers = new HttpHeaders();

        if (authContext != null && authContext.getToken() != null && !authContext.getToken().isBlank()) {
            headers.setBearerAuth(authContext.getToken());
        }
        if (acceptedMediaTypes != null && !acceptedMediaTypes.isEmpty()) {
            headers.setAccept(acceptedMediaTypes);
        }
        return headers;
    }

    /**
     * Возвращает заголовки для JSON-запросов с Accept = application/json.
     */
    protected final HttpHeaders jsonAcceptHeaders() {
        return composeHeaders(List.of(MediaType.APPLICATION_JSON));
    }

    /**
     * Возвращает заголовки для JSON-запросов с Accept и Content-Type = application/json.
     */
    protected final HttpHeaders jsonContentHeaders() {
        HttpHeaders h = composeHeaders(List.of(MediaType.APPLICATION_JSON));
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    protected final HttpHeaders composeJsonHeaders() { return jsonAcceptHeaders(); }
    protected final HttpHeaders composeFileHeaders() { return composeHeaders(List.of(MediaType.ALL)); }
    protected final HttpHeaders composeAnyHeaders()  { return composeHeaders(List.of(MediaType.ALL)); }

    private HttpEntity<?> composeHeadersEntity(List<MediaType> acceptedMediaTypes) {
        return new HttpEntity<>(composeHeaders(acceptedMediaTypes));
    }

    protected final HttpEntity<?> composeJsonHeadersEntity() { return composeHeadersEntity(List.of(MediaType.APPLICATION_JSON)); }
    protected final HttpEntity<?> composeFileHeadersEntity() { return composeHeadersEntity(List.of(MediaType.ALL)); }
    protected final HttpEntity<?> composeAnyHeadersEntity()  { return composeHeadersEntity(List.of(MediaType.ALL)); }

    /**
     * Унифицированный метод выполнения HTTP-запроса.
     * Логирует вызов и проверяет статус-код ответа.
     *
     * @param url           полный URL запроса
     * @param method        HTTP-метод
     * @param requestEntity тело запроса и заголовки
     * @param responseType  ожидаемый тип тела ответа
     * @param <T>           тип тела ответа
     * @return тело ответа
     * @throws ApiClientException если ответ не является успешным (код ≠ 2xx)
     */
    protected final <T> T exchange(String url,
                                   HttpMethod method,
                                   HttpEntity<?> requestEntity,
                                   ParameterizedTypeReference<T> responseType) {
        if (log.isDebugEnabled()) {
            log.debug("HTTP {} {}", method, url);
        }
        ResponseEntity<T> response = restTemplate.exchange(url, method, requestEntity, responseType);

        if (log.isDebugEnabled()) {
            log.debug("HTTP {} -> {}", method, response.getStatusCode());
        }

        if (response.getStatusCode().is2xxSuccessful()) {
            return response.getBody();
        }
        throw new ApiClientException(response);
    }

    // ----------------------------- GET ---------------------------------

    protected final <T> T executeGetRequest(String endpoint,
                                            ParameterizedTypeReference<T> responseType,
                                            MultiValueMap<String, String> queryParams) {
        var url = buildUrl(endpoint, queryParams).toUriString();
        return exchange(url, HttpMethod.GET, composeHeadersEntity(List.of(MediaType.APPLICATION_JSON)), responseType);
    }

    protected final <T> T executeGetRequest(String endpoint,
                                            ParameterizedTypeReference<T> responseType) {
        return executeGetRequest(endpoint, responseType, WITHOUT_QUERY_PARAMS);
    }

    protected final <T> T executeGetRequest(UriComponents uriComponents,
                                            ParameterizedTypeReference<T> responseType,
                                            MediaType acceptedMediaType) {
        var url = uriComponents.toUriString();
        return exchange(url, HttpMethod.GET, composeHeadersEntity(List.of(acceptedMediaType)), responseType);
    }

    // ----------------------------- GET FILE ----------------------------

    protected final ResponseEntity<byte[]> executeGetRequestForFile(UriComponents uriComponents) {
        var url = uriComponents.toUriString();
        if (log.isDebugEnabled()) log.debug("HTTP GET (file) {}", url);

        var response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                composeFileHeadersEntity(),
                byte[].class
        );

        if (log.isDebugEnabled()) log.debug("HTTP GET (file) -> {}", response.getStatusCode());

        if (response.getStatusCode().is2xxSuccessful()) {
            return response;
        }
        throw new ApiClientException(response);
    }

    protected final ResponseEntity<byte[]> executeGetRequestForFile(String endpoint,
                                                                    MultiValueMap<String, String> queryParams) {
        var url = buildUrl(endpoint, queryParams);
        return executeGetRequestForFile(url);
    }

    protected final ResponseEntity<byte[]> executeGetRequestForFile(String endpoint) {
        return executeGetRequestForFile(endpoint, WITHOUT_QUERY_PARAMS);
    }

    // ----------------------------- POST --------------------------------

    protected final <H,T> T executePostRequest(String url, HttpEntity<H> requestEntity, ParameterizedTypeReference<T> responseType) {
        return exchange(url, HttpMethod.POST, requestEntity, responseType);
    }

    protected final <T> T executeMultipartPostRequest(
            String endpoint,
            MultiValueMap<String, Object> parts,
            ParameterizedTypeReference<T> responseType,
            MultiValueMap<String, String> queryParams
    ) {
        UriComponents url = buildUrl(endpoint, queryParams);
        HttpHeaders headers = jsonAcceptHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(parts, headers);
        return executePostRequest(url.toUriString(), requestEntity, responseType);
    }

    protected final <T> T executeMultipartPostRequest(
            String endpoint,
            MultiValueMap<String, Object> parts,
            ParameterizedTypeReference<T> responseType
    ) {
        return executeMultipartPostRequest(endpoint, parts, responseType, WITHOUT_QUERY_PARAMS);
    }

    protected final <T> T executeJsonBodyPostRequest(
            String endpoint,
            JsonNode body,
            ParameterizedTypeReference<T> responseType
    ) {
        UriComponents url = buildUrl(endpoint, WITHOUT_QUERY_PARAMS);
        HttpEntity<JsonNode> requestEntity = new HttpEntity<>(body, jsonContentHeaders());
        return executePostRequest(url.toUriString(), requestEntity, responseType);
    }

    // ----------------------------- PUT ---------------------------------

    protected final <H, T> T executePutRequest(String url,
                                               HttpEntity<H> requestEntity,
                                               ParameterizedTypeReference<T> responseType) {
        return exchange(url, HttpMethod.PUT, requestEntity, responseType);
    }

    protected final <T> T executeMultipartPutRequest(
            String endpoint,
            MultiValueMap<String, Object> parts,
            ParameterizedTypeReference<T> responseType,
            MultiValueMap<String, String> queryParams
    ) {
        UriComponents url = buildUrl(endpoint, queryParams);
        HttpHeaders headers = jsonAcceptHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(parts, headers);
        return executePutRequest(url.toUriString(), requestEntity, responseType);
    }

    protected final <T> T executeMultipartPutRequest(
            String endpoint,
            MultiValueMap<String, Object> parts,
            ParameterizedTypeReference<T> responseType
    ) {
        return executeMultipartPutRequest(endpoint, parts, responseType, WITHOUT_QUERY_PARAMS);
    }

    protected final <T> T executeJsonBodyPutRequest(
            String endpoint,
            JsonNode body,
            ParameterizedTypeReference<T> responseType
    ) {
        UriComponents url = buildUrl(endpoint, WITHOUT_QUERY_PARAMS);
        HttpEntity<JsonNode> requestEntity = new HttpEntity<>(body, jsonContentHeaders());
        return executePutRequest(url.toUriString(), requestEntity, responseType);
    }

    // ----------------------------- DELETE -------------------------------

    protected final <T> T executeDeleteRequest(
            String endpoint,
            ParameterizedTypeReference<T> responseType,
            MultiValueMap<String, String> queryParams
    ) {
        var url = buildUrl(endpoint, queryParams).toUriString();
        return exchange(url, HttpMethod.DELETE, composeAnyHeadersEntity(), responseType);
    }

    protected final <T> T executeDeleteRequest(
            String endpoint,
            ParameterizedTypeReference<T> responseType
    ) {
        return executeDeleteRequest(endpoint, responseType, WITHOUT_QUERY_PARAMS);
    }

    // ----------------------------- PATCH -------------------------------

    protected final <T> T executeJsonBodyPatchRequest(
            String endpoint,
            JsonNode body,
            ParameterizedTypeReference<T> responseType
    ) {
        UriComponents url = buildUrl(endpoint, WITHOUT_QUERY_PARAMS);
        HttpHeaders headers = jsonContentHeaders();
        HttpEntity<JsonNode> requestEntity = new HttpEntity<>(body, headers);
        return exchange(url.toUriString(), HttpMethod.PATCH, requestEntity, responseType);
    }
}
