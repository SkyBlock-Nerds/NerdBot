package net.hypixel.nerdbot.service.orangejuice.requestmodels.generator.submodels;

import lombok.Data;
import org.jetbrains.annotations.Nullable;

@Data
public class ItemLocation {

    /**
     * Depending on if only location1 is filled or location1 and location2 are filled it will either be a range of items or a single slot.
     */
    private Integer location1;

    /**
     * Depending on if only location1 is filled or location1 and location2 are filled it will either be a range of items or a single slot.
     */
    @Nullable
    private Integer location2;
}