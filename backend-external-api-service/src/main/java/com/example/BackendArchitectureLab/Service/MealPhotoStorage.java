package com.example.BackendArchitectureLab.Service;

import java.io.InputStream;
import java.nio.file.Path;

public interface MealPhotoStorage {
    void put(String objectKey, Path file, long size, String contentType);
    InputStream open(String objectKey);
    void delete(String objectKey);
}
