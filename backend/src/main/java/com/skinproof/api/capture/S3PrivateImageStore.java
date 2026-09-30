package com.skinproof.api.capture;

import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class S3PrivateImageStore implements PrivateImageStore {
    private final S3Client s3;
    private final String bucket;

    public S3PrivateImageStore(S3Client s3, @Value("${skinproof.storage.image-bucket:}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public String put(String key, byte[] content, String contentType) {
        requireBucket();
        try {
            s3.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .serverSideEncryption("AES256")
                    .metadata(Map.of("privacy", "skinproof-private"))
                    .build(), RequestBody.fromBytes(content));
            return key;
        } catch (Exception exception) {
            throw new ImageStoreException("Unable to store private capture image.", exception);
        }
    }

    @Override
    public byte[] get(String key) {
        requireBucket();
        try {
            ResponseBytes<GetObjectResponse> object = s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            return object.asByteArray();
        } catch (Exception exception) {
            throw new ImageStoreException("Unable to read private capture image.", exception);
        }
    }

    @Override
    public void delete(String key) {
        requireBucket();
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (Exception exception) {
            throw new ImageStoreException("Unable to delete private capture image.", exception);
        }
    }

    private void requireBucket() {
        if (bucket.isBlank()) {
            throw new ImageStoreException("Private image storage is not configured.");
        }
    }
}