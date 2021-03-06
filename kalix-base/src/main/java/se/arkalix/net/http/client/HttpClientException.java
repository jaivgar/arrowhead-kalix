package se.arkalix.net.http.client;

/**
 * Signifies that some HTTP client action related to a
 * {@link HttpClientConnection} failed.
 * <p>
 * As these exceptions are expected to be quite common, and are caused by
 * external rather than internal mistakes, <i>they do not produce stack
 * traces</i>. If an HTTP response causes an error that should generate a stack
 * trace, some other exception type should be used instead.
 */
public class HttpClientException extends RuntimeException {
    /**
     * Creates new HTTP client exception with given message.
     *
     * @param message Human-readable description of issue.
     */
    public HttpClientException(final String message) {
        super(message, null, true, false); // Disable stack trace.
    }

    /**
     * Creates new HTTP client exception with given message.
     *
     * @param message Human-readable description of issue.
     * @param cause   Exception causing this exception to be thrown.
     */
    public HttpClientException(final String message, final Throwable cause) {
        super(message, cause, true, false); // Disable stack trace.
    }
}
