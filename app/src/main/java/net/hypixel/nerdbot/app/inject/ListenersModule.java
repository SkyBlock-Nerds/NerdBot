package net.hypixel.nerdbot.app.inject;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoSet;
import net.hypixel.nerdbot.app.activity.ActivityListener;
import net.hypixel.nerdbot.app.listener.FunListener;
import net.hypixel.nerdbot.app.listener.MemberLifecycleListener;
import net.hypixel.nerdbot.app.listener.MemberRolesListener;
import net.hypixel.nerdbot.app.listener.MetricsListener;
import net.hypixel.nerdbot.app.listener.ModLogListener;
import net.hypixel.nerdbot.app.listener.PinListener;
import net.hypixel.nerdbot.app.listener.ReactionChannelListener;
import net.hypixel.nerdbot.app.listener.RoleRestrictedChannelListener;
import net.hypixel.nerdbot.app.listener.SuggestionListener;
import net.hypixel.nerdbot.discord.cache.MessageCache;

/** Every JDA event listener the bot registers. */
@Module
public interface ListenersModule {

    @Binds
    @IntoSet
    @EventListeners
    Object activityListener(ActivityListener listener);

    @Binds
    @IntoSet
    @EventListeners
    Object modLogListener(ModLogListener listener);

    @Binds
    @IntoSet
    @EventListeners
    Object suggestionListener(SuggestionListener listener);

    @Binds
    @IntoSet
    @EventListeners
    Object reactionChannelListener(ReactionChannelListener listener);

    @Binds
    @IntoSet
    @EventListeners
    Object pinListener(PinListener listener);

    @Binds
    @IntoSet
    @EventListeners
    Object metricsListener(MetricsListener listener);

    @Binds
    @IntoSet
    @EventListeners
    Object funListener(FunListener listener);

    @Binds
    @IntoSet
    @EventListeners
    Object roleRestrictedChannelListener(RoleRestrictedChannelListener listener);

    @Binds
    @IntoSet
    @EventListeners
    Object memberLifecycleListener(MemberLifecycleListener listener);

    @Binds
    @IntoSet
    @EventListeners
    Object memberRolesListener(MemberRolesListener listener);

    @Binds
    @IntoSet
    @EventListeners
    Object messageCache(MessageCache cache);
}
