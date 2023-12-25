package net.hypixel.nerdbot.feature;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.wiki.MediaWikiAPI;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.TimerTask;

@Log4j2
public class ProfileUpdateFeature extends BotFeature {

    @Override
    public void onFeatureStart() {
        this.timer.scheduleAtFixedRate(
            new TimerTask() {
                @Override
                public void run() {
                    if (NerdBotApp.getBot().isReadOnly()) {
                        log.error("Bot is in read-only mode, skipping profile update task!");
                        return;
                    }

                    DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
                    discordUserRepository.forEach(discordUser -> {
                        if (discordUser.isProfileAssigned() && discordUser.getMojangProfile().requiresCacheUpdate()) {
                            updateUser(discordUser);
                        }
                    });
                }
            }, 0L, Duration.of(NerdBotApp.getBot().getConfig().getMojangUsernameCacheTTL(), ChronoUnit.HOURS).toMillis());
    }

    @Override
    public void onFeatureEnd() {
        this.timer.cancel();
    }

    public static void updateUser(DiscordUser discordUser) {
        MojangProfile mojangProfile = Util.getMojangProfile(discordUser.getMojangProfile().getUniqueId());
        discordUser.setMojangProfile(mojangProfile);
        Guild guild = Util.getMainGuild();
        guild.retrieveMemberById(discordUser.getDiscordId()).queue(m -> {
            if (!m.getEffectiveName().toLowerCase().contains(mojangProfile.getUsername().toLowerCase())) {
                try {
                    m.modifyNickname(mojangProfile.getUsername()).queue();
                    log.info("Modified nickname of " + m.getUser().getName() + " (ID: " + m.getId() + ") to " + mojangProfile.getUsername());
                } catch (HierarchyException exception) {
                    log.error("Unable to modify the nickname of " + m.getUser().getName() + " (" + m.getEffectiveName() + ") [" + m.getId() + "]", exception);
                }
            }

            boolean wikiEditor = MediaWikiAPI.isEditor(mojangProfile.getUsername());
            if (!wikiEditor || !discordUser.isAutoGiveWikiRole()) {
                return;
            }

            RoleManager.getPingableRoleByName("Wiki Editor").ifPresent(pingableRole -> {
                Role role = guild.getRoleById(pingableRole.roleId());
                if (role == null) {
                    log.warn("Role with ID " + pingableRole.roleId() + " does not exist");
                    return;
                }

                if (!m.getRoles().contains(role)) {
                    guild.addRoleToMember(m, role).complete();
                    log.info("Added " + role.getName() + " role to " + m.getUser().getName() + " (" + m.getId() + ")");
                }
            });
        }, e -> {
            log.error("Unable to retrieve member with ID " + discordUser.getDiscordId(), e);
        });
    }
}
