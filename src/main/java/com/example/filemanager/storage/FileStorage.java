package com.example.filemanager.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Where file contents live, separate from the metadata in the database.
 *
 * <p>
 * The service layer talked to {@code S3Template} directly, which meant every
 * test had to mock the AWS SDK and the end-to-end suite needed a running
 * S3-compatible server. Behind this interface the storage backend is a
 * deployment choice.
 */
public interface FileStorage {

    /** Stores {@code content} under {@code key}, replacing anything already there. */
    void upload(String key, InputStream content) throws IOException;

    /** Reads the object stored under {@code key}. */
    byte[] download(String key) throws IOException;

    /** Removes the object under {@code key}. Missing objects are not an error. */
    void delete(String key);
}
