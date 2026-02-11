package ee.buerokratt.email.service.provider;

/**
 * Exception thrown when email sending fails.
 */
public class EmailException extends Exception {

    private final String provider;
    private final String errorCode;

    public EmailException(String message) {
        super(message);
        this.provider = null;
        this.errorCode = null;
    }

    public EmailException(String message, Throwable cause) {
        super(message, cause);
        this.provider = null;
        this.errorCode = null;
    }

    public EmailException(String message, String provider, String errorCode) {
        super(message);
        this.provider = provider;
        this.errorCode = errorCode;
    }

    public EmailException(String message, Throwable cause, String provider) {
        super(message, cause);
        this.provider = provider;
        this.errorCode = null;
    }

    public String getProvider() {
        return provider;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isRetryable() {
        // Determine if error is retryable based on error code
        if (errorCode == null) {
            return true;
        }

        // Common non-retryable error codes
        return !errorCode.matches("^(400|401|403|404)$");
    }
}
