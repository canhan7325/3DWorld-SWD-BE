package com.project.w_3d_world.features.model.exception;

import com.project.w_3d_world.common.exception.ApiException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import org.springframework.http.HttpStatus;

import static com.project.w_3d_world.features.model.exception.ModelStorageException.Code.*;

public final class ModelErrors {
    private ModelErrors() {}

    public static ApiException modelOrVersionNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Model or version was not found.");
    }

    public static ApiException modelFileNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Model file was not found.");
    }

    public static ModelStorageException invalidContent() {
        return new ModelStorageException(INVALID_CONTENT, "File content is malformed or unsupported.");
    }

    public static ModelStorageException unsafeStoragePath() {
        return new ModelStorageException(UNSAFE_PATH,
                "Storage path is not a safe managed directory or regular file.");
    }

    public static ModelStorageException storageFailure(Exception exception) {
        if (exception instanceof ModelStorageException storage) return storage;
        if (exception instanceof AtomicMoveNotSupportedException) {
            return new ModelStorageException(ATOMIC_MOVE_UNSUPPORTED,
                    "Storage filesystem must support atomic moves.", exception);
        }
        if (exception instanceof NoSuchFileException) {
            return new ModelStorageException(NOT_FOUND,
                    "Stored file or managed directory was not found.", exception);
        }
        if (exception instanceof FileAlreadyExistsException) {
            return new ModelStorageException(COLLISION,
                    "Storage destination already exists.", exception);
        }
        return new ModelStorageException(IO_FAILURE, "Local storage operation failed.", exception);
    }
}
