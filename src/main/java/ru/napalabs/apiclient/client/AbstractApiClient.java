package ru.napalabs.apiclient.client;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import ru.napalabs.apiclient.exception.ApiClientException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Базовая реализация API клиента для выполнения GET и POST запросов
 * @author Andrey Khanenko
 * @version 1.2
 */
@Slf4j
public abstract class AbstractApiClient {
    protected final RestTemplate restTemplate;
    /**
     * Контекст, содержащий токен авторизации и имя пользователя.
     */
    protected final AuthContext authContext;
    /**
     * Базовый URL вызываемого сервиса.
     * <p>
     * Рекомендуется указывать адрес без параметров и путей — в "чистом" виде.
     * Например: {@code http://s3-service:8888}, а не {@code http://s3-service:8888/api/v1}.
     * </p>
     */
    protected final String baseUrl;
    /**
     * Константа, обозначающая отсутствие query-параметров при вызове эндпоинта.
     * <p>
     * Может использоваться для методов, не требующих передачи параметров запроса.
     * </p>
     */
    public static final MultiValueMap<String, String> WITHOUT_QUERY_PARAMS = null;

    /**
     * Пример внедрения {@code baseUrl} через конфигурацию Spring при создании клиента в виде бина.
     * <p>
     * Рекомендуется использовать аннотацию {@link Value} для передачи базового URL из настроек приложения.
     * </p>
     *
     * <pre>{@code
     * @Autowired
     * public S3Client(RestTemplate restTemplate,
     *                 AuthContext authContext,
     *                 @Value("${bpms.s3.url}") String baseUrl) {
     *     super(restTemplate, authContext, baseUrl);
     * }
     * }</pre>
     *
     * @param restTemplate экземпляр {@link RestTemplate}, используемый для выполнения HTTP-запросов
     * @param authContext контекст аутентификации, содержащий информацию о текущем пользователе или токене
     * @param baseUrl базовый URL S3-сервиса, задаётся через свойство {@code bpms.s3.url}
     */
    protected AbstractApiClient(RestTemplate restTemplate, AuthContext authContext, String baseUrl) {
        this.restTemplate = restTemplate;
        this.authContext = authContext;
        this.baseUrl = baseUrl;
    }

