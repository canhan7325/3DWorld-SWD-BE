package com.project.w_3d_world.features.model.download;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** An already-authorized, already-open stream. The owner must close this handle. */
public final class AuthorizedModelDownload implements AutoCloseable {
    private final InputStream inputStream;
    private final String filename;
    private final String contentType;
    private final Long fileSize;
    private final AtomicBoolean closed = new AtomicBoolean();

    public AuthorizedModelDownload(InputStream inputStream, String filename, String contentType, Long fileSize) {
        this.inputStream = Objects.requireNonNull(inputStream, "inputStream");
        this.filename = Objects.requireNonNull(filename, "filename");
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.fileSize = fileSize;
    }

    public InputStream inputStream() { return inputStream; }
    public String filename() { return filename; }
    public String contentType() { return contentType; }
    public Long fileSize() { return fileSize; }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) inputStream.close();
    }
}
