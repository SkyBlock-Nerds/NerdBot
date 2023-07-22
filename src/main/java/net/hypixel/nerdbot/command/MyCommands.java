package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.api.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.channel.ChannelManager;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.discord.SuggestionCache;

import java.awt.*;
import java.util.List;
import java.util.regex.Pattern;

@Log4j2
public class MyCommands extends ApplicationCommand {

    private static final Pattern DURATION = Pattern.compile("((\\d+)w)?((\\d+)d)?((\\d+)h)?((\\d+)m)?((\\d+)s)?");

    @JDASlashCommand(
        name = "link",
        description = "Link your Mojang Profile to your account."
    )
    public void linkProfile(GuildSlashEvent event, @AppOption(description = "Your Minecraft IGN to link.") String username) {
        event.deferReply(true).queue();
        java.util.Optional<MojangProfile> mojangProfile = updateMojangProfile(event, event.getMember(), username);

        if (mojangProfile.isPresent() && ChannelManager.getLogChannel() != null) {
            ChannelManager.getLogChannel().sendMessageEmbeds(
                new EmbedBuilder()
                    .setAuthor(event.getMember().getEffectiveName())
                    .setTitle("Mojang Profile Change")
                    .setThumbnail(event.getMember().getAvatarUrl())
                    .setDescription(event.getMember().getAsMention() + " updated their Mojang Profile.")
                    .addField("Username", mojangProfile.get().getUsername(), false)
                    .addField("UUID", mojangProfile.get().getUniqueId().toString(), false)
                    .build()
            ).queue();
        }
    }

    public static java.util.Optional<MojangProfile> updateMojangProfile(GuildSlashEvent event, Member member, String username) {
        Database database = NerdBotApp.getBot().getDatabase();
        DiscordUser discordUser = Util.getOrAddUserToCache(database, member.getId());
        java.util.Optional<MojangProfile> mojangProfile = Util.getMojangProfile(username);

        if (mojangProfile.isEmpty()) {
            event.getHook().sendMessage("Unable to locate Minecraft UUID for `" + username + "`.").queue();
            return java.util.Optional.empty();
        }

        username = mojangProfile.get().getUsername(); // Case-correction
        discordUser.setMojangProfile(mojangProfile.get());
        event.getHook().sendMessage("Updated your Mojang Profile to `" + username + "` (`" + mojangProfile.get().getUniqueId() + "`).").queue();

        if (!member.getEffectiveName().toLowerCase().contains(username.toLowerCase())) {
            member.modifyNickname(username).queue();
        }

        return mojangProfile;
    }

    @JDASlashCommand(name = "my", subcommand = "activity", description = "View your recent activity.")
    public void myActivity(GuildSlashEvent event) {
        event.deferReply(true).queue();
        Pair<EmbedBuilder, EmbedBuilder> activityEmbeds = getActivityEmbeds(event.getMember());
        event.getHook().editOriginalEmbeds(activityEmbeds.getLeft().build(), activityEmbeds.getRight().build()).queue();
    }

    @JDASlashCommand(name = "my", subcommand = "info", description = "View information about yourself")
    public void myInfo(GuildSlashEvent event) {
        event.deferReply(true).queue();
        Database database = NerdBotApp.getBot().getDatabase();
        DiscordUser discordUser = Util.getOrAddUserToCache(database, event.getMember().getId());

        String profile = discordUser.isProfileAssigned() ?
            discordUser.getMojangProfile().getUsername() + " (" + discordUser.getMojangProfile().getUniqueId().toString() + ")" :
            "*Missing Data*";

        event.getHook().editOriginalEmbeds(
            new EmbedBuilder()
                .setAuthor(event.getMember().getEffectiveName())
                .setThumbnail(event.getMember().getEffectiveAvatarUrl())
                .addField("ID", event.getMember().getId(), false)
                .addField("Mojang Profile", profile, false)
                .build()
        ).queue();
    }

    @JDASlashCommand(name = "my", subcommand = "suggestions", description = "View your suggestions.")
    public void mySuggestions(
        GuildSlashEvent event,
        @AppOption @Optional Integer page,
        @AppOption(description = "Tags to filter for (comma separated).") @Optional String tags,
        @AppOption(description = "Words to filter title for.") @Optional String title,
        @AppOption(description = "Toggle alpha suggestions.") @Optional Boolean alpha
    ) {
        event.deferReply(true).queue();
        page = (page == null) ? 1 : page;
        final int pageNum = Math.max(page, 1);
        final boolean isAlpha = (alpha != null && alpha);

        List<SuggestionCache.Suggestion> suggestions = SuggestionCommands.getSuggestions(event.getMember().getIdLong(), tags, title, isAlpha);

        if (suggestions.isEmpty()) {
            event.getHook().editOriginal("Found no suggestions matching the specified filters!").queue();
            return;
        }

        event.getHook().editOriginalEmbeds(
            SuggestionCommands.buildSuggestionsEmbed(suggestions, tags, title, isAlpha, pageNum, false)
                .setAuthor(event.getMember().getEffectiveName())
                .setThumbnail(event.getMember().getEffectiveAvatarUrl())
                .build()
        ).queue();
    }

    public static Pair<EmbedBuilder, EmbedBuilder> getActivityEmbeds(Member member) {
        Database database = NerdBotApp.getBot().getDatabase();
        DiscordUser discordUser = Util.getOrAddUserToCache(database, member.getId());

        LastActivity lastActivity = discordUser.getLastActivity();
        EmbedBuilder globalEmbedBuilder = new EmbedBuilder();
        EmbedBuilder alphaEmbedBuilder = new EmbedBuilder();

        // Global Activity
        globalEmbedBuilder.setColor(Color.GREEN)
            .setTitle("Last Global Activity")
            .addField("Most Recent", lastActivity.toRelativeTimestamp(LastActivity::getLastGlobalActivity), true)
            .addField("Voice Chat", lastActivity.toRelativeTimestamp(LastActivity::getLastVoiceChannelJoinDate), true)
            .addField("Item Generator", lastActivity.toRelativeTimestamp(LastActivity::getLastItemGenUsage), true)
            // Suggestions
            .addField("Created Suggestion", lastActivity.toRelativeTimestamp(LastActivity::getLastSuggestionDate), true)
            .addField("Voted on Suggestion", lastActivity.toRelativeTimestamp(LastActivity::getSuggestionVoteDate), true)
            .addField("New Comment", lastActivity.toRelativeTimestamp(LastActivity::getSuggestionCommentDate), true);

        // Alpha Activity
        alphaEmbedBuilder.setColor(Color.RED)
            .setTitle("Last Alpha Activity")
            .addField("Most Recent", lastActivity.toRelativeTimestamp(LastActivity::getLastAlphaActivity), true)
            .addField("Voice Chat", lastActivity.toRelativeTimestamp(LastActivity::getAlphaVoiceJoinDate), true)
            .addBlankField(true)
            // Suggestions
            .addField("Created Suggestion", lastActivity.toRelativeTimestamp(LastActivity::getLastAlphaSuggestionDate), true)
            .addField("Voted on Suggestion", lastActivity.toRelativeTimestamp(LastActivity::getAlphaSuggestionVoteDate), true)
            .addField("New Comment", lastActivity.toRelativeTimestamp(LastActivity::getAlphaSuggestionCommentDate), true);

        return Pair.of(globalEmbedBuilder, alphaEmbedBuilder);
    }

}
