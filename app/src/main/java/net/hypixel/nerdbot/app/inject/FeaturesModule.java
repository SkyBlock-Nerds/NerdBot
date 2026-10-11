package net.hypixel.nerdbot.app.inject;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;
import net.hypixel.nerdbot.app.activity.ActivityPurgeFeature;
import net.hypixel.nerdbot.app.feature.CurateFeature;
import net.hypixel.nerdbot.app.feature.DrivePermissionSyncFeature;
import net.hypixel.nerdbot.app.feature.HelloGoodbyeFeature;
import net.hypixel.nerdbot.app.feature.ProfileUpdateFeature;
import net.hypixel.nerdbot.app.feature.RepositoryAutosaveFeature;
import net.hypixel.nerdbot.app.feature.ResourcePackUpdateFeature;
import net.hypixel.nerdbot.app.feature.RoleReconcileFeature;
import net.hypixel.nerdbot.app.feature.UserGrabberFeature;
import net.hypixel.nerdbot.app.feature.UserNominationFeature;
import net.hypixel.nerdbot.app.user.BirthdayAnnouncementFeature;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;

import java.util.List;

/**
 * Every feature, keyed by class so the config's {@code features[].className} entries can be
 * looked up here instead of being built by reflection.
 */
@Module
public interface FeaturesModule {

    @Binds
    @IntoMap
    @ClassKey(CurateFeature.class)
    BotFeature curateFeature(CurateFeature feature);

    @Binds
    @IntoMap
    @ClassKey(DrivePermissionSyncFeature.class)
    BotFeature drivePermissionSyncFeature(DrivePermissionSyncFeature feature);

    @Binds
    @IntoMap
    @ClassKey(HelloGoodbyeFeature.class)
    BotFeature helloGoodbyeFeature(HelloGoodbyeFeature feature);

    @Binds
    @IntoMap
    @ClassKey(ProfileUpdateFeature.class)
    BotFeature profileUpdateFeature(ProfileUpdateFeature feature);

    @Binds
    @IntoMap
    @ClassKey(RepositoryAutosaveFeature.class)
    BotFeature repositoryAutosaveFeature(RepositoryAutosaveFeature feature);

    @Binds
    @IntoMap
    @ClassKey(ResourcePackUpdateFeature.class)
    BotFeature resourcePackUpdateFeature(ResourcePackUpdateFeature feature);

    @Binds
    @IntoMap
    @ClassKey(RoleReconcileFeature.class)
    BotFeature roleReconcileFeature(RoleReconcileFeature feature);

    @Binds
    @IntoMap
    @ClassKey(UserGrabberFeature.class)
    BotFeature userGrabberFeature(UserGrabberFeature feature);

    @Binds
    @IntoMap
    @ClassKey(UserNominationFeature.class)
    BotFeature userNominationFeature(UserNominationFeature feature);

    @Binds
    @IntoMap
    @ClassKey(ActivityPurgeFeature.class)
    BotFeature activityPurgeFeature(ActivityPurgeFeature feature);

    @Binds
    @IntoMap
    @ClassKey(BirthdayAnnouncementFeature.class)
    BotFeature birthdayAnnouncementFeature(BirthdayAnnouncementFeature feature);

    /**
     * Started after the configured features, whatever the config says. Listing one of these in the
     * config as well doesn't start a second copy.
     * <ul>
     *     <li>The autosave flushes cached changes, so a crash loses at most one interval.</li>
     *     <li>Role reconciling catches role changes made while the bot was offline.</li>
     *     <li>Birthdays are announced on the day, once, in each member's own time zone.</li>
     *     <li>The Drive sync is the safety net for folder permissions, and does nothing unless Drive is configured.</li>
     * </ul>
     */
    @Provides
    @AlwaysOnFeatures
    static List<BotFeature> alwaysOn(RepositoryAutosaveFeature repositoryAutosaveFeature, RoleReconcileFeature roleReconcileFeature,
                                     BirthdayAnnouncementFeature birthdayAnnouncementFeature, DrivePermissionSyncFeature drivePermissionSyncFeature) {
        return List.of(repositoryAutosaveFeature, roleReconcileFeature, birthdayAnnouncementFeature, drivePermissionSyncFeature);
    }
}
