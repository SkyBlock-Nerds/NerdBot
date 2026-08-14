package net.hypixel.nerdbot.app.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleDriveConfigTest {

    @Test
    void parsesFromBotConfigJson() {
        String json = """
            {
              "googleDriveConfig": {
                "enabled": true,
                "folderMappings": [
                  {"roleId": "111", "folderIds": ["fA", "fB"], "accessLevel": "READER"},
                  {"roleId": "222", "folderIds": ["fA"], "accessLevel": "WRITER"}
                ]
              }
            }
            """;

        NerdBotConfig config = new Gson().fromJson(json, NerdBotConfig.class);
        GoogleDriveConfig driveConfig = config.getGoogleDriveConfig();

        assertTrue(driveConfig.isEnabled());
        assertEquals(2, driveConfig.getFolderMappings().size());
        assertEquals("111", driveConfig.getFolderMappings().get(0).getRoleId());
        assertEquals(List.of("fA", "fB"), driveConfig.getFolderMappings().get(0).getFolderIds());
        assertEquals("WRITER", driveConfig.getFolderMappings().get(1).getAccessLevel());
    }

    @Test
    void defaultsToDisabledWhenAbsent() {
        NerdBotConfig config = new Gson().fromJson("{}", NerdBotConfig.class);
        assertNotNull(config.getGoogleDriveConfig());
        assertFalse(config.getGoogleDriveConfig().isEnabled());
        assertNotNull(config.getGoogleDriveConfig().getFolderMappings());
    }
}
