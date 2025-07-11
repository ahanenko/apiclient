package ru.napalabs.apiclient.client;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
public abstract class AbstractApiClient {
    protected final RestTemplate restTemplate;
    protected final AuthContext authContext;
    protected final String baseUrl;
    // Константа для вызова ендпойнта без параметров
    public static final MultiValueMap<String, String> WITHOUT_QUERY_PARAMS = null;

    protected AbstractApiClient(RestTemplate restTemplate, AuthContext authContext, String baseUrl) {
        this.restTemplate = restTemplate;
        this.authContext = authContext;
        this.baseUrl = baseUrl;
    }

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

    protected final void validateConfig(String... endpoints) {
        for (String endpoint : endpoints) {
            if (endpoint == null || endpoint.isBlank()) {
                throw new IllegalStateException("Endpoint is not set for " + getClass().getSimpleName());
            }
        }
    }

    protected final UriComponents buildUrl(String endpoint, MultiValueMap<String, String> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path(endpoint);
        if (queryParams != null && !queryParams.isEmpty()) {
            builder.queryParams(queryParams);
        }
        return builder.build();
    }

    private HttpEntity<?> composeHeadersEntity() {
        return new HttpEntity<>(composeHeaders());
    }

    private HttpHeaders composeHeaders() {
        var headers = new HttpHeaders();
        if (authContext != null && authContext.getToken() != null && !authContext.getToken().isBlank()) {
            headers.setBearerAuth(authContext.getToken());
        }
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        return headers;
    }

    protected final <T> T executeGetRequest(String endpoint,
                                            ParameterizedTypeReference<T> responseType,
                                            MultiValueMap<String, String> queryParams) {
        var url = buildUrl(endpoint, queryParams);

        return executeGetRequest(url, responseType);
    }

    protected final <T> T executeGetRequest(String endpoint,
                                            ParameterizedTypeReference<T> responseType) {
        return executeGetRequest(endpoint, responseType, WITHOUT_QUERY_PARAMS);
    }

    protected final <T> T executeGetRequest(UriComponents uriComponents,
                                            ParameterizedTypeReference<T> responseType)  {
        var url = uriComponents.toUriString();

        log.debug("Executing request to URL: {}", url);
        var response = restTemplate.exchange(url, HttpMethod.GET, composeHeadersEntity(), responseType);
        if (response.getStatusCode().is2xxSuccessful()) {
            return response.getBody();
        }
        throw new ApiClientException(response);
    }

    protected final <T> T executeMultipartPostRequest(
            String endpoint,
            MultiValueMap<String, Object> parts,
            ParameterizedTypeReference<T> responseType,
            MultiValueMap<String, String> queryParams
    ) {
        UriComponents url = buildUrl(endpoint, queryParams);
        HttpHeaders headers = composeHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(parts, headers);

        ResponseEntity<T> response = restTemplate.exchange(
                url.toUriString(),
                HttpMethod.POST,
                requestEntity,
                responseType
        );

        if (response.getStatusCode().is2xxSuccessful()) {
            return response.getBody();
        }
        throw new ApiClientException(response);
    }

    protected final <T> T executeMultipartPostRequest(
            String endpoint,
            MultiValueMap<String, Object> parts,
            ParameterizedTypeReference<T> responseType
    ) {
        return executeMultipartPostRequest(endpoint, parts, responseType, WITHOUT_QUERY_PARAMS);
    }
}