    /**
     * Выполняет автоматическую валидацию значений всех эндпоинтов,
     * определённых в текущем клиенте.
     * <p>
     * Метод ищет поля, помеченные аннотацией {@link Value}, имена которых
     * начинаются с {@code endpoint}, и проверяет, что они инициализированы.
     * Вызывается автоматически после создания бина.
     * </p>
     *
     * @throws IllegalStateException если базовый URL или какой-либо эндпоинт не задан
     */
    @PostConstruct
    protected final void autoValidateEndpoints() {
        if (this.baseUrl == null || this.baseUrl.isBlank()) {
            throw new IllegalStateException("Base URL is not set for " + getClass().getSimpleName());
        }

        List<String> endpoints = new ArrayList<>();

        // Собираем все поля, помеченные аннотацией @Endpoint
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
     * Проверяет корректность конфигурации клиента — убеждается,
     * что все указанные эндпоинты заданы и не пусты.
     *
     * @param endpoints список эндпоинтов для проверки
     * @throws IllegalStateException если какой-либо эндпоинт пустой или равен {@code null}
     */
    protected final void validateConfig(String... endpoints) {
        for (String endpoint : endpoints) {
            if (endpoint == null || endpoint.isBlank()) {
                throw new IllegalStateException("Endpoint is not set for " + getClass().getSimpleName());
            }
        }
    }

    /**
     * Формирует полный URL вызова с учётом базового адреса и переданных query-параметров.
     *
     * @param endpoint относительный путь эндпоинта
     * @param queryParams параметры запроса (может быть {@code null})
     * @return объект {@link UriComponents}, представляющий итоговый URL
     */
    protected final UriComponents buildUrl(String endpoint, MultiValueMap<String, String> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path(endpoint);
        if (queryParams != null && !queryParams.isEmpty()) {
            builder.queryParams(queryParams);
        }
        return builder.build();
    }

    /**
     * Формирует HTTP-заголовки с указанием принимаемых типов контента.
     *
     * @param acceptedMediaTypes список принимаемых MediaType (может быть пустым)
     * @return объект {@link HttpHeaders}, готовый к использованию при запросе
     */
    protected final HttpHeaders composeHeaders(List<MediaType> acceptedMediaTypes) {
        var headers = new HttpHeaders();

        // Если есть токен в текущем контексте, то добавляем авторизацию
        if (authContext != null && authContext.getToken() != null && !authContext.getToken().isBlank()) {
            headers.setBearerAuth(authContext.getToken());
        }

        // Если acceptedMediaTypes пустой - Accept заголовок не устанавливается
        if (acceptedMediaTypes != null && !acceptedMediaTypes.isEmpty()) {
            headers.setAccept(acceptedMediaTypes);
        }

        return headers;
    }

    /**
     * Вспомогательные методы для часто используемых конфигураций
     */
    protected final HttpHeaders composeJsonHeaders() {
        return composeHeaders(List.of(MediaType.APPLICATION_JSON));
    }

    protected final HttpHeaders composeFileHeaders() {
        return composeHeaders(List.of(MediaType.ALL)); // или пустой список
    }

    protected final HttpHeaders composeAnyHeaders() {
        return composeHeaders(List.of(MediaType.ALL));
    }

    // Соответствующие методы для HttpEntity
    private HttpEntity<?> composeHeadersEntity(List<MediaType> acceptedMediaTypes) {
        return new HttpEntity<>(composeHeaders(acceptedMediaTypes));
    }

    protected final HttpEntity<?> composeJsonHeadersEntity() {
        return composeHeadersEntity(List.of(MediaType.APPLICATION_JSON));
    }

    protected final HttpEntity<?> composeFileHeadersEntity() {
        return composeHeadersEntity(List.of(MediaType.ALL));
    }

    protected final HttpEntity<?> composeAnyHeadersEntity() {
        return composeHeadersEntity(List.of(MediaType.ALL));
    }

    /**
     * Выполняет GET-запрос по указанному эндпойнту с переданными query-параметрами.
     *
     * @param endpoint относительный путь эндпоинта
     * @param responseType тип ожидаемого тела ответа
     * @param queryParams параметры запроса (может быть {@code null} - {@code WITHOUT_QUERY_PARAMS})
     * @param <T> тип возвращаемого объекта
     * @return тело ответа, десериализованное в указанный тип
     * @throws ApiClientException если ответ сервера не имеет статус 2xx
     */
    protected final <T> T executeGetRequest(String endpoint,
                                            ParameterizedTypeReference<T> responseType,
                                            MultiValueMap<String, String> queryParams) {
        var url = buildUrl(endpoint, queryParams);

        return executeGetRequest(url, responseType, MediaType.APPLICATION_JSON);
    }

    /**
     * Выполняет GET-запрос без query-параметров.
     *
     * @param endpoint относительный путь эндпоинта
     * @param responseType тип ожидаемого тела ответа
     * @param <T> тип возвращаемого объекта
     * @return тело ответа, десериализованное в указанный тип
     * @throws ApiClientException если ответ сервера не имеет статус 2xx
     */
    protected final <T> T executeGetRequest(String endpoint,
                                            ParameterizedTypeReference<T> responseType) {
        return executeGetRequest(endpoint, responseType, WITHOUT_QUERY_PARAMS);
    }

    /**
     * Выполняет GET-запрос по заранее собранному {@link UriComponents}.
     *
     * @param uriComponents объект, содержащий полный URL и параметры запроса
     * @param responseType тип ожидаемого тела ответа
     * @param <T> тип возвращаемого объекта
     * @return тело ответа, десериализованное в указанный тип
     * @throws ApiClientException если ответ сервера не имеет статус 2xx
     */
    protected final <T> T executeGetRequest(UriComponents uriComponents,
                                            ParameterizedTypeReference<T> responseType,
                                            MediaType contentType) {
        var url = uriComponents.toUriString();
        log.debug("Executing request to URL: {}", url);

        var response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                composeHeadersEntity(List.of(contentType)),
                responseType
        );

        if (response.getStatusCode().is2xxSuccessful()) {
            return response.getBody();
        }
        throw new ApiClientException(response);
    }

    /**
     * Выполняет GET-запрос для получения файла (бинарных данных).
     *
     * @param uriComponents объект, содержащий полный URL и параметры запроса
     * @return ResponseEntity с массивом байт и заголовками
     * @throws ApiClientException если ответ сервера не имеет статус 2xx
     */
    protected final ResponseEntity<byte[]> executeGetRequestForFile(UriComponents uriComponents) {
        var url = uriComponents.toUriString();
        log.debug("Executing file request to URL: {}", url);

        var response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                composeFileHeadersEntity(),
                byte[].class
        );

