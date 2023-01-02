package net.hypixel.nerdbot.bot.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;

@Getter
@Setter
@ToString
public class TagConfig {

    /**
     * The ID of the {@link ForumTag tag} in a {@link ForumChannel forum channel}
     */
    private String greenlit;

}
