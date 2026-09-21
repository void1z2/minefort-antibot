package com.minefart.antibot;

import org.bukkit.configuration.file.FileConfiguration;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

final class V3Reporter {
    private final File linkedFile;
    private final Logger logger;
    private final String baseUrl;
    private final String pluginVersion;
    private final boolean allowHttp;
    private volatile String serverName;
    private volatile String serverId;
    private volatile String secret;

    V3Reporter(File dataFolder, FileConfiguration config, Logger logger, String pluginVersion) {
        this.linkedFile = new File(dataFolder, "v3-link.properties");
        this.logger = logger;
        this.baseUrl = trimSlash(config.getString("v3.base-url", ""));
        this.serverName = config.getString("v3.server-name", "").trim().toLowerCase();
        this.pluginVersion = pluginVersion;
        this.allowHttp = config.getBoolean("v3.allow-http", false);
        loadLink();
    }

    synchronized String link(String requestedServer, String verificationCode) {
        String requested = String.valueOf(requestedServer == null ? "" : requestedServer).trim().toLowerCase();
        String code = String.valueOf(verificationCode == null ? "" : verificationCode).trim().toUpperCase();
        if (!requested.matches("^[a-z0-9_-]{1,64}$")) return "link failed | invalid server name";
        if (!code.matches("^[A-Z0-9_-]{6,32}$")) return "link failed | invalid Kixae verification code";
        if (isLinked() && requested.equals(serverName)) return "already linked to " + serverName;
        if (empty(baseUrl)) return "link failed | reporting url is missing";
        if (!validTransport()) {
            return "link failed | reporting url must use https";
        }
        HttpURLConnection connection = null;
        try {
            byte[] body = ("server=" + encode(requested) + "&code=" + encode(code)).getBytes(StandardCharsets.UTF_8);
            connection = open("/v1/link", "application/x-www-form-urlencoded", body.length);
            try (OutputStream output = connection.getOutputStream()) { output.write(body); }
            int status = connection.getResponseCode();
            String response = readResponse(connection, status);
            if (status < 200 || status >= 300) throw new IOException("link returned http " + status + " | " + response);
            String nextServerId = jsonValue(response, "serverId");
            String nextSecret = jsonValue(response, "secret");
            if (empty(nextServerId) || empty(nextSecret)) throw new IOException("link response was invalid");
            serverName = requested;
            serverId = nextServerId;
            secret = nextSecret;
            saveLink();
            logger.info("v3 linked | " + serverName + " | reports enabled");
            return "linked to " + serverName + " | signed reports enabled";
        } catch (Exception error) {
            logger.warning("v3 link failed | " + error.getMessage());
            return "link failed | " + error.getMessage();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    void reportRaid(Set<String> names, long startedAt, long endedAt, int databaseSize) {
        if (!isLinked() || !validTransport()) return;
        String nonce = UUID.randomUUID().toString().replace("-", "");
        long timestamp = System.currentTimeMillis();
        String body = "{\"type\":\"known_raid\",\"server\":\"" + json(serverName)
                + "\",\"startedAt\":" + startedAt + ",\"endedAt\":" + endedAt
                + ",\"databaseSize\":" + databaseSize + ",\"pluginVersion\":\"" + json(pluginVersion)
                + "\",\"usernames\":[" + jsonNames(names) + "]}";
        HttpURLConnection connection = null;
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection = open("/v1/events", "application/json", bytes.length);
            connection.setRequestProperty("X-Mab-Server", serverId);
            connection.setRequestProperty("X-Mab-Timestamp", String.valueOf(timestamp));
            connection.setRequestProperty("X-Mab-Nonce", nonce);
            connection.setRequestProperty("X-Mab-Signature", hmac(serverId + "\n" + timestamp + "\n" + nonce + "\n" + body));
            try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                logger.warning("v3 report failed | http " + status + " | " + readResponse(connection, status));
            }
        } catch (Exception error) {
            logger.warning("v3 report failed | " + error.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    boolean isLinked() {
        return !empty(serverId) && !empty(secret);
    }

    String linkedServerName() {
        return isLinked() ? serverName : "unlinked";
    }

    private HttpURLConnection open(String path, String contentType, int length) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", contentType);
        connection.setRequestProperty("Content-Length", String.valueOf(length));
        connection.setRequestProperty("User-Agent", "MinefortAntiBot/" + pluginVersion);
        connection.setDoOutput(true);
        return connection;
    }

    private boolean validTransport() {
        return baseUrl.startsWith("https://") || (allowHttp && baseUrl.startsWith("http://"));
    }

    private String hmac(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256"));
        return hex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private void loadLink() {
        if (!linkedFile.isFile()) return;
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(linkedFile)) {
            properties.load(input);
            serverName = properties.getProperty("serverName", serverName).trim().toLowerCase();
            serverId = properties.getProperty("serverId", "").trim();
            secret = properties.getProperty("secret", "").trim();
        } catch (IOException error) {
            logger.warning("v3 link cache failed | " + error.getMessage());
        }
    }

    private void saveLink() throws IOException {
        File parent = linkedFile.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) throw new IOException("could not create plugin folder");
        Properties properties = new Properties();
        properties.setProperty("serverName", serverName);
        properties.setProperty("serverId", serverId);
        properties.setProperty("secret", secret);
        try (FileOutputStream output = new FileOutputStream(linkedFile)) {
            properties.store(output, "minefart antibot v3 link | keep private");
        }
    }

    private String readResponse(HttpURLConnection connection, int status) throws IOException {
        if (status >= 400 && connection.getErrorStream() == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                status >= 400 ? connection.getErrorStream() : connection.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder value = new StringBuilder();
        try {
            String line;
            while ((line = reader.readLine()) != null) value.append(line);
        } finally {
            reader.close();
        }
        return value.toString();
    }

    private String jsonNames(Set<String> names) {
        StringBuilder value = new StringBuilder();
        for (String name : names) {
            if (value.length() > 0) value.append(',');
            value.append('"').append(json(name)).append('"');
        }
        return value.toString();
    }

    private String jsonValue(String body, String key) {
        String marker = "\"" + key + "\":\"";
        int start = body.indexOf(marker);
        if (start < 0) return "";
        start += marker.length();
        int end = body.indexOf('"', start);
        if (end < 0) return "";
        return body.substring(start, end);
    }

    private String json(String value) {
        return String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private String encode(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8");
    }

    private String trimSlash(String value) {
        String clean = String.valueOf(value == null ? "" : value).trim();
        while (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
        return clean;
    }

    private boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) value.append(String.format("%02x", current & 0xff));
        return value.toString();
    }
}
