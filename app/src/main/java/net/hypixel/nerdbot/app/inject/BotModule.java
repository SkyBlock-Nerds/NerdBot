package net.hypixel.nerdbot.app.inject;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import net.hypixel.nerdbot.app.channel.DiscordLogChannel;
import net.hypixel.nerdbot.app.channel.LogChannel;

import java.time.Clock;

@Module
public interface BotModule {

    @Binds
    LogChannel logChannel(DiscordLogChannel channel);

    @Provides
    static Clock clock() {
        return Clock.systemUTC();
    }
}
