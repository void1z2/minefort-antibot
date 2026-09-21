package com.minefart.antibot;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class MinefortAntiBotPlugin extends JavaPlugin implements Listener {
    private static final int BSTATS_PLUGIN_ID = 33379;
    private static final String PLUGIN_VERSION = "1.1.0";
    private static final String UPDATE_API = "https://api.github.com/repos/void1z2/minefort-antibot/releases/latest";
    private static final String RELEASES_URL = "https://github.com/void1z2/minefort-antibot/releases";

    private final AtomicBoolean syncing = new AtomicBoolean(false);
    private final AtomicLong blockedAttempts = new AtomicLong();
    private volatile Set<String> blockedNames = Collections.emptySet();
    private volatile String databaseSource = "empty";
    private volatile String lastError = "none";

    private PublicDatabase database;
    private RaidTracker raidTracker;
    private V3Reporter reporter;
    private BukkitTask updateTask;
    private BukkitTask updateCheckTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        database = new PublicDatabase(getDataFolder());
        loadCachedDatabase();
        setupStuff();
        Bukkit.getPluginManager().registerEvents(this, this);
        startMetrics();

        long minutes = Math.max(1L, getConfig().getLong("update-minutes", 1L));
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

        getLogger().info("enabled | blocking before login | database " + blockedNames.size() + " | checking every " + minutes + "m");
    }

    @Override
    public void onDisable() {
        if (updateTask != null) updateTask.cancel();
        if (updateCheckTask != null) updateCheckTask.cancel();
    }

    private void setupStuff() {
        int minimum = Math.max(2, getConfig().getInt("raid.minimum-known-accounts", 3));
        long window = Math.max(1000L, getConfig().getLong("raid.window-seconds", 40L) * 1000L);
        long stayUp = Math.max(window, getConfig().getLong("raid.incident-seconds", 60L) * 1000L);
        raidTracker = new RaidTracker(minimum, window, stayUp);
        reporter = new V3Reporter(getDataFolder(), getConfig(), getLogger(), PLUGIN_VERSION);
    }

    private void loadCachedDatabase() {
        try {
            Set<String> oldNames = database.loadSnapshot();
            if (!oldNames.isEmpty()) putNames(oldNames, "cache");
        } catch (IOException error) {
            lastError = error.getMessage();
            getLogger().warning("database cache failed | " + error.getMessage());
        }
    }

    private void putNames(Set<String> names, String source) throws IOException {
        if (names == null || names.isEmpty()) throw new IOException("database contained no valid usernames");
        Set<String> clean = new LinkedHashSet<String>();
        for (String name : names) clean.add(normalize(name));
        blockedNames = Collections.unmodifiableSet(clean);
        databaseSource = source;
        lastError = "none";
    }

    private void syncDatabase() {
        if (!syncing.compareAndSet(false, true)) return;
        try {
            Set<String> names = database.download(getConfig().getString("database-url", ""));
            database.saveSnapshot(names);
            putNames(names, "remote");
            getLogger().info("database updated | " + names.size() + " usernames");
        } catch (IOException error) {
            lastError = error.getMessage();
            getLogger().warning("database check failed | using " + blockedNames.size() + " cached usernames | " + error.getMessage());
        } finally {
            syncing.set(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String name = event.getName();
        if (!blockedNames.contains(normalize(name))) return;

        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, colour(getConfig().getString(
                "blocked-message", "&cminefart antibot &8| &7access denied")));
        blockedAttempts.incrementAndGet();
        getLogger().warning("blocked | " + name + " | known database username");

        final RaidTracker.Incident incident = raidTracker.record(name, System.currentTimeMillis());
        if (incident == null) return;
        Bukkit.getScheduler().runTask(this, new Runnable() {
            @Override
            public void run() {
                broadcastRaid(incident);
            }
        });
        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                reporter.reportRaid(incident.names, incident.startedAt, incident.endedAt, blockedNames.size());
            }
        });
    }

    private void broadcastRaid(RaidTracker.Incident incident) {
        String summary = ChatColor.RED + "[AntiBot] " + ChatColor.YELLOW + "raid attempt " + ChatColor.DARK_GRAY + "| "
                + ChatColor.GRAY + incident.names.size() + " known accounts blocked " + ChatColor.DARK_GRAY + "| "
                + ChatColor.AQUA + "hover for details";
        String details = ChatColor.RED + "minefart antibot" + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY
                + "blocked " + incident.names.size() + " known accounts\n"
                + ChatColor.DARK_GRAY + "usernames | " + ChatColor.WHITE + joinNames(incident.names) + "\n"
                + ChatColor.DARK_GRAY + "database | " + ChatColor.WHITE + blockedNames.size() + " usernames";

        getLogger().warning("raid attempt | " + incident.names.size() + " known accounts | " + joinNames(incident.names));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("minefortantibot.alerts")) continue;
            try {
                TextComponent component = new TextComponent(summary);
                component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new BaseComponent[]{new TextComponent(details)}));
                player.spigot().sendMessage(component);
            } catch (Throwable ignored) {
                player.sendMessage(summary);
                player.sendMessage(ChatColor.DARK_GRAY + "accounts | " + ChatColor.GRAY + joinNames(incident.names));
            }
        }
    }

    private String joinNames(Set<String> names) {
        StringBuilder value = new StringBuilder();
        int added = 0;
        for (String name : names) {
            if (value.length() + name.length() + 2 > 220) {
                value.append(" | +").append(names.size() - added).append(" more");
                break;
            }
            if (value.length() > 0) value.append(", ");
            value.append(name);
            added++;
        }
        return value.toString();
    }

    private String colour(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    private String normalize(String value) {
        return String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
    }

    private void startMetrics() {
        Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);
        metrics.addCustomChart(new SimplePie("protection_mode", () -> "pre_login_blocking"));
        metrics.addCustomChart(new SimplePie("database_source", () -> databaseSource));
    }

    private void checkForPluginUpdate() {
        try {
            String latest = UpdateChecker.latestTag(UPDATE_API);
            if (UpdateChecker.newerThan(PLUGIN_VERSION, latest)) {
                getLogger().warning("update available | " + latest + " | running " + PLUGIN_VERSION);
                getLogger().warning("download | " + RELEASES_URL);
            }
        } catch (IOException error) {
            getLogger().fine("update check failed | " + error.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(ChatColor.GRAY + "MinefortAntiBot " + ChatColor.DARK_GRAY + "| "
                    + ChatColor.GRAY + "blocking " + blockedNames.size() + " usernames " + ChatColor.DARK_GRAY + "| "
                    + ChatColor.GRAY + "blocked " + blockedAttempts.get() + " this boot " + ChatColor.DARK_GRAY + "| "
                    + ChatColor.GRAY + "source " + databaseSource + " " + ChatColor.DARK_GRAY + "| "
                    + ChatColor.GRAY + "reports " + reporter.linkedServerName() + " " + ChatColor.DARK_GRAY + "| "
                    + ChatColor.GRAY + "last error " + lastError);
            return true;
        }
        if (args[0].equalsIgnoreCase("link")) {
            String verifier = getConfig().getString("v3.verifier-name", "Kixae").trim();
            boolean sentByKixae = sender instanceof Player && sender.getName().equalsIgnoreCase(verifier);
            if (!sender.hasPermission("minefortantibot.admin") && !sentByKixae) {
                sender.sendMessage(ChatColor.RED + "no permission");
                return true;
            }
            if (args.length != 3) {
                sender.sendMessage(ChatColor.GRAY + "/" + label + " link <server-name> <Kixae-code>");
                return true;
            }
            final CommandSender linkSender = sender;
            final String requestedServer = args[1];
            final String verificationCode = args[2];
            final V3Reporter activeReporter = reporter;
            sender.sendMessage(ChatColor.GRAY + "MinefortAntiBot " + ChatColor.DARK_GRAY + "| " + ChatColor.GRAY + "checking Kixae verification for " + requestedServer);
            Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
                @Override
                public void run() {
                    final String result = activeReporter.link(requestedServer, verificationCode);
                    Bukkit.getScheduler().runTask(MinefortAntiBotPlugin.this, new Runnable() {
                        @Override
                        public void run() {
                            linkSender.sendMessage(ChatColor.GRAY + "MinefortAntiBot " + ChatColor.DARK_GRAY + "| " + ChatColor.GRAY + result);
                        }
                    });
                }
            });
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("minefortantibot.admin")) {
                sender.sendMessage(ChatColor.RED + "no permission");
                return true;
            }
            reloadConfig();
            setupStuff();
            Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
                @Override
                public void run() {
                    syncDatabase();
                }
            });
            sender.sendMessage(ChatColor.GRAY + "MinefortAntiBot " + ChatColor.DARK_GRAY + "| " + ChatColor.GRAY + "checking database");
            return true;
        }
        sender.sendMessage(ChatColor.GRAY + "/" + label + " " + ChatColor.DARK_GRAY + "| " + ChatColor.GRAY + "status " + ChatColor.DARK_GRAY + "| " + ChatColor.GRAY + "reload " + ChatColor.DARK_GRAY + "| " + ChatColor.GRAY + "link <server-name> <Kixae-code>");
        return true;
    }
}
