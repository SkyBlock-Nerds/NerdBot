package net.hypixel.nerdbot.util.discord;

import com.github.kaktushose.jda.commands.scope.GuildScopeProvider;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.hypixel.nerdbot.NerdBotApp;

import java.util.Set;

public class GuildScopeProviderImpl implements GuildScopeProvider {

    @Override
    public Set<Long> apply(CommandData commandData) {
        return Set.of(Long.valueOf(NerdBotApp.getBot().getConfig().getGuildId()));
    }
}
