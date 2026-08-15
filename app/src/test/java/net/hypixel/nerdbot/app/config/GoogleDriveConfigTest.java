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

    @Test
    void notificationEmailsDefaultOnAndParseOff() {
        NerdBotConfig absent = new Gson().fromJson("{}", NerdBotConfig.class);
        assertTrue(absent.getGoogleDriveConfig().isSendNotificationEmails());

        String json = """
            {"googleDriveConfig": {"sendNotificationEmails": false}}
            """;
        NerdBotConfig explicit = new Gson().fromJson(json, NerdBotConfig.class);
        assertFalse(explicit.getGoogleDriveConfig().isSendNotificationEmails());
    }

    @Test
    void notificationEmailMessageDefaultsAndParses() {
        NerdBotConfig absent = new Gson().fromJson("{}", NerdBotConfig.class);
        assertFalse(absent.getGoogleDriveConfig().getNotificationEmailMessage().isBlank());

        String json = """
            {"googleDriveConfig": {"notificationEmailMessage": "custom text"}}
            """;
        NerdBotConfig explicit = new Gson().fromJson(json, NerdBotConfig.class);
        assertEquals("custom text", explicit.getGoogleDriveConfig().getNotificationEmailMessage());
    }
}
