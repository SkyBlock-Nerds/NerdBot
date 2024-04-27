package net.hypixel.skyblocknerds.discordbot.feature;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.hypixel.skyblocknerds.api.SkyBlockNerdsAPI;
import net.hypixel.skyblocknerds.api.configuration.ConfigurationManager;
import net.hypixel.skyblocknerds.api.feature.Feature;
import net.hypixel.skyblocknerds.api.http.mojang.sessionserver.MojangSessionServerUsernameResponse;
import net.hypixel.skyblocknerds.database.objects.user.DiscordUser;
import net.hypixel.skyblocknerds.database.repository.RepositoryManager;
import net.hypixel.skyblocknerds.database.repository.impl.DiscordUserRepository;
import net.hypixel.skyblocknerds.discordbot.DiscordBot;
import net.hypixel.skyblocknerds.discordbot.configuration.GuildConfiguration;
import net.hypixel.skyblocknerds.utilities.StringUtils;

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
        GuildConfiguration guildConfiguration = ConfigurationManager.loadConfig(GuildConfiguration.class);
        Guild guild = DiscordBot.getJda().getGuildById(guildConfiguration.getPrimaryGuildId());

        if (guild == null) {
            log.error("Primary guild " + guildConfiguration.getPrimaryGuildId() + " not found");
            return;
        }

        guild.loadMembers(member -> {
            if (member.getUser().isBot()) {
                return;
            }

            Optional<DiscordUser> discordUser = discordUserRepository.findOrCreateById(member.getId(), member.getId());

            if (discordUser.isEmpty()) {
                log.warn("DiscordUser not found for " + StringUtils.formatNameWithId(member.getEffectiveName(), member.getId()));
                return;
            }

            if (discordUser.get().getMinecraftProfile() == null) {
                log.warn("MinecraftProfile not linked for " + StringUtils.formatNameWithId(member.getUser().getName(), member.getId()));
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
                    log.info("Updated nickname for " + StringUtils.formatNameWithId(member.getEffectiveName(), member.getId()) + " to " + discordUser.get().getMinecraftProfile().getUsername());
                } catch (HierarchyException exception) {
                    log.warn("Unable to modify the nickname of " + StringUtils.formatNameWithId(member.getEffectiveName(), member.getId()) + " since they have a higher role than the bot");
                } catch (Exception exception) {
                    log.error("Unable to modify the nickname of " + StringUtils.formatNameWithId(member.getEffectiveName(), member.getId()), exception);
                }
            }
        });
    }

    @Override
    public void onFeatureEnd() {

    }
}
