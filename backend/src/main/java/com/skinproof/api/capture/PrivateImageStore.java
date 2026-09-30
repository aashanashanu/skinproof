package com.skinproof.api.capture;

import java.io.IOException;

public interface PrivateImageStore {
    String put(String key, byte[] content, String contentType);

    byte[] get(String key);

    void delete(String key);

    class ImageStoreException extends RuntimeException {
        public ImageStoreException(String message, Throwable cause) {
            super(message, cause);
        }

        public ImageStoreException(String message) {
            super(message);
        }
    }
}