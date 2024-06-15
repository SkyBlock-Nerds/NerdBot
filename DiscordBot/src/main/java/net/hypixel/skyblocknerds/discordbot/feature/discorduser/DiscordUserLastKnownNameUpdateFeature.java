package net.hypixel.skyblocknerds.discordbot.feature.discorduser;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Guild;
import net.hypixel.skyblocknerds.api.configuration.ConfigurationManager;
import net.hypixel.skyblocknerds.api.feature.Feature;
import net.hypixel.skyblocknerds.database.objects.user.DiscordUser;
import net.hypixel.skyblocknerds.database.repository.RepositoryManager;
import net.hypixel.skyblocknerds.database.repository.impl.DiscordUserRepository;
import net.hypixel.skyblocknerds.discordbot.DiscordBot;
import net.hypixel.skyblocknerds.discordbot.configuration.GuildConfiguration;
import net.hypixel.skyblocknerds.utilities.StringUtils;

import java.util.Optional;

@Log4j2
public class DiscordUserLastKnownNameUpdateFeature extends Feature {

    public DiscordUserLastKnownNameUpdateFeature() {
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
            String nameWithId = StringUtils.formatNameWithId(member.getEffectiveName(), member.getId());

            if (discordUser.isEmpty()) {
                log.warn("DiscordUser not found for " + nameWithId);
                return;
            }

            DiscordUser user = discordUser.get();

            if (user.getLastKnownUsername() == null) {
                user.setLastKnownUsername(member.getUser().getName());
                log.info("Set last known username for user " + nameWithId + " to " + user.getLastKnownUsername() + " because it was null!");
            }
        });
    }

    @Override
    public void onFeatureEnd() {

    }
}
