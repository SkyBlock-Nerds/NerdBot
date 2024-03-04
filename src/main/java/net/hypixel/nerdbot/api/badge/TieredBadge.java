package net.hypixel.nerdbot.api.badge;

import com.vdurmont.emoji.EmojiManager;
import lombok.Getter;
import lombok.ToString;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.hypixel.nerdbot.cache.EmojiCache;

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
        return tiers.stream().filter(t -> t.tier == tier).findFirst().orElse(null);
    }

    @Override
    public Emoji getEmoji() {
        return null;
    }

    @Override
    public String getFormattedName() {
        return getName();
    }

    @Getter
    @ToString
    public static class Tier {
        private final String name;
        private final String emoji;
        private final int tier;

        public Tier(String name, String emoji, int tier) {
            this.name = name;
            this.emoji = emoji;
            this.tier = tier;
        }

        public Emoji getEmoji() {
            if (EmojiManager.isEmoji(emoji)) {
                return Emoji.fromUnicode(emoji);
            }

            return EmojiCache.getEmojiById(emoji).orElse(Emoji.fromUnicode("❓"));
        }

        public String getFormattedName() {
            return getEmoji().getFormatted() + " " + name;
        }
    }
}
