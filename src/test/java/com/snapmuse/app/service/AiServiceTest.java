package com.snapmuse.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snapmuse.app.model.ApiEndpointConfig;
import com.snapmuse.app.model.AppConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiServiceTest {

    private HttpServer firstServer;
    private HttpServer secondServer;

    @AfterEach
    void tearDown() {
        if (firstServer != null) {
            firstServer.stop(0);
        }
        if (secondServer != null) {
            secondServer.stop(0);
        }
    }

    @Test
    void fallsBackToNextApiWhenFirstEndpointFails() throws Exception {
        firstServer = startServer(0, 500, """
                {"error":{"message":"first endpoint failed"}}
                """);
        secondServer = startServer(0, 200, """
                {"choices":[{"message":{"content":"second endpoint answer"}}]}
                """);

        AppConfig config = new AppConfig();
        config.setApiConfigs(List.of(
                new ApiEndpointConfig("主接口", "http://127.0.0.1:" + firstServer.getAddress().getPort(), "key-1", "model-1"),
                new ApiEndpointConfig("兜底接口 1", "http://127.0.0.1:" + secondServer.getAddress().getPort(), "key-2", "model-2")
        ));
        config.setSystemPrompt("test prompt");

        AiService aiService = new AiService(stubConfigService(config), new ObjectMapper());

        String answer = aiService.ask("hello", null);

        assertEquals("second endpoint answer", answer);
    }

    private ConfigService stubConfigService(AppConfig config) {
        return new ConfigService(new ObjectMapper()) {
            @Override
            public AppConfig getConfig() {
                return config;
            }
        };
    }

    private HttpServer startServer(int port, int statusCode, String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/chat/completions", exchange -> respond(exchange, statusCode, responseBody));
        server.start();
        return server;
    }

    private void respond(HttpExchange exchange, int statusCode, String responseBody) throws IOException {
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        } finally {
            exchange.close();
        }
    }
}
