package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.channel.ChannelManager;
import net.hypixel.nerdbot.listener.ModMailListener;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.Util;

import java.util.Optional;

public class ModMailCommands extends ApplicationCommand {

    @JDASlashCommand(name = "modmail", subcommand = "find", description = "Find a Mod Mail thread", defaultLocked = true)
    public void findModMailThread(GuildSlashEvent event, @AppOption Member member) {
        event.deferReply(true).complete();

        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            event.getHook().editOriginal("The database is not connected!").queue();
        }

        ForumChannel forumChannel = ChannelManager.getModMailChannel();
        if (forumChannel == null) {
            event.getHook().editOriginal("Couldn't find the Mod Mail channel!").queue();
            return;
        }

        Optional<ThreadChannel> modMailThread = getModMailThread(member.getUser());
        if (modMailThread.isEmpty()) {
            event.getHook().editOriginal("Couldn't find a Mod Mail thread for " + member.getAsMention() + "!").queue();
            return;
        }

        event.getHook().editOriginal("Found Mod Mail thread for " + member.getAsMention() + ": " + modMailThread.get().getAsMention()).queue();
    }

    @JDASlashCommand(name = "modmail", subcommand = "new", description = "Create a new Mod Mail thread for the specified member", defaultLocked = true)
    public void createNewModMail(GuildSlashEvent event, @AppOption Member member) {
        event.deferReply(true).complete();

        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            event.getHook().editOriginal("The database is not connected!").queue();
        }

        ForumChannel modMailChannel = ChannelManager.getModMailChannel();
        if (modMailChannel == null) {
            event.getHook().editOriginal("Couldn't find the Mod Mail channel!").queue();
            return;
        }

        Optional<ThreadChannel> optional = getModMailThread(member.getUser());
        if (optional.isPresent()) {
            event.getHook().editOriginal("A Mod Mail thread for " + member.getAsMention() + " already exists: " + optional.get().getAsMention()).queue();
            return;
        }

        String expectedThreadName = ModMailListener.MOD_MAIL_TITLE_TEMPLATE.formatted(Util.getDisplayName(member.getUser()), member.getId());
        DiscordUser discordUser = Util.getOrAddUserToCache(NerdBotApp.getBot().getDatabase(), member.getId());
        String username = discordUser.noProfileAssigned() ? "**Unlinked**" : discordUser.getMojangProfile().getUsername();
        String uniqueId = discordUser.noProfileAssigned() ? "**Unlinked**" : discordUser.getMojangProfile().getUniqueId().toString();
        String initialPost = "Forcefully created a Mod Mail request from " + member.getAsMention() + "!\n\n" +
            "User ID: " + member.getId() + "\n" +
            "Minecraft IGN: " + username + "\n" +
            "Minecraft UUID: " + uniqueId;

        ThreadChannel thread = modMailChannel.createForumPost(expectedThreadName, MessageCreateData.fromContent(initialPost)).complete().getThreadChannel();
        thread.getManager().setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS).queue();

        String modMailRoleId = NerdBotApp.getBot().getConfig().getModMailConfig().getRoleId();
        Role role = RoleManager.getRoleById(modMailRoleId);
        if (role != null) {
            thread.getGuild().getMembersWithRoles(RoleManager.getRoleById(modMailRoleId)).forEach(m -> thread.addThreadMember(m).complete());
        }

        event.getHook().editOriginal("Created new Mod Mail thread for " + member.getAsMention() + ": " + thread.getAsMention()).queue();
    }

    public Optional<ThreadChannel> getModMailThread(User user) {
        if (ChannelManager.getModMailChannel() == null) {
            return Optional.empty();
        }

        String expectedThreadName = ModMailListener.MOD_MAIL_TITLE_TEMPLATE.formatted(Util.getDisplayName(user), user.getId());

        return ChannelManager.getModMailChannel().retrieveArchivedPublicThreadChannels().stream()
            .filter(channel -> channel.getName().matches(expectedThreadName))
            .findFirst()
            .or(() -> ChannelManager.getModMailChannel().getThreadChannels().stream()
                .filter(thread -> !thread.isArchived())
                .filter(thread -> thread.getName().matches(expectedThreadName))
                .findFirst()
            );
    }
}
