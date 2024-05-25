package net.hypixel.skyblocknerds.database.objects.user.warning;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class WarningEntry {

    /**
     * The reason for the warning
     */
    private String reason;

    /**
     * The {@link Long timestamp} of the warning
     */
    private long timestamp;

    /**
     * The ID of the issuer of the warning
     */
    private String issuerId;
}
