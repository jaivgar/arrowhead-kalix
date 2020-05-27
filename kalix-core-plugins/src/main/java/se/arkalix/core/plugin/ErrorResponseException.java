package se.arkalix.core.plugin;

import se.arkalix.net.http.client.HttpClientRequest;
import se.arkalix.net.http.client.HttpClientResponse;
import se.arkalix.net.http.client.HttpClientResponseRejectedException;

/**
 * Some error caused by a core service responding to a request with an {@link
 * ErrorResponse}.
 */
public class ErrorResponseException extends HttpClientResponseRejectedException {
    private final ErrorResponse error;

    /**
     * Creates new HTTP response containing given {@code error}.
     *
     * @param error Core service error to include in exception.
     */
    public ErrorResponseException(final HttpClientResponse response, final ErrorResponse error) {
        super(response, error.type() + " [" + error.code() + "]: " + error.message());
        this.error = error;
    }

    /**
     * @return Error causing this exception to be thrown.
     */
    public ErrorResponse error() {
        return error;
    }
}
