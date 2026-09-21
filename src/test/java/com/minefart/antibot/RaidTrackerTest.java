package com.minefart.antibot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class RaidTrackerTest {
    @Test
    public void triggersOnThirdDistinctKnownUsername() {
        RaidTracker tracker = new RaidTracker(3, 40000L, 60000L);
        assertNull(tracker.record("BotOne", 1000L));
        assertNull(tracker.record("BotTwo", 2000L));
        RaidTracker.Incident incident = tracker.record("BotThree", 3000L);
        assertNotNull(incident);
        assertEquals(3, incident.names.size());
    }

    @Test
    public void repeatedUsernameDoesNotTriggerRaid() {
        RaidTracker tracker = new RaidTracker(3, 40000L, 60000L);
        assertNull(tracker.record("BotOne", 1000L));
        assertNull(tracker.record("botone", 2000L));
        assertNull(tracker.record("BOTONE", 3000L));
    }

    @Test
    public void oldAttemptsFallOutOfWindow() {
        RaidTracker tracker = new RaidTracker(3, 1000L, 60000L);
        assertNull(tracker.record("BotOne", 1L));
        assertNull(tracker.record("BotTwo", 2L));
        assertNull(tracker.record("BotThree", 2000L));
    }
}
