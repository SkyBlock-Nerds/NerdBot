package net.hypixel.skyblocknerds.discordbot.feature;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.hypixel.skyblocknerds.api.SkyBlockNerdsAPI;
import net.hypixel.skyblocknerds.api.feature.Feature;
import net.hypixel.skyblocknerds.api.http.mojang.sessionserver.MojangSessionServerUsernameResponse;
import net.hypixel.skyblocknerds.database.objects.user.DiscordUser;
import net.hypixel.skyblocknerds.database.repository.RepositoryManager;
import net.hypixel.skyblocknerds.database.repository.impl.DiscordUserRepository;
import net.hypixel.skyblocknerds.discordbot.Entrypoint;
import net.hypixel.skyblocknerds.utilities.StringUtilities;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Log4j2
public class MinecraftProfileUpdateFeature extends Feature {

    public MinecraftProfileUpdateFeature() {
        super(1, TimeUnit.HOURS);
    }

    @Override
    public void onFeatureStart() {
        DiscordUserRepository discordUserRepository = RepositoryManager.getInstance().getRepository(DiscordUserRepository.class);

        Guild guild = Entrypoint.INSTANCE.getJda().getGuildById(Entrypoint.INSTANCE.getBotConfiguration().getPrimaryGuildId());

        if (guild == null) {
            log.error("Primary guild not found");
            return;
        }

        guild.loadMembers(member -> {
            if (member.getUser().isBot()) {
                return;
            }

            Optional<DiscordUser> discordUser = discordUserRepository.findOrCreateById(member.getId(), member.getId());

            if (discordUser.isEmpty()) {
                log.warn("DiscordUser not found for " + StringUtilities.formatNameWithId(member.getEffectiveName(), member.getId()));
                return;
            }

            if (discordUser.get().getMinecraftProfile() == null) {
                log.warn("MinecraftProfile not linked for " + StringUtilities.formatNameWithId(member.getEffectiveName(), member.getId()));
                return;
            }

            MojangSessionServerUsernameResponse response = SkyBlockNerdsAPI.MOJANG_SESSION_SERVER_REQUEST.getUsername(discordUser.get().getMinecraftProfile().getUniqueId());

            if (!discordUser.get().getMinecraftProfile().getUsername().equals(response.getUsername())) {
                discordUser.get().getMinecraftProfile().setUsername(response.getUsername());
                discordUserRepository.saveToDatabase(discordUser.get());
                log.info("Updated MinecraftProfile username for " + discordUser.get().getDiscordId() + " to " + response.getUsername());
            }

            if (!member.getEffectiveName().toLowerCase().contains(member.getUser().getName().toLowerCase())) {
                try {
                    member.modifyNickname(discordUser.get().getMinecraftProfile().getUsername()).complete();
                    log.info("Updated nickname for " + StringUtilities.formatNameWithId(member.getEffectiveName(), member.getId()) + " to " + discordUser.get().getMinecraftProfile().getUsername());
                } catch (HierarchyException exception) {
                    log.error("Unable to modify the nickname of " + StringUtilities.formatNameWithId(member.getEffectiveName(), member.getId()) + " because they have a higher role");
                } catch (InsufficientPermissionException exception) {
                    log.error("Unable to modify the nickname of " + StringUtilities.formatNameWithId(member.getEffectiveName(), member.getId()) + " because the bot doesn't have the required permissions", exception);
                }
            }
        });
    }

    @Override
    public void onFeatureEnd() {

    }
}
