package com.minefart.antibot;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;

public class BanCommandBuilderTest {
    @Test
    public void liteBansUsesSilentFlag() {
        BanCommandBuilder.Mode mode = BanCommandBuilder.detect(new HashSet<String>(Arrays.asList("Vault", "LiteBans")));
        assertEquals("ban -s BotName Bot Account", BanCommandBuilder.build(mode, "BotName", "Bot Account", true));
    }

    @Test
    public void advancedBanUsesSilentFlag() {
        BanCommandBuilder.Mode mode = BanCommandBuilder.detect(new HashSet<String>(Arrays.asList("AdvancedBan")));
        assertEquals("ban -s BotName Bot Account", BanCommandBuilder.build(mode, "BotName", "Bot Account", true));
    }

    @Test
    public void normalBanHasNoUnsupportedFlag() {
        assertEquals("ban BotName Bot Account", BanCommandBuilder.build(BanCommandBuilder.Mode.NORMAL, "BotName", "Bot Account", true));
    }

    @Test
    public void crackedMarkerIsNotSentAsPartOfUsername() {
        assertEquals("ban BotName Bot Account", BanCommandBuilder.build(BanCommandBuilder.Mode.NORMAL, "+BotName", "Bot Account", true));
    }
}

