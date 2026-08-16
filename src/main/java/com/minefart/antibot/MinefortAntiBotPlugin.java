package com.minefart.antibot;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MinefortAntiBotPlugin extends JavaPlugin {
    private static final int BSTATS_PLUGIN_ID = 33379;
    private static final String PLUGIN_VERSION = "1.0.2";
    private static final String UPDATE_API = "https://api.github.com/repos/void1z2/minefort-antibot/releases/latest";
    private static final String RELEASES_URL = "https://github.com/void1z2/minefort-antibot/releases";
    private final AtomicBoolean syncing = new AtomicBoolean(false);
    private PublicDatabase database;
    private BanCommandBuilder.Mode banMode = BanCommandBuilder.Mode.NORMAL;
    private BukkitTask updateTask;
    private BukkitTask updateCheckTask;
    private volatile int databaseSize;
    private volatile long lastUpdate;
    private volatile String lastError = "none";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        database = new PublicDatabase(getDataFolder());
        detectBanPlugin();
        startMetrics();

        long minutes = Math.max(1L, getConfig().getLong("update-minutes", 5L));
        updateTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                syncDatabase();
            }
        }, 20L, minutes * 60L * 20L);
        if (getConfig().getBoolean("check-updates", true)) {
            long hours = Math.max(1L, getConfig().getLong("update-check-hours", 12L));
            updateCheckTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
                @Override
                public void run() {
                    checkForPluginUpdate();
                }
            }, 40L, hours * 60L * 60L * 20L);
        }
        getLogger().info("enabled. ban mode: " + banMode.name().toLowerCase() + ", checking every " + minutes + "m");
    }

    private void startMetrics() {
        new Metrics(this, BSTATS_PLUGIN_ID);
        getLogger().info("bStats enabled");
    }

    @Override
    public void onDisable() {
        if (updateTask != null) updateTask.cancel();
        if (updateCheckTask != null) updateCheckTask.cancel();
    }

    private void checkForPluginUpdate() {
        try {
            String latest = UpdateChecker.latestTag(UPDATE_API);
            if (UpdateChecker.newerThan(PLUGIN_VERSION, latest)) {
                getLogger().warning("update available: " + latest + " (running " + PLUGIN_VERSION + ")");
                getLogger().warning("download: " + RELEASES_URL);
            }
        } catch (IOException error) {
            getLogger().fine("update check failed: " + error.getMessage());
        }
    }

    private void detectBanPlugin() {
        Set<String> plugins = new HashSet<String>();
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) plugins.add(plugin.getName());
        banMode = BanCommandBuilder.detect(plugins);
        if (banMode == BanCommandBuilder.Mode.NORMAL) {
            if (plugins.contains("BanManager")) getLogger().info("found BanManager, using normal /ban");
            else if (plugins.contains("Essentials")) getLogger().info("found Essentials, using normal /ban");
            else getLogger().warning("no known ban plugin found, trying the server /ban command");
        } else {
            getLogger().info("found " + banMode.name() + ", silent bans are supported");
        }
    }

    private void syncDatabase() {
        if (!syncing.compareAndSet(false, true)) return;
        try {
            String url = getConfig().getString("database-url", "");
            Set<DatabaseEntry> downloaded = database.download(url);
            Set<DatabaseEntry> old = database.loadSnapshot();
            Set<DatabaseEntry> added = new LinkedHashSet<DatabaseEntry>(downloaded);
            added.removeAll(old);
            database.saveSnapshot(downloaded);
            String legacyUrl = getConfig().getString("legacy-database-url", "");
            Set<String> legacy;
            if (legacyUrl == null || legacyUrl.trim().isEmpty()) {
                legacy = database.loadLegacySnapshot();
            } else {
                try {
                    legacy = database.downloadLegacyNames(legacyUrl);
                } catch (IOException error) {
                    getLogger().warning("legacy database check failed: " + error.getMessage());
                    legacy = database.loadLegacySnapshot();
                }
            }
            Set<String> oldLegacy = database.loadLegacySnapshot();
            Set<String> addedLegacy = new LinkedHashSet<String>(legacy);
            addedLegacy.removeAll(oldLegacy);
            database.saveLegacySnapshot(legacy);
            for (DatabaseEntry entry : added) {
                if (isLegacyName(entry.name) && !entry.name.isEmpty()) addedLegacy.add(entry.name);
            }
            databaseSize = downloaded.size() + legacy.size();
            lastUpdate = System.currentTimeMillis();
            lastError = "none";
            if (added.isEmpty() && addedLegacy.isEmpty()) return;
            int uuidCount = 0;
            List<DatabaseEntry> uuidEntries = new ArrayList<DatabaseEntry>();
            for (DatabaseEntry entry : added) {
                if (!isLegacyName(entry.name)) {
                    uuidEntries.add(entry);
                    uuidCount++;
                }
            }
            getLogger().info("database updated, " + uuidCount + " uuid account(s), " + addedLegacy.size() + " legacy name(s)");
            queueBans(uuidEntries);
            queueUsernames(new ArrayList<String>(addedLegacy));
        } catch (IOException error) {
            lastError = error.getMessage();
            getLogger().warning("database check failed: " + error.getMessage());
        } finally {
            syncing.set(false);
        }
    }

    private void queueBans(final List<DatabaseEntry> entries) {
        List<String> targets = new ArrayList<String>();
        for (DatabaseEntry entry : entries) {
            if (isLegacyName(entry.name)) targets.add(entry.name);
            else targets.add(entry.uuid.toString());
        }
        queueTargets(targets);
    }

    private void queueUsernames(List<String> names) {
        queueTargets(names);
    }

    private void queueTargets(final List<String> targets) {
        final int delay = Math.max(1, getConfig().getInt("ticks-between-bans", 4));
        final String reason = getConfig().getString("ban-reason", "Bot Account");
        final boolean silent = getConfig().getBoolean("silent-when-supported", true);
        for (int i = 0; i < targets.size(); i++) {
            final String targetName = targets.get(i);
            Bukkit.getScheduler().runTaskLater(this, new Runnable() {
                @Override
                public void run() {
                    String command = BanCommandBuilder.build(banMode, targetName, reason, silent);
                    if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                        getLogger().warning("command failed for " + targetName + ": /" + command);
                    }
                }
            }, (long) i * delay);
        }
    }

    private boolean isLegacyName(String name) {
        return name != null && (name.startsWith("+") || name.startsWith("."));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(ChatColor.GRAY + "MinefortAntiBot: " + databaseSize + " accounts, " +
                    banMode.name().toLowerCase() + " mode, last error: " + lastError);
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("minefortantibot.admin")) {
                sender.sendMessage(ChatColor.RED + "no permission");
                return true;
            }
            reloadConfig();
            detectBanPlugin();
            Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
                @Override
                public void run() {
                    syncDatabase();
                }
            });
            sender.sendMessage(ChatColor.GRAY + "checking database");
            return true;
        }
        sender.sendMessage(ChatColor.GRAY + "/" + label + " [status|reload]");
        return true;
    }
}

