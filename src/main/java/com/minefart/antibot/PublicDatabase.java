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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

final class PublicDatabase {
    private static final Pattern USERNAME = Pattern.compile("^\\+?[A-Za-z0-9_]{1,16}$");
    private final File snapshotFile;

    PublicDatabase(File dataFolder) {
        this.snapshotFile = new File(dataFolder, "databasev2-snapshot.txt");
    }

    Set<DatabaseEntry> download(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", "MinefortAntiBot/1.1");
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

    Set<DatabaseEntry> loadSnapshot() throws IOException {
        if (!snapshotFile.isFile()) return new LinkedHashSet<DatabaseEntry>();
        return read(new FileInputStream(snapshotFile));
    }

    void saveSnapshot(Set<DatabaseEntry> entries) throws IOException {
        if (!snapshotFile.getParentFile().exists() && !snapshotFile.getParentFile().mkdirs()) {
            throw new IOException("could not make plugin folder");
        }
        File temporary = new File(snapshotFile.getParentFile(), snapshotFile.getName() + ".tmp");
        Writer writer = new OutputStreamWriter(new FileOutputStream(temporary), StandardCharsets.UTF_8);
        try {
            for (DatabaseEntry entry : entries) {
                writer.write(entry.uuid.toString());
                if (!entry.name.isEmpty()) {
                    writer.write('\t');
                    writer.write(entry.name);
                }
                writer.write('\n');
            }
        } finally {
            writer.close();
        }
        if (snapshotFile.exists() && !snapshotFile.delete()) throw new IOException("could not replace old snapshot");
        if (!temporary.renameTo(snapshotFile)) throw new IOException("could not save snapshot");
    }

    private Set<DatabaseEntry> read(InputStream stream) throws IOException {
        Map<UUID, DatabaseEntry> entries = new LinkedHashMap<UUID, DatabaseEntry>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] columns = line.split("\\s+", 2);
                UUID uuid;
                try {
                    uuid = UUID.fromString(columns[0]);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                String name = columns.length > 1 ? columns[1].trim() : "";
                if (!name.isEmpty() && !USERNAME.matcher(name).matches()) name = "";
                if (!entries.containsKey(uuid)) entries.put(uuid, new DatabaseEntry(uuid, name));
            }
        } finally {
            reader.close();
        }
        return new LinkedHashSet<DatabaseEntry>(entries.values());
    }
}
