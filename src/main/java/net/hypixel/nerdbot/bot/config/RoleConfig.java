package net.hypixel.nerdbot.bot.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.util.Objects;
import java.util.function.Function;

@Getter
@Setter
@ToString
public class RoleConfig {

    /**
     * The {@link Role} ID of the Bot Manager role
     */
    private String botManagerRoleId = "";

    /**
     * The {@link Role} ID of the New Member role
     */
    private String newMemberRoleId = "";

    public boolean isEquals(MessageReaction reaction, Function<RoleConfig, String> function) {
        if (reaction.getEmoji().getType() != Emoji.Type.CUSTOM) {
            return false;
        }

        return Objects.equals(reaction.getEmoji().asCustom().getId(), function.apply(this));
    }
}
