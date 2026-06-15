package com.example.BackendArchitectureLab.Service;

import java.io.IOException;

public interface ICheckApiService {
    String getApiOnlyUrl(String url) throws IOException;

    void getAquarkApiData() throws IOException;
}
