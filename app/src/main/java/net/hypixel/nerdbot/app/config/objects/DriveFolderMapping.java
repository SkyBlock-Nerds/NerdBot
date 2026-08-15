package net.hypixel.nerdbot.app.config.objects;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.hypixel.nerdbot.app.config.ExampleValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps one Discord role to the shared-Drive folders its holders can access and
 * the level they get. A member holding several mapped roles receives the union
 * of all their mappings; where two mappings hit the same folder, the more
 * permissive level wins.
 */
@Getter
@Setter
@ToString
public class DriveFolderMapping {

    private String roleId;
    private List<String> folderIds = new ArrayList<>();

    /**
     * A {@code DriveAccessLevel} enum name: READER, COMMENTER, or WRITER.
     * Held as a string so a config typo disables one mapping (with a warning)
     * instead of failing the whole config load.
     */
    @ExampleValue("READER")
    private String accessLevel = "READER";
}
