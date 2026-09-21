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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class PublicDatabase {
    private static final Pattern USERNAME = Pattern.compile("^[+.]?[A-Za-z0-9_]{1,16}$");
    private final File snapshotFile;

    PublicDatabase(File dataFolder) {
        this.snapshotFile = new File(dataFolder, "database-snapshot.txt");
    }

    Set<String> download(String address) throws IOException {
        if (address == null || address.trim().isEmpty()) throw new IOException("database url is empty");
        HttpURLConnection con = (HttpURLConnection) new URL(address).openConnection();
        con.setConnectTimeout(10000);
        con.setReadTimeout(15000);
        con.setRequestProperty("User-Agent", "MinefortAntiBot/1.1.1");
        con.setUseCaches(false);
        int status = con.getResponseCode();
        if (status < 200 || status >= 300) {
            con.disconnect();
            throw new IOException("database returned http " + status);
        }
        try {
            Set<String> names = read(con.getInputStream());
            if (names.isEmpty()) throw new IOException("database contained no valid usernames");
            return names;
        } finally {
            con.disconnect();
        }
    }

    Set<String> loadSnapshot() throws IOException {
        if (!snapshotFile.isFile()) return new LinkedHashSet<String>();
        return read(new FileInputStream(snapshotFile));
    }

    void saveSnapshot(Set<String> names) throws IOException {
        if (names == null || names.isEmpty()) throw new IOException("refusing to save empty database");
        if (!snapshotFile.getParentFile().exists() && !snapshotFile.getParentFile().mkdirs()) {
            throw new IOException("could not make plugin folder");
        }
        List<String> sorted = new ArrayList<String>(names);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        File temp = new File(snapshotFile.getParentFile(), snapshotFile.getName() + ".tmp");
        Writer writer = new OutputStreamWriter(new FileOutputStream(temp), StandardCharsets.UTF_8);
        try {
            for (String name : sorted) writer.write(name + '\n');
        } finally {
            writer.close();
        }
        if (snapshotFile.exists() && !snapshotFile.delete()) throw new IOException("could not replace old database cache");
        if (!temp.renameTo(snapshotFile)) throw new IOException("could not save database cache");
    }

    private Set<String> read(InputStream stream) throws IOException {
        Map<String, String> names = new LinkedHashMap<String, String>(); // keeps the first spelling it sees
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || !USERNAME.matcher(line).matches()) continue;
                String id = line.toLowerCase(Locale.ROOT);
                if (!names.containsKey(id)) names.put(id, line);
            }
        } finally {
            reader.close();
        }
        return new LinkedHashSet<String>(names.values());
    }
}
