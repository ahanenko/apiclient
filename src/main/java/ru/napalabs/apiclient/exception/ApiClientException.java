package ru.napalabs.apiclient.exception;

import lombok.Getter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;

@Getter
public class ApiClientException extends RuntimeException {
    private final ResponseEntity<?> response;

    public ApiClientException(ResponseEntity<?> response) {
        super("API request failed with status: " + response.getStatusCode()
                + " and body: " + response.getBody());
        this.response = response;
    }

    public ApiClientException(ResponseEntity<?> response, RestClientException e) {
        super("API request failed with status: " + response.getStatusCode()
                + " and body: " + response.getBody(), e);
        this.response = response;
    }
}
