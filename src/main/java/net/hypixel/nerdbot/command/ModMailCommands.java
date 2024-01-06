package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.cache.ChannelCache;
import net.hypixel.nerdbot.listener.ModMailListener;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.Util;

import java.util.Optional;
import java.util.stream.Stream;

@Log4j2
public class ModMailCommands extends ApplicationCommand {

    @JDASlashCommand(name = "modmail", subcommand = "find", description = "Find a Mod Mail thread", defaultLocked = true)
    public void findModMailThread(GuildSlashEvent event, @AppOption Member member) {
        event.deferReply(true).complete();

        ChannelCache.getModMailChannel().ifPresentOrElse(forumChannel -> {
            Optional<ThreadChannel> modMailThread = getModMailThread(member.getUser());
            if (modMailThread.isEmpty()) {
                event.getHook().editOriginal("Couldn't find a Mod Mail thread for " + member.getAsMention() + "!").queue();
                return;
            }

            event.getHook().editOriginal("Found Mod Mail thread for " + member.getAsMention() + ": " + modMailThread.get().getAsMention()).queue();
        }, () -> event.getHook().editOriginal("Couldn't find the Mod Mail channel!").queue());
    }

    @JDASlashCommand(name = "modmail", subcommand = "new", description = "Create a new Mod Mail thread for the specified member", defaultLocked = true)
    public void createNewModMail(GuildSlashEvent event, @AppOption Member member) {
        event.deferReply(true).complete();

        ChannelCache.getModMailChannel().ifPresentOrElse(forumChannel -> {
            Optional<ThreadChannel> modMailThread = getModMailThread(member.getUser());
            if (modMailThread.isPresent()) {
                event.getHook().editOriginal("A Mod Mail thread for " + member.getAsMention() + " already exists: " + modMailThread.get().getAsMention()).queue();
                return;
            }

            String expectedThreadName = ModMailListener.MOD_MAIL_TITLE_TEMPLATE.formatted(Util.getDisplayName(member.getUser()), member.getId());
            DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
            DiscordUser discordUser = discordUserRepository.findById(member.getId());

            if (discordUser == null) {
                discordUser = new DiscordUser(member);
                discordUserRepository.cacheObject(discordUser);
            }

            String username = discordUser.noProfileAssigned() ? "**Unlinked**" : discordUser.getMojangProfile().getUsername();
            String uniqueId = discordUser.noProfileAssigned() ? "**Unlinked**" : discordUser.getMojangProfile().getUniqueId().toString();
            String initialPost = "Created a Mod Mail request from " + member.getAsMention() + "!\n\n" +
                "User ID: " + member.getId() + "\n" +
                "Minecraft IGN: " + username + "\n" +
                "Minecraft UUID: " + uniqueId;

            ThreadChannel thread = forumChannel.createForumPost(expectedThreadName, MessageCreateData.fromContent(initialPost)).complete().getThreadChannel();
            thread.getManager().setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS).queue();

            String modMailRoleId = NerdBotApp.getBot().getConfig().getModMailConfig().getRoleId();

            RoleManager.getRoleById(modMailRoleId).ifPresent(role -> {
                thread.getGuild().getMembersWithRoles(role).forEach(m -> thread.addThreadMember(m).complete());
            });

            log.info("Forcefully created new Mod Mail thread for " + member.getId() + " (" + member.getEffectiveName() + ")");
            event.getHook().editOriginal("Created new Mod Mail thread for " + member.getAsMention() + ": " + thread.getAsMention()).queue();
        }, () -> event.getHook().editOriginal("Couldn't find the Mod Mail channel!").queue());
    }

    public Optional<ThreadChannel> getModMailThread(User user) {
        Optional<ForumChannel> optionalModMailChannel = ChannelCache.getModMailChannel();
        if (optionalModMailChannel.isEmpty()) {
            return Optional.empty();
        }

        ForumChannel modMailChannel = optionalModMailChannel.get();
        String expectedThreadName = ModMailListener.MOD_MAIL_TITLE_TEMPLATE.formatted(Util.getDisplayName(user), user.getId());
        Stream<ThreadChannel> archivedThreads = modMailChannel.retrieveArchivedPublicThreadChannels().stream();
        Optional<ThreadChannel> foundArchivedThread = archivedThreads
            .filter(channel -> channel.getName().matches(expectedThreadName))
            .findFirst();

        if (foundArchivedThread.isPresent()) {
            return foundArchivedThread;
        }

        Stream<ThreadChannel> activeThreads = modMailChannel.getThreadChannels().stream();
        return activeThreads
            .filter(thread -> !thread.isArchived())
            .filter(thread -> thread.getName().matches(expectedThreadName))
            .findFirst();
    }
}
