package com.example.filemanager.storage;

import io.awspring.cloud.s3.S3Template;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Default backend: any S3-compatible object store (Garage, MinIO, AWS S3). */
@Component
@Profile("!local-storage")
public class S3FileStorage implements FileStorage {

    private final S3Template s3Template;
    private final String bucketName;

    public S3FileStorage(S3Template s3Template, @Value("${S3_BUCKET_NAME:files}") String bucketName) {
        this.s3Template = s3Template;
        this.bucketName = Objects.requireNonNull(bucketName);
    }

    @Override
    public void upload(String key, InputStream content) {
        s3Template.upload(bucketName, Objects.requireNonNull(key), content);
    }

    @Override
    public byte[] download(String key) throws IOException {
        return s3Template.download(bucketName, Objects.requireNonNull(key)).getInputStream().readAllBytes();
    }

    @Override
    public void delete(String key) {
        s3Template.deleteObject(bucketName, Objects.requireNonNull(key));
    }
}
