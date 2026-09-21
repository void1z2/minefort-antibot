package com.minefart.antibot;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;

import static org.junit.Assert.assertTrue;

public class PublicDatabaseCompatibilityTest {
    @Test
    public void oldV102CachesStillProvideKnownNamesAfterUpgrade() throws Exception {
        File folder = Files.createTempDirectory("mab-old-cache-").toFile();
        Files.write(new File(folder, "databasev2-snapshot.txt").toPath(),
                "123e4567-e89b-12d3-a456-426614174000 NormalName\n".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(folder, "database-legacy-snapshot.txt").toPath(),
                "+CrackedName\n.BedrockName\n".getBytes(StandardCharsets.UTF_8));

        Set<String> names = new PublicDatabase(folder).loadSnapshot();
        assertTrue(names.contains("NormalName"));
        assertTrue(names.contains("+CrackedName"));
        assertTrue(names.contains(".BedrockName"));
    }
}
