package com.minefart.antibot;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class V3ReporterTest {
    @Test
    public void pluginLinksItselfAndKeepsTheSigningKeyOnDisk() throws Exception {
        final AtomicReference<String> body = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/link", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                body.set(read(exchange.getRequestBody()));
                byte[] response = ("{\"serverId\":\"4dd78e3f-cbd1-43d0-84d0-3cb109de85d4\","
                        + "\"secret\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=\"}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            }
        });
        server.start();

        try {
            File folder = Files.createTempDirectory("mab-link-").toFile();
            YamlConfiguration config = new YamlConfiguration();
            config.set("v3.base-url", "http://127.0.0.1:" + server.getAddress().getPort());
            config.set("v3.allow-http", true);
            V3Reporter reporter = new V3Reporter(folder, config, Logger.getAnonymousLogger(), "1.1.0");

            assertEquals("linked to dueled | signed reports enabled", reporter.link("Dueled", "KIXAE123"));
            assertTrue(reporter.isLinked());
            assertEquals("dueled", reporter.linkedServerName());
            assertEquals("server=dueled&code=KIXAE123", body.get());

            Properties saved = new Properties();
            InputStream input = Files.newInputStream(new File(folder, "v3-link.properties").toPath());
            try {
                saved.load(input);
            } finally {
                input.close();
            }
            assertEquals("dueled", saved.getProperty("serverName"));
            assertEquals("4dd78e3f-cbd1-43d0-84d0-3cb109de85d4", saved.getProperty("serverId"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void pluginRejectsBadServerNamesBeforeMakingARequest() throws Exception {
        File folder = Files.createTempDirectory("mab-link-invalid-").toFile();
        YamlConfiguration config = new YamlConfiguration();
        config.set("v3.base-url", "https://minef.art");
        V3Reporter reporter = new V3Reporter(folder, config, Logger.getAnonymousLogger(), "1.1.0");
        assertEquals("link failed | invalid server name", reporter.link("not a server", "KIXAE123"));
        assertEquals("link failed | invalid Kixae verification code", reporter.link("dueled", "no"));
    }

    private static String read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
