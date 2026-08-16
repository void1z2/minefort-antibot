package com.minefart.antibot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class UpdateChecker {
    private static final Pattern TAG = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private UpdateChecker() {
    }

    static String latestTag(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "MinefortAntiBot/1.0.2");
        int status = connection.getResponseCode();
        try {
            if (status < 200 || status >= 300) throw new IOException("update check returned http " + status);
            InputStream stream = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
            reader.close();
            Matcher matcher = TAG.matcher(body.toString());
            return matcher.find() ? matcher.group(1) : "";
        } finally {
            connection.disconnect();
        }
    }

    static boolean newerThan(String current, String latest) {
        int[] left = versionParts(current);
        int[] right = versionParts(latest);
        for (int i = 0; i < left.length; i++) {
            if (right[i] != left[i]) return right[i] > left[i];
        }
        return false;
    }

    private static int[] versionParts(String value) {
        Matcher matcher = Pattern.compile("v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?").matcher(String.valueOf(value));
        if (!matcher.find()) return new int[] {0, 0, 0};
        return new int[] {
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3))
        };
    }
}
