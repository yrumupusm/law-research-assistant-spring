package com.example.lawassistant.infrastructure.openrouter;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import javax.net.ssl.SSLException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

final class OpenRouterFailureCode {

    private OpenRouterFailureCode() {
    }

    static String from(RestClientException exception) {
        if (exception instanceof RestClientResponseException responseException) {
            return "provider_http_" + responseException.getStatusCode().value();
        }
        if (exception instanceof ResourceAccessException && hasCause(exception, SocketTimeoutException.class, HttpTimeoutException.class)) {
            return "provider_timeout";
        }
        if (exception instanceof ResourceAccessException
                && hasCause(exception, ConnectException.class, UnknownHostException.class, SSLException.class)) {
            return "provider_connection_failed";
        }
        return "provider_request_failed";
    }

    @SafeVarargs
    private static boolean hasCause(Throwable exception, Class<? extends Throwable>... causeTypes) {
        Throwable current = exception;
        while (current != null) {
            for (Class<? extends Throwable> causeType : causeTypes) {
                if (causeType.isInstance(current)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
