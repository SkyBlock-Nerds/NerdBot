package net.hypixel.skyblocknerds.discordbot.feature.discorduser;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.hypixel.skyblocknerds.api.configuration.ConfigurationManager;
import net.hypixel.skyblocknerds.api.feature.Feature;
import net.hypixel.skyblocknerds.database.objects.user.DiscordUser;
import net.hypixel.skyblocknerds.database.repository.RepositoryManager;
import net.hypixel.skyblocknerds.database.repository.impl.DiscordUserRepository;
import net.hypixel.skyblocknerds.discordbot.DiscordBot;
import net.hypixel.skyblocknerds.discordbot.configuration.GuildConfiguration;
import net.hypixel.skyblocknerds.utilities.StringUtils;

import java.util.List;
import java.util.Optional;

@Log4j2
public class DiscordUserRoleUpdateFeature extends Feature {

    public DiscordUserRoleUpdateFeature() {
        super();
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

            DiscordUser user = discordUser.get();
            String username = user.getLastKnownUsername() != null ? user.getLastKnownUsername() : user.getDiscordId();
            List<String> roles = member.getRoles().stream()
                .map(Role::getId)
                .toList();

            if (user.getRoles() == null || !user.getRoles().equals(roles)) {
                user.setRoles(roles);
                log.info("Updated roles for DiscordUser " + StringUtils.formatNameWithId(username, user.getDiscordId()));
            } else {
                log.debug("No role changes for DiscordUser " + StringUtils.formatNameWithId(username, user.getDiscordId()));
            }
        });
    }

    @Override
    public void onFeatureEnd() {
        // Nothing to do here
    }
}
