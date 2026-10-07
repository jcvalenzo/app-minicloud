package dev.minicloud.files;

/**
 * An upload was refused. The message is the reason only, never a path or client-supplied value.
 */
public class UploadRejectedException extends RuntimeException {

    public enum Reason {
        INVALID_NAME,
        EMPTY,
        TOO_LARGE,
        TYPE_MISMATCH,
        EXECUTABLE,
        INFECTED,
        SCAN_ERROR,
        INSUFFICIENT_SPACE
    }

    private final Reason reason;

    public UploadRejectedException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public UploadRejectedException(Reason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
