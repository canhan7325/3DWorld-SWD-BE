package com.project.w_3d_world.features.model.exception;

/** Safe public messages; causes/suppressed exceptions are for internal diagnostics only. */
public class ModelStorageException extends RuntimeException {
    public enum Code {
        CONFIGURATION, INVALID_METADATA, INVALID_KEY, EMPTY_FILE, TOO_LARGE, INVALID_CONTENT,
        UNSAFE_PATH, NOT_FOUND, COLLISION, ATOMIC_MOVE_UNSUPPORTED, IO_FAILURE, CLEANUP_FAILURE
    }

    private final Code code;

    public ModelStorageException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public ModelStorageException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
