package com.minefart.antibot;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class RaidTracker {
    private final int needed;
    private final long windowMs;
    private final long incidentMs;
    private final Deque<Attempt> attempts = new ArrayDeque<Attempt>();
    private long incidentUntil;

    RaidTracker(int minimumKnownAccounts, long windowMs, long incidentMs) {
        this.needed = minimumKnownAccounts;
        this.windowMs = windowMs;
        this.incidentMs = incidentMs;
    }

    synchronized Incident record(String username, long now) {
        while (!attempts.isEmpty() && now - attempts.peekFirst().at > windowMs) attempts.removeFirst();
        attempts.addLast(new Attempt(username, now));
        if (now < incidentUntil) return null;

        Map<String, String> distinct = new LinkedHashMap<String, String>();
        long startedAt = now;
        for (Attempt attempt : attempts) {
            String key = attempt.username.toLowerCase(Locale.ROOT);
            if (!distinct.containsKey(key)) distinct.put(key, attempt.username);
            startedAt = Math.min(startedAt, attempt.at);
        }
        if (distinct.size() < needed) return null;
        incidentUntil = now + incidentMs;
        return new Incident(new LinkedHashSet<String>(distinct.values()), startedAt, now);
    }

    static final class Incident {
        final Set<String> names;
        final long startedAt;
        final long endedAt;

        Incident(Set<String> names, long startedAt, long endedAt) {
            this.names = names;
            this.startedAt = startedAt;
            this.endedAt = endedAt;
        }
    }

    private static final class Attempt {
        final String username;
        final long at;

        Attempt(String username, long at) {
            this.username = username;
            this.at = at;
        }
    }
}
