package com.example.BackendArchitectureLab.Util;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class CallApiTest {

    private HttpServer server;
    private int port;
    private CallApi callApi;

    @BeforeEach
    void setUp() throws IOException {
        callApi = new CallApi();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void get_whenSuccessResponse_shouldReturnResponseBody() throws IOException {
        server.createContext("/api/success", exchange -> {
            byte[] response = "{\"status\":\"ok\"}".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            OutputStream os = exchange.getResponseBody();
            os.write(response);
            os.close();
        });

        String result = callApi.get("http://localhost:" + port + "/api/success");
        assertEquals("{\"status\":\"ok\"}", result);
    }

    @Test
    void get_whenErrorResponse_shouldReadErrorStream() throws IOException {
        server.createContext("/api/error", exchange -> {
            byte[] response = "{\"error\":\"not found\"}".getBytes();
            exchange.sendResponseHeaders(404, response.length);
            OutputStream os = exchange.getResponseBody();
            os.write(response);
            os.close();
        });

        String result = callApi.get("http://localhost:" + port + "/api/error");
        assertEquals("{\"error\":\"not found\"}", result);
    }
}
