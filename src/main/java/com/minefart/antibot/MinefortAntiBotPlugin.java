package com.minefart.antibot;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class MinefortAntiBotPlugin extends JavaPlugin implements Listener {
    private static final int BSTATS_PLUGIN_ID = 33379;
    private static final String PLUGIN_VERSION = "1.1.2";
    private static final String CURRENT_DATABASE_URL = "https://raw.githubusercontent.com/void1z2/minefort-antibot/main/database.txt";
    private static final String UPDATE_API = "https://api.github.com/repos/void1z2/minefort-antibot/releases/latest";
    private static final String RELEASES_URL = "https://github.com/void1z2/minefort-antibot/releases";
    private static final String PREFIX = ChatColor.DARK_GRAY + "[" + ChatColor.YELLOW + "Minefart" + ChatColor.DARK_GRAY + "] ";

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
    private BukkitTask rosterTask;

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

        long rosterSeconds = Math.max(30L, getConfig().getLong("v3.roster-seconds", 60L));
        rosterTask = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                sendRoster();
            }
        }, 100L, rosterSeconds * 20L);

        getLogger().info("enabled | blocking before login | database " + blockedNames.size() + " | checking every " + minutes + "m");
    }

    @Override
    public void onDisable() {
        if (updateTask != null) updateTask.cancel();
        if (updateCheckTask != null) updateCheckTask.cancel();
        if (rosterTask != null) rosterTask.cancel();
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
            Set<String> names = database.download(databaseUrl());
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

    private void sendRoster() {
        if (reporter == null || !reporter.isLinked()) return;
        final Set<String> names = new LinkedHashSet<String>();
        for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                reporter.reportRoster(names);
            }
        });
    }

    private String databaseUrl() {
        String configured = getConfig().getString("database-url", "").trim();
        // v1.0.2 pointed this at databasev2.txt, which is a UUID list and is
        // not readable by the modern pre-login username blocker. Switching
        // that known old setting here makes a straight JAR upgrade safe.
        if (configured.toLowerCase(Locale.ROOT).contains("databasev2")) {
            getLogger().info("old v1.0 database setting found | using current username list");
            return CURRENT_DATABASE_URL;
        }
        return configured.isEmpty() ? CURRENT_DATABASE_URL : configured;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String name = event.getName();
        if (!blockedNames.contains(normalize(name))) return;

        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, colour(getConfig().getString(
                "blocked-message", "&8[&eMinefart&8] &cAccess denied")));
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
        String summary = ChatColor.DARK_GRAY + "[" + ChatColor.RED + "RAID" + ChatColor.DARK_GRAY + "] "
                + ChatColor.GRAY + "known accounts blocked " + ChatColor.DARK_GRAY + "» "
                + ChatColor.WHITE + incident.names.size() + ChatColor.DARK_GRAY + "  "
                + ChatColor.YELLOW + "hover";
        String details = ChatColor.DARK_GRAY + "[" + ChatColor.RED + "Raid attempt" + ChatColor.DARK_GRAY + "]\n"
                + ChatColor.DARK_GRAY + "» " + ChatColor.YELLOW + "Blocked: " + ChatColor.WHITE + incident.names.size() + " known accounts\n"
                + ChatColor.DARK_GRAY + "» " + ChatColor.YELLOW + "Accounts: " + ChatColor.WHITE + joinNames(incident.names) + "\n"
                + ChatColor.DARK_GRAY + "» " + ChatColor.YELLOW + "Database: " + ChatColor.WHITE + blockedNames.size() + " names";

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

    private String line(String label, Object value) {
        return ChatColor.DARK_GRAY + "» " + ChatColor.YELLOW + label + ChatColor.GRAY + ": " + ChatColor.WHITE + value;
    }

    private void sendTitle(CommandSender sender, String title) {
        sender.sendMessage(PREFIX + ChatColor.YELLOW + title);
    }

    private void sendPluginPage(CommandSender sender) {
        TextComponent message = new TextComponent(PREFIX + ChatColor.YELLOW + "Plugin" + ChatColor.DARK_GRAY
                + " » " + ChatColor.WHITE + "https://minef.art");
        message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://minef.art"));
        message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new BaseComponent[]{new TextComponent(ChatColor.GRAY + "Click to open minef.art")}));
        if (sender instanceof Player) {
            ((Player) sender).spigot().sendMessage(message);
            return;
        }
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "Plugin" + ChatColor.DARK_GRAY
                + " » " + ChatColor.WHITE + "https://minef.art");
    }

    private boolean isKixae(Player player) {
        String configured = getConfig().getString("v3.verifier-uuid", "f306579c-18d7-49b7-a58e-5c065d985107").trim();
        try {
            return player.getName().equalsIgnoreCase(getConfig().getString("v3.verifier-name", "Kixae"))
                    && player.getUniqueId().equals(UUID.fromString(configured));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String installResult(String result) {
        String value = String.valueOf(result == null ? "" : result).trim();
        if (value.startsWith("linked to ")) {
            String server = value.substring("linked to ".length()).replace(" | signed reports enabled", "");
            return ChatColor.GREEN + "Linked " + ChatColor.DARK_GRAY + "» "
                    + ChatColor.GRAY + "reports enabled for " + ChatColor.WHITE + server;
        }
        if (value.startsWith("already linked to ")) {
            return ChatColor.YELLOW + "Already linked " + ChatColor.DARK_GRAY + "» "
                    + ChatColor.WHITE + value.substring("already linked to ".length());
        }
        String mismatch = "link failed | server name mismatch | linked to ";
        if (value.startsWith(mismatch)) {
            return ChatColor.RED + "Server name mismatch " + ChatColor.DARK_GRAY + "» "
                    + ChatColor.GRAY + "linked to " + ChatColor.WHITE + value.substring(mismatch.length());
        }
        if (value.startsWith("link failed | ")) value = value.substring("link failed | ".length());
        return ChatColor.RED + "Link failed " + ChatColor.DARK_GRAY + "» " + ChatColor.GRAY + value;
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
            sendTitle(sender, "Status");
            sender.sendMessage(line("Database", blockedNames.size() + " names"));
            sender.sendMessage(line("Blocked", blockedAttempts.get() + " this restart"));
            sender.sendMessage(line("Source", databaseSource));
            sender.sendMessage(line("Linked server", reporter.linkedServerName()));
            if (!"none".equalsIgnoreCase(lastError)) sender.sendMessage(line("Last error", lastError));
            return true;
        }
        if (args[0].equalsIgnoreCase("install")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Kixae has to run this in game.");
                return true;
            }
            Player player = (Player) sender;
            if (!isKixae(player)) {
                sender.sendMessage(PREFIX + ChatColor.RED + "No permission.");
                return true;
            }
            if (!player.isOp()) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Kixae needs OP before linking.");
                return true;
            }
            if (args.length != 3) {
                sender.sendMessage(PREFIX + ChatColor.GRAY + "Kixae handshake is missing.");
                return true;
            }
            final CommandSender linkSender = sender;
            final String requestedServer = args[1];
            final String verificationCode = args[2];
            final V3Reporter activeReporter = reporter;
            sendTitle(sender, "Installing link");
            sender.sendMessage(line("Server", requestedServer));
            sender.sendMessage(ChatColor.DARK_GRAY + "» " + ChatColor.GRAY + "Checking Kixae handshake...");
            Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
                @Override
                public void run() {
                    final String result = activeReporter.install(requestedServer, verificationCode);
                    Bukkit.getScheduler().runTask(MinefortAntiBotPlugin.this, new Runnable() {
                        @Override
                        public void run() {
                            linkSender.sendMessage(PREFIX + installResult(result));
                        }
                    });
                }
            });
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("minefortantibot.admin")) {
                sender.sendMessage(PREFIX + ChatColor.RED + "No permission.");
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
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Checking database...");
            return true;
        }
        if (args[0].equalsIgnoreCase("plugin")) {
            sendPluginPage(sender);
            return true;
        }
        sendTitle(sender, "Commands");
        sender.sendMessage(ChatColor.DARK_GRAY + "» " + ChatColor.YELLOW + "/" + label + " status " + ChatColor.DARK_GRAY + "| " + ChatColor.YELLOW + "/" + label + " reload " + ChatColor.DARK_GRAY + "| " + ChatColor.YELLOW + "/" + label + " plugin");
        sender.sendMessage(ChatColor.DARK_GRAY + "» " + ChatColor.GRAY + "Kixae links this server after it gets OP.");
        return true;
    }
}
