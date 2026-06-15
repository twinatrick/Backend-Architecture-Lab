package com.example.BackendArchitectureLab.Service;

import java.io.IOException;

public interface IApiFetcher {
    String get(String url) throws IOException;
}
