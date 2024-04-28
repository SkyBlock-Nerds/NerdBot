package net.hypixel.skyblocknerds.discordbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Member;
import net.hypixel.skyblocknerds.api.SkyBlockNerdsAPI;
import net.hypixel.skyblocknerds.api.http.hypixel.HypixelPlayerDataResponse;
import net.hypixel.skyblocknerds.api.http.mojang.api.MojangUUIDResponse;
import net.hypixel.skyblocknerds.api.http.mojang.exception.MojangAPIException;
import net.hypixel.skyblocknerds.database.objects.user.DiscordUser;
import net.hypixel.skyblocknerds.database.repository.RepositoryManager;
import net.hypixel.skyblocknerds.database.repository.impl.DiscordUserRepository;
import net.hypixel.skyblocknerds.utilities.StringUtils;

import java.util.Optional;

@Log4j2
public class VerificationCommands extends ApplicationCommand {

    private final DiscordUserRepository discordUserRepository;

    public VerificationCommands() {
        this.discordUserRepository = RepositoryManager.getInstance().getRepository(DiscordUserRepository.class);
    }

    @JDASlashCommand(name = "verify", description = "Verify your Minecraft account to access the server", defaultLocked = true)
    public void verifyUser(GuildSlashEvent event, @AppOption(description = "Your Minecraft account's current username") String username) {
        event.deferReply(true).complete();

        Member member = event.getMember();
        Optional<DiscordUser> discordUser = discordUserRepository.findOrCreateById(member.getId(), member.getId());

        discordUser.ifPresentOrElse(user -> {
            try {
                MojangUUIDResponse uuidResponse = SkyBlockNerdsAPI.MOJANG_REQUEST.getUniqueId(username);
                HypixelPlayerDataResponse response = SkyBlockNerdsAPI.HYPIXEL_REQUEST.getPlayerData(uuidResponse.getUniqueId().toString());
                HypixelPlayerDataResponse.Player player = response.getPlayer();

                if (player == null) {
                    event.getHook().editOriginal("That Minecraft account has never logged on to the Hypixel Network!").queue();
                    return;
                }

                if (player.getSocialMedia() == null) {
                    event.getHook().editOriginal("Your Hypixel Profile does not have any social media linked to it!").queue();
                    return;
                }

                String discordUsername = player.getSocialMedia().getLinks().getDiscordUsername();

                if (discordUsername == null) {
                    event.getHook().editOriginal("Your Hypixel Profile is not linked to a Discord account!").queue();
                    return;
                }

                if (!discordUsername.equalsIgnoreCase(member.getUser().getName())) {
                    event.getHook().editOriginal("That Hypixel Profile is currently linked to the Discord account `" + discordUsername + "`! It must match your current username for you to be verified.").queue();
                    return;
                }

                user.linkMinecraftProfile(uuidResponse.getUniqueId(), uuidResponse.getUsername());
                event.getHook().editOriginal("Your SkyBlock Nerds Profile is now linked to the Minecraft account `" + uuidResponse.getUsername() + "`!").queue();
                log.info("Successfully verified the account of " + StringUtils.formatNameWithId(member.getUser().getName(), member.getId()) + " with the Minecraft account " + StringUtils.formatNameWithId(uuidResponse.getUsername(), uuidResponse.getUniqueId().toString()));
            } catch (MojangAPIException exception) {
                event.getHook().editOriginal(exception.getResponse().getReason()).complete();
            } catch (Exception exception) {
                event.getHook().editOriginal("An error occurred while trying to verify your account! Please try again later or contact us via Mod Mail").queue();
                log.error("An error occurred while trying to verify the account of " + StringUtils.formatNameWithId(member.getUser().getName(), member.getId()), exception);
            }
        }, () -> event.getHook().editOriginal("An error occurred while trying to verify your account, please try again later!").queue());
    }
}
