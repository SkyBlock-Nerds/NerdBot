package net.hypixel.nerdbot.app.listener;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import net.dv8tion.jda.api.entities.Guild;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import org.jetbrains.annotations.Nullable;

/**
 * The guild the bot serves. The bot can also sit in other guilds, such as emoji servers, and
 * listeners ignore their events so leaving one of those can't touch a member's record.
 */
public class GuildScope {

    private final Provider<NerdBotConfig> config;

    @Inject
    public GuildScope(Provider<NerdBotConfig> config) {
        this.config = config;
    }

    public boolean isMainGuild(@Nullable Guild guild) {
        return guild != null && guild.getId().equals(config.get().getGuildId());
    }
}
