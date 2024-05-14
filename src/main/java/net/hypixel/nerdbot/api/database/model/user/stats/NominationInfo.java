package net.hypixel.nerdbot.api.database.model.user.stats;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

@Getter
@Setter
@ToString
public class NominationInfo {

    private int totalNominations = 0;
    private Date lastNominationDate = null;

    public void increaseNominations() {
        this.totalNominations++;
        this.lastNominationDate = new Date();
    }
}