        if (response.getStatusCode().is2xxSuccessful()) {
            return response;
        }
        throw new ApiClientException(response);
    }

    /**
     * Выполняет GET-запрос для получения файла по эндпоинту.
     *
     * @param endpoint относительный путь эндпоинта
     * @param queryParams параметры запроса (может быть {@code null})
     * @return ResponseEntity с массивом байт и заголовками
     * @throws ApiClientException если ответ сервера не имеет статус 2xx
     */
    protected final ResponseEntity<byte[]> executeGetRequestForFile(String endpoint,
                                                                    MultiValueMap<String, String> queryParams) {
        var url = buildUrl(endpoint, queryParams);
        return executeGetRequestForFile(url);
    }

    /**
     * Выполняет GET-запрос для получения файла по эндпоинту без query-параметров.
     *
     * @param endpoint относительный путь эндпоинта
     * @return ResponseEntity с массивом байт и заголовками
     * @throws ApiClientException если ответ сервера не имеет статус 2xx
     */
    protected final ResponseEntity<byte[]> executeGetRequestForFile(String endpoint) {
        return executeGetRequestForFile(endpoint, WITHOUT_QUERY_PARAMS);
    }

    /**
     * Выполняет POST-запрос по указанному URL с произвольным телом.
     *
     * @param url полный URL эндпоинта
     * @param requestEntity HTTP-сущность с телом и заголовками
     * @param responseType тип ожидаемого тела ответа
     * @param <H> тип тела запроса
     * @param <T> тип тела ответа
     * @return тело ответа, десериализованное в указанный тип
     * @throws ApiClientException если ответ сервера не имеет статус 2xx
     */
    protected final <H,T> T executePostRequest(String url,  HttpEntity<H> requestEntity, ParameterizedTypeReference<T> responseType) {
        ResponseEntity<T> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                responseType
        );

        if (response.getStatusCode().is2xxSuccessful()) {
            return response.getBody();
        }
        throw new ApiClientException(response);
    }

    /**
     * Выполняет POST-запрос с типом содержимого {@code multipart/form-data}.
     *
     * @param endpoint относительный путь эндпоинта
     * @param parts части multipart-запроса
     * @param responseType тип ожидаемого тела ответа
     * @param queryParams параметры запроса (может быть {@code null})
     * @param <T> тип возвращаемого объекта
     * @return тело ответа, десериализованное в указанный тип
     * @throws ApiClientException если ответ сервера не имеет статус 2xx
     */
    protected final <T> T executeMultipartPostRequest(
            String endpoint,
            MultiValueMap<String, Object> parts,
            ParameterizedTypeReference<T> responseType,
            MultiValueMap<String, String> queryParams
    ) {
        UriComponents url = buildUrl(endpoint, queryParams);
        HttpHeaders headers = composeJsonHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(parts, headers);

        return executePostRequest(url.toString(), requestEntity, responseType);
    }

    /**
     * Выполняет multipart POST-запрос без query-параметров.
     *
     * @param endpoint относительный путь эндпоинта
     * @param parts части multipart-запроса
     * @param responseType тип ожидаемого тела ответа
     * @param <T> тип возвращаемого объекта
     * @return тело ответа, десериализованное в указанный тип
     * @throws ApiClientException если ответ сервера не имеет статус 2xx
     */
    protected final <T> T executeMultipartPostRequest(
            String endpoint,
            MultiValueMap<String, Object> parts,
            ParameterizedTypeReference<T> responseType
    ) {
        return executeMultipartPostRequest(endpoint, parts, responseType, WITHOUT_QUERY_PARAMS);
    }

    /**
     * Выполняет POST-запрос с JSON-телом.
     *
     * @param endpoint относительный путь эндпоинта
     * @param body тело запроса в формате JSON
     * @param responseType тип ожидаемого тела ответа
     * @param <T> тип возвращаемого объекта
     * @return тело ответа, десериализованное в указанный тип
     * @throws ApiClientException если ответ сервера не имеет статус 2xx
     */
    protected final <T> T executeJsonBodyPostRequest(
            String endpoint,
            JsonNode body,
            ParameterizedTypeReference<T> responseType
    ) {
        UriComponents url = buildUrl(endpoint, WITHOUT_QUERY_PARAMS);
        HttpHeaders headers = composeJsonHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<JsonNode> requestEntity = new HttpEntity<>(body, headers);

        return executePostRequest(url.toUriString(), requestEntity, responseType);
    }
}