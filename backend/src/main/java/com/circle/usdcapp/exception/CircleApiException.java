package com.circle.usdcapp.exception;

public class CircleApiException extends RuntimeException {

    private final int statusCode;
    private final String circleErrorCode;

    public CircleApiException(int statusCode, String circleErrorCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.circleErrorCode = circleErrorCode;
    }

    public CircleApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.circleErrorCode = null;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getCircleErrorCode() {
        return circleErrorCode;
    }
}
