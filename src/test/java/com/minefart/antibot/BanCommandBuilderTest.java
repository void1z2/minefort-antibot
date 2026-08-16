package com.minefart.antibot;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void bedrockMarkerIsNotSentAsPartOfUsername() {
        assertEquals("ban BedrockName Bot Account", BanCommandBuilder.build(BanCommandBuilder.Mode.NORMAL, ".BedrockName", "Bot Account", true));
    }

    @Test
    public void uuidIsUsedAsTheBanTarget() {
        assertEquals("ban -s 123e4567-e89b-12d3-a456-426614174000 Bot Account",
                BanCommandBuilder.build(BanCommandBuilder.Mode.LITEBANS,
                        "123e4567-e89b-12d3-a456-426614174000", "Bot Account", true));
    }

    @Test
    public void updateCheckerUnderstandsReleaseTags() {
        assertTrue(UpdateChecker.newerThan("1.0.1", "v1.0.2"));
        assertFalse(UpdateChecker.newerThan("1.0.2", "v1.0.2"));
    }
}

