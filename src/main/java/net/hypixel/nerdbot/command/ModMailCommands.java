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
import net.hypixel.nerdbot.api.language.TranslationManager;
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

        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            TranslationManager.edit(event.getHook(), "database.not_connected");
            return;
        }

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findOrCreateById(event.getMember().getId());

        ChannelCache.getModMailChannel().ifPresentOrElse(forumChannel -> {
            Optional<ThreadChannel> modMailThread = getModMailThread(member.getUser());
            if (modMailThread.isEmpty()) {
                TranslationManager.edit(event.getHook(), discordUser, "commands.mod_mail.thread_not_found", member.getAsMention());
                return;
            }

            TranslationManager.edit(event.getHook(), discordUser, "commands.mod_mail.thread_found", member.getAsMention(), modMailThread.get().getAsMention());
        }, () -> TranslationManager.edit(event.getHook(), discordUser, "commands.mod_mail.channel_not_found"));
    }

    @JDASlashCommand(name = "modmail", subcommand = "new", description = "Create a new Mod Mail thread for the specified member", defaultLocked = true)
    public void createNewModMail(GuildSlashEvent event, @AppOption Member member) {
        event.deferReply(true).complete();

        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            TranslationManager.edit(event.getHook(), "database.not_connected");
            return;
        }

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser commandSender = discordUserRepository.findOrCreateById(event.getMember().getId());

        ChannelCache.getModMailChannel().ifPresentOrElse(forumChannel -> {
            Optional<ThreadChannel> modMailThread = getModMailThread(member.getUser());
            if (modMailThread.isPresent()) {
                TranslationManager.edit(event.getHook(), commandSender, "commands.mod_mail.already_exists", member.getAsMention(), modMailThread.get().getAsMention());
                return;
            }

            String expectedThreadName = ModMailListener.MOD_MAIL_TITLE_TEMPLATE.formatted(Util.getDisplayName(member.getUser()), member.getId());
            DiscordUser specifiedUser = discordUserRepository.findById(member.getId());

            if (specifiedUser == null) {
                specifiedUser = new DiscordUser(member);
                discordUserRepository.cacheObject(specifiedUser);
            }

            String username = specifiedUser.noProfileAssigned() ? "**Unlinked**" : specifiedUser.getMojangProfile().getUsername();
            String uniqueId = specifiedUser.noProfileAssigned() ? "**Unlinked**" : specifiedUser.getMojangProfile().getUniqueId().toString();
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
            TranslationManager.edit(event.getHook(), commandSender, "commands.mod_mail.created", member.getAsMention(), thread.getAsMention());
        }, () -> TranslationManager.edit(event.getHook(), commandSender, "commands.mod_mail.channel_not_found"));
    }

    public static Optional<ThreadChannel> getModMailThread(User user) {
        Optional<ForumChannel> optionalModMailChannel = ChannelCache.getModMailChannel();

        if (optionalModMailChannel.isEmpty()) {
            return Optional.empty();
        }

        ForumChannel modMailChannel = optionalModMailChannel.get();
        String expectedThreadName = ModMailListener.MOD_MAIL_TITLE_TEMPLATE.formatted(Util.getDisplayName(user), user.getId());
        Stream<ThreadChannel> archivedThreads = modMailChannel.retrieveArchivedPublicThreadChannels().stream();
        Optional<ThreadChannel> foundArchivedThread = archivedThreads
            .filter(channel -> channel.getName().equalsIgnoreCase(expectedThreadName))
            .findFirst();

        if (foundArchivedThread.isPresent()) {
            return foundArchivedThread;
        }

        Stream<ThreadChannel> activeThreads = modMailChannel.getThreadChannels().stream();
        return activeThreads.filter(thread -> thread.getName().equalsIgnoreCase(expectedThreadName)).findFirst();
    }
}
