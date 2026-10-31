package com.antigravity.mes.ai;

/** A failed call to a provider. {@code status} is the HTTP status, or 0 when no response arrived
 * (timeout, connection reset). Extends IllegalStateException so existing handlers still map it. */
public class AiProviderException extends IllegalStateException {

    private final int status;

    public AiProviderException(String message, int status) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    /** Worth retrying on the same model: no response, rate limited, or the provider is overloaded. */
    public boolean isTransient() {
        return status == 0 || status == 429 || status >= 500;
    }

    /** The key itself was refused. Switching model will not help, so this is not retried on another model. */
    public boolean isKeyRejected() {
        return status == 401 || status == 403;
    }
}
