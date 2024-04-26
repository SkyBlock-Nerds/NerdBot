package net.hypixel.skyblocknerds.api.badge;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@ToString
public class TieredBadge extends Badge {

    private final List<Tier> tiers;

    public TieredBadge(String id, String name, List<Tier> tiers) {
        super(id, name, null);
        this.tiers = tiers;
    }

    public Tier getTier(int tier) {
        return tiers.stream()
                .filter(t -> t.tier == tier)
                .findFirst()
                .map(t -> {
                    t.setBadge(this);
                    return t;
                })
                .orElse(null);
    }

    @Override
    public String getEmoji() {
        return null;
    }

    @Override
    public String getFormattedName() {
        return getName();
    }

    @Getter
    @Setter
    @ToString
    public static class Tier {
        private final String name;
        private final String emoji;
        private final int tier;
        private transient TieredBadge badge;

        public Tier(String name, String emoji, int tier) {
            this.name = name;
            this.emoji = emoji;
            this.tier = tier;
        }

        public String getEmoji() {
            // TODO: Implement
            return emoji;
        }

        public String getFormattedName() {
            // TODO: Implement
            /*if (badge == null) {
                return getEmoji().getFormatted() + " " + name;
            }

            return getEmoji().getFormatted() + " " + badge.getName() + " - " + name;*/

            return name;
        }
    }
}
