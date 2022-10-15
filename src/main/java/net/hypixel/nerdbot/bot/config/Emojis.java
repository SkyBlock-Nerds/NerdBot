package net.hypixel.nerdbot.bot.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@Getter
@Setter
@ToString
public class Emojis {

    public static final Emojis DEFAULT = new Emojis("\uD83D\uDC4D", "\uD83D\uDC4E", "\u2705");

    private final String agree, disagree, greenlit;
}
