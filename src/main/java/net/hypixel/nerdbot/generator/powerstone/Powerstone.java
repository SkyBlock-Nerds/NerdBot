package net.hypixel.nerdbot.generator.powerstone;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.util.Tuple;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Powerstone {
    List<PowerstoneStats> scalingStats = new ArrayList<PowerstoneStats>();
    List<PowerstoneStats> staticStats = new ArrayList<PowerstoneStats>();

    public static class Builder implements ClassBuilder<Powerstone> {

        @Override
        public Powerstone build() {
            return null;
        }
    }
}