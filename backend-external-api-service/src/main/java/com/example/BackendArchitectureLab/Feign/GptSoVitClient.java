package com.example.BackendArchitectureLab.Feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "gpt-sovit-client", url = "http://127.0.0.1:9880")
public interface GptSoVitClient {

    @PostMapping(value = "/tts", consumes = MediaType.APPLICATION_JSON_VALUE)
    byte[] synthesize(@RequestBody Map<String, Object> request);
}
