package com.minefart.antibot;

import java.util.UUID;

final class DatabaseEntry {
    final UUID uuid;
    final String name;

    DatabaseEntry(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name == null ? "" : name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DatabaseEntry)) return false;
        DatabaseEntry entry = (DatabaseEntry) other;
        return uuid.equals(entry.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}
