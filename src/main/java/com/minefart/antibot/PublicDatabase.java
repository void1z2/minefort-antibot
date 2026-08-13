package com.minefart.antibot;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

final class PublicDatabase {
    private static final Pattern USERNAME = Pattern.compile("^\\+?[A-Za-z0-9_]{1,16}$");
    private final File snapshotFile;

    PublicDatabase(File dataFolder) {
        this.snapshotFile = new File(dataFolder, "database-snapshot.txt");
    }

    Set<String> download(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", "MinefortAntiBot/1.0");
        connection.setUseCaches(false);
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            connection.disconnect();
            throw new IOException("database returned http " + status);
        }
        try {
            return read(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    Set<String> loadSnapshot() throws IOException {
        if (!snapshotFile.isFile()) return new LinkedHashSet<String>();
        return read(new FileInputStream(snapshotFile));
    }

    void saveSnapshot(Set<String> names) throws IOException {
        if (!snapshotFile.getParentFile().exists() && !snapshotFile.getParentFile().mkdirs()) {
            throw new IOException("could not make plugin folder");
        }
        File temporary = new File(snapshotFile.getParentFile(), snapshotFile.getName() + ".tmp");
        Writer writer = new OutputStreamWriter(new FileOutputStream(temporary), StandardCharsets.UTF_8);
        try {
            for (String name : names) {
                writer.write(name);
                writer.write('\n');
            }
        } finally {
            writer.close();
        }
        if (snapshotFile.exists() && !snapshotFile.delete()) throw new IOException("could not replace old snapshot");
        if (!temporary.renameTo(snapshotFile)) throw new IOException("could not save snapshot");
    }

    private Set<String> read(InputStream stream) throws IOException {
        Set<String> names = new LinkedHashSet<String>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                String name = line.trim();
                if (name.isEmpty() || name.startsWith("#") || !USERNAME.matcher(name).matches()) continue;
                names.add(name);
            }
        } finally {
            reader.close();
        }
        return names;
    }
}

