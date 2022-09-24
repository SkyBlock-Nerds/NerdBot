package net.hypixel.nerdbot.bot.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class Emojis {

    public static final Emojis DEFAULT = new Emojis("\uD83D\uDC4D", "\uD83D\uDC4E", "\u2705");

    private final String agree, disagree, greenlit;
}
