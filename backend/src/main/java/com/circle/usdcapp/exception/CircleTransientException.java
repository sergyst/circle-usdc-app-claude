package com.circle.usdcapp.exception;

/**
 * A Circle call that failed for a reason worth retrying: a network/timeout
 * error, or a 5xx from Circle. Client errors (4xx) are NOT transient - they
 * won't succeed on retry - so those stay as plain {@link CircleApiException}.
 *
 * <p>Resilience4j's retry for the "circleApi" instance is configured (in
 * application.yml) to retry only on this type, so 4xx responses fail fast.
 */
public class CircleTransientException extends CircleApiException {

    public CircleTransientException(int statusCode, String circleErrorCode, String message) {
        super(statusCode, circleErrorCode, message);
    }

    public CircleTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
