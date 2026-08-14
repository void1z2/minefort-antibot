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
    private final AtomicBoolean syncing = new AtomicBoolean(false);
    private PublicDatabase database;
    private BanCommandBuilder.Mode banMode = BanCommandBuilder.Mode.NORMAL;
    private BukkitTask updateTask;
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
        getLogger().info("enabled. ban mode: " + banMode.name().toLowerCase() + ", checking every " + minutes + "m");
    }

    private void startMetrics() {
        new Metrics(this, BSTATS_PLUGIN_ID);
        getLogger().info("bStats enabled");
    }

    @Override
    public void onDisable() {
        if (updateTask != null) updateTask.cancel();
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
            Set<String> downloaded = database.download(url);
            Set<String> old = database.loadSnapshot();
            Set<String> added = new LinkedHashSet<String>(downloaded);
            added.removeAll(old);
            database.saveSnapshot(downloaded);
            databaseSize = downloaded.size();
            lastUpdate = System.currentTimeMillis();
            lastError = "none";
            if (added.isEmpty()) return;
            getLogger().info("database updated, " + added.size() + " new account(s)");
            queueBans(new ArrayList<String>(added));
        } catch (IOException error) {
            lastError = error.getMessage();
            getLogger().warning("database check failed: " + error.getMessage());
        } finally {
            syncing.set(false);
        }
    }

    private void queueBans(final List<String> names) {
        final int delay = Math.max(1, getConfig().getInt("ticks-between-bans", 4));
        final String reason = getConfig().getString("ban-reason", "Bot Account");
        final boolean silent = getConfig().getBoolean("silent-when-supported", true);
        for (int i = 0; i < names.size(); i++) {
            final String name = names.get(i);
            Bukkit.getScheduler().runTaskLater(this, new Runnable() {
                @Override
                public void run() {
                    String command = BanCommandBuilder.build(banMode, name, reason, silent);
                    if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                        getLogger().warning("command failed for " + name + ": /" + command);
                    }
                }
            }, (long) i * delay);
        }
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

