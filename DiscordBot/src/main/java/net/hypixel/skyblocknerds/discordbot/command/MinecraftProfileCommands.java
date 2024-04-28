package net.hypixel.skyblocknerds.discordbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.mongodb.client.result.UpdateResult;
import net.hypixel.skyblocknerds.database.objects.user.minecraft.MinecraftProfile;
import net.hypixel.skyblocknerds.database.repository.RepositoryManager;
import net.hypixel.skyblocknerds.database.repository.impl.DiscordUserRepository;
import net.hypixel.skyblocknerds.utilities.StringUtils;

public class MinecraftProfileCommands extends ApplicationCommand {

    private final DiscordUserRepository discordUserRepository;

    public MinecraftProfileCommands() {
        discordUserRepository = RepositoryManager.getInstance().getRepository(DiscordUserRepository.class);
    }

    @JDASlashCommand(name = "profiletest", description = "View your linked Minecraft profile", defaultLocked = true)
    public void profileTest(GuildSlashEvent event) {
        discordUserRepository.findById(event.getMember().getId()).ifPresentOrElse(discordUser -> {
            if (discordUser.hasMinecraftProfile()) {
                MinecraftProfile minecraftProfile = discordUser.getMinecraftProfile();

                event.reply("Your linked Minecraft profile is: " + StringUtils.formatNameWithId(minecraftProfile.getUsername(), minecraftProfile.getUniqueId().toString())).queue();
            } else {
                event.reply("You have not linked your Minecraft profile yet!").queue();
            }
        }, () -> {
            event.reply("You have not linked your Minecraft profile yet!").queue();
        });
    }

    @JDASlashCommand(name = "profilelinktest", description = "Link your Minecraft profile", defaultLocked = true)
    public void profileLinkTest(GuildSlashEvent event, @AppOption(name = "username", description = "Your Minecraft username") String username) {
        discordUserRepository.findById(event.getMember().getId()).ifPresentOrElse(discordUser -> {
            if (discordUser.hasMinecraftProfile()) {
                event.reply("You have already linked your Minecraft profile!").queue();
            } else {
                // TODO swap this to use the hypixel api and check social media
                discordUser.linkMinecraftProfile(username);

                UpdateResult result = discordUserRepository.saveToDatabase(discordUser);
                if (!result.wasAcknowledged() || result.getModifiedCount() == 0) {
                    event.reply("Failed to link your Minecraft profile!").queue();
                    return;
                }

                event.reply("Successfully linked your Minecraft profile to `" + discordUser.getMinecraftProfile().getUsername() + "`").queue();
            }
        }, () -> {
            event.reply("You don't seem to exist in our database!").queue();
        });
    }

}
