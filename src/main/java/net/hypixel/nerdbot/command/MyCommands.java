package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.CommandScope;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GlobalSlashEvent;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.BirthdayData;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.api.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.cache.SuggestionCache;
import net.hypixel.nerdbot.channel.ChannelManager;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.exception.HttpException;
import net.hypixel.nerdbot.util.exception.ProfileMismatchException;
import net.hypixel.nerdbot.util.gson.HypixelPlayerResponse;
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.commons.lang.time.DateUtils;

import java.awt.Color;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

@Log4j2
public class MyCommands extends ApplicationCommand {

    private static final Pattern DURATION = Pattern.compile("((\\d+)w)?((\\d+)d)?((\\d+)h)?((\\d+)m)?((\\d+)s)?");
    public static final Cache<String, MojangProfile> VERIFY_CACHE = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofDays(1L))
        .scheduler(Scheduler.systemScheduler())
        .build();

    @JDASlashCommand(
        name = "link",
        description = "Link your Mojang Profile to your account.",
        scope = CommandScope.GLOBAL
    )
    public void linkProfile(GlobalSlashEvent event, @AppOption(description = "Your Minecraft IGN to link.") String username) {
        event.deferReply(true).complete();
        Member member = Util.getMainGuild().retrieveMemberById(event.getUser().getId()).complete();

        if (member == null) {
            event.getHook().editOriginal("You must be in SkyBlock Nerds to use this command!").queue();
            return;
        }

        try {
            MojangProfile mojangProfile = requestMojangProfile(member, username, true);
            updateMojangProfile(member, mojangProfile);
            event.getHook().sendMessage("Updated your Mojang Profile to `" + mojangProfile.getUsername() + "` (`" + mojangProfile.getUniqueId() + "`).").queue();
            ChannelManager.getLogChannel().ifPresentOrElse(textChannel -> {
                textChannel.sendMessageEmbeds(
                        new EmbedBuilder()
                            .setTitle("Mojang Profile Link")
                            .setDescription(member.getAsMention() + " has linked their Mojang Profile.")
                            .setThumbnail(member.getEffectiveAvatarUrl())
                            .setColor(Color.GREEN)
                            .addField("Username", mojangProfile.getUsername(), false)
                            .addField(
                                "UUID / SkyCrypt",
                                String.format(
                                    "[%s](https://sky.shiiyu.moe/stats/%s)",
                                    mojangProfile.getUniqueId(),
                                    mojangProfile.getUniqueId()
                                ),
                                false
                            )
                            .build()
                    )
                    .queue();
            }, () -> {
                throw new RuntimeException("Log channel not found!");
            });

        } catch (HttpException httpex) {
            event.getHook().sendMessage("Unable to locate Minecraft UUID for `" + username + "`.").queue();
        } catch (ProfileMismatchException exception) {
            event.getHook().sendMessage(exception.getMessage()).queue();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @JDASlashCommand(
        name = "verify",
        description = "Send a request to link your Mojang Profile to your account."
    )
    public void requestLinkProfile(GuildSlashEvent event, @AppOption(description = "Your Minecraft IGN to link. Use the account you applied with.") String username) {
        event.deferReply(true).complete();

        if (VERIFY_CACHE.getIfPresent(event.getMember().getId()) != null) {
            event.getHook().sendMessage("Your previous verification request has not been reviewed. You will be contacted via DM if any further information is required.").queue();
            return;
        }

        try {
            MojangProfile mojangProfile = requestMojangProfile(event.getMember(), username, true);
            VERIFY_CACHE.put(event.getMember().getId(), mojangProfile);
            event.getHook().sendMessage("Your verification request has been sent. You will be contacted via DM if any further information is required.").queue();

            ChannelManager.getVerifyLogChannel().ifPresentOrElse(textChannel -> {
                textChannel.sendMessageEmbeds(
                        new EmbedBuilder()
                            .setTitle("Mojang Profile Verification")
                            .setDescription(event.getMember().getAsMention() + " has sent a mojang verification request. This discord account matches the social set for this Mojang Profile.")
                            .setColor(Color.PINK)
                            .setThumbnail(event.getMember().getEffectiveAvatarUrl())
                            .setFooter("This request expires in 1 day.")
                            .addField("Username", mojangProfile.getUsername(), false)
                            .addField(
                                "UUID / SkyCrypt",
                                String.format(
                                    "[%s](https://sky.shiiyu.moe/stats/%s)",
                                    mojangProfile.getUniqueId(),
                                    mojangProfile.getUniqueId()
                                ),
                                false
                            )
                            .build()
                    )
                    .addActionRow(
                        Button.of(
                            ButtonStyle.SUCCESS,
                            String.format(
                                "verification-accept-%s",
                                event.getMember().getId()
                            ),
                            "Accept"
                        ),
                        Button.of(
                            ButtonStyle.DANGER,
                            String.format(
                                "verification-deny-%s",
                                event.getMember().getId()
                            ),
                            "Deny"
                        )
                    )
                    .queue();
            }, () -> {
                throw new RuntimeException("Verification log channel not found!");
            });
        } catch (HttpException httpex) {
            event.getHook().sendMessage("Unable to locate Minecraft UUID for `" + username + "`.").queue();
        } catch (ProfileMismatchException exception) {
            event.getHook().sendMessage(exception.getMessage()).queue();
        }
    }

    @JDASlashCommand(
        name = "my",
        subcommand = "activity",
        description = "View your activity."
    )
    public void myActivity(GuildSlashEvent event) {
        event.deferReply(true).complete();
        Pair<EmbedBuilder, EmbedBuilder> activityEmbeds = getActivityEmbeds(event.getMember());
        event.getHook().editOriginalEmbeds(activityEmbeds.getLeft().build(), activityEmbeds.getRight().build()).queue();
    }

    @JDASlashCommand(
        name = "my",
        subcommand = "profile",
        description = "View your profile."
    )
    public void myInfo(GuildSlashEvent event) {
        event.deferReply(true).complete();
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        String profile = discordUser.isProfileAssigned() ?
            discordUser.getMojangProfile().getUsername() + " (" + discordUser.getMojangProfile().getUniqueId().toString() + ")" :
            "*Missing Data*";

        event.getHook().editOriginalEmbeds(
            new EmbedBuilder()
                .setAuthor(event.getMember().getEffectiveName() + " (" + event.getMember().getUser().getName() + ")")
                .setTitle("Your Profile")
                .setThumbnail(event.getMember().getEffectiveAvatarUrl())
                .setColor(event.getMember().getColor())
                .addField("ID", event.getMember().getId(), false)
                .addField("Mojang Profile", profile, false)
                .build()
        ).queue();
    }

    @JDASlashCommand(
        name = "my",
        subcommand = "suggestions",
        description = "View your suggestions."
    )
    public void mySuggestions(
        GuildSlashEvent event,
        @AppOption @Optional Integer page,
        @AppOption(description = "Tags to filter for (comma separated).") @Optional String tags,
        @AppOption(description = "Words to filter title for.") @Optional String title,
        @AppOption(description = "Toggle alpha suggestions.") @Optional Boolean alpha
    ) {
        event.deferReply(true).complete();
        page = (page == null) ? 1 : page;
        final int pageNum = Math.max(page, 1);
        final boolean isAlpha = (alpha != null && alpha);

        List<SuggestionCache.Suggestion> suggestions = SuggestionCommands.getSuggestions(event.getMember().getIdLong(), tags, title, isAlpha);

        if (suggestions.isEmpty()) {
            event.getHook().editOriginal("Found no suggestions matching the specified filters!").queue();
            return;
        }

        event.getHook().editOriginalEmbeds(
            SuggestionCommands.buildSuggestionsEmbed(suggestions, tags, title, isAlpha, pageNum, false, true)
                .setAuthor(event.getMember().getEffectiveName())
                .setThumbnail(event.getMember().getEffectiveAvatarUrl())
                .build()
        ).queue();
    }

    @JDASlashCommand(name = "birthday", subcommand = "remove", description = "Remove your birthday.")
    public void removeBirthday(GuildSlashEvent event) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser.getBirthdayData().getTimer() != null) {
            discordUser.getBirthdayData().getTimer().cancel();
        }

        discordUser.setBirthdayData(new BirthdayData());
        discordUserRepository.cacheObject(discordUser);

        event.getHook().editOriginal("Your birthday has been removed!").queue();
    }

    @JDASlashCommand(name = "birthday", subcommand = "get", description = "Get your birthday.")
    public void getBirthday(GuildSlashEvent event) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (!discordUser.getBirthdayData().isBirthdaySet()) {
            event.getHook().editOriginal("You have not set your birthday!").queue();
            return;
        }

        Date birthday = discordUser.getBirthdayData().getBirthday();
        event.getHook().editOriginal("Your birthday is currently set to " + "`" + DateFormatUtils.format(birthday, "dd MMMM yyyy") + "`!").queue();
    }

    @JDASlashCommand(name = "birthday", subcommand = "set", description = "Set your birthday.")
    public void setBirthday(GuildSlashEvent event, @AppOption(description = "Your birthday in the format MM/DD/YYYY.") String birthday, @AppOption(description = "Whether to announce your age.") @Optional Boolean announceAge) {
        event.deferReply(true).complete();

        try {
            Member member = event.getMember();
            DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
            DiscordUser discordUser = discordUserRepository.findById(member.getId());

            if (discordUser.getBirthdayData().getTimer() != null) {
                discordUser.getBirthdayData().getTimer().cancel();
            }

            discordUser.setBirthday(DateUtils.parseDate(birthday, new String[]{"MM/dd/yyyy"}));
            discordUser.getBirthdayData().setShouldAnnounceAge(announceAge != null && announceAge);
            discordUser.scheduleBirthdayReminder(discordUser.getBirthdayData().getBirthdayThisYear());
            discordUserRepository.cacheObject(discordUser);
            event.getHook().editOriginal("Your birthday has been set to `" + birthday + "`!").queue();
        } catch (Exception ex) {
            event.getHook().editOriginal("Encountered an error while parsing that date! Please try again or contact a bot developer!").queue();
            ex.printStackTrace();
        }
    }

    public static MojangProfile requestMojangProfile(Member member, String username, boolean enforceSocial) throws ProfileMismatchException, HttpException {
        MojangProfile mojangProfile = Util.getMojangProfile(username);
        HypixelPlayerResponse hypixelPlayerResponse = Util.getHypixelPlayer(mojangProfile.getUniqueId());

        if (!hypixelPlayerResponse.isSuccess()) {
            throw new HttpException("Unable to look up `" + mojangProfile.getUsername() + "`: " + hypixelPlayerResponse.getCause());
        }

        if (hypixelPlayerResponse.getPlayer().getSocialMedia() == null) {
            throw new ProfileMismatchException("The Hypixel profile for `" + mojangProfile.getUsername() + "` does not have any social media linked!");
        }

        String discord = hypixelPlayerResponse.getPlayer().getSocialMedia().getLinks().get(HypixelPlayerResponse.SocialMedia.Service.DISCORD);
        String discordName = member.getUser().getName();

        if (!member.getUser().getDiscriminator().equalsIgnoreCase("0")) {
            discordName += "#" + member.getUser().getDiscriminator();
        }

        if (enforceSocial && !discordName.equalsIgnoreCase(discord)) {
            throw new ProfileMismatchException("The Discord account `" + discordName + "` does not match the social media linked on the Hypixel profile for `" + mojangProfile.getUsername() + "`! It is currently set to `" + discord + "`");
        }

        return mojangProfile;
    }

    public static void updateMojangProfile(Member member, MojangProfile mojangProfile) throws HttpException {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(member.getId());
        discordUser.setMojangProfile(mojangProfile);

        if (!member.getEffectiveName().toLowerCase().contains(mojangProfile.getUsername().toLowerCase())) {
            try {
                member.modifyNickname(mojangProfile.getUsername()).queue();
            } catch (HierarchyException hex) {
                log.warn("Unable to modify the nickname of " + member.getUser().getName() + " (" + member.getEffectiveName() + ") [" + member.getId() + "], lacking hierarchy.");
            }
        }

        Guild guild = member.getGuild();
        String newMemberRoleId = NerdBotApp.getBot().getConfig().getRoleConfig().getNewMemberRoleId();
        java.util.Optional<Role> newMemberRole = java.util.Optional.empty();

        if (newMemberRoleId != null) {
            newMemberRole = java.util.Optional.ofNullable(guild.getRoleById(newMemberRoleId));
        }

        if (newMemberRole.isPresent()) {
            if (!RoleManager.hasHigherOrEqualRole(member, newMemberRole.get())) { // Ignore Existing Members
                try {
                    guild.addRoleToMember(member, newMemberRole.get()).complete();
                    String limboRoleId = NerdBotApp.getBot().getConfig().getRoleConfig().getLimboRoleId();

                    if (limboRoleId != null) {
                        Role limboRole = guild.getRoleById(limboRoleId);

                        if (limboRole != null) {
                            guild.removeRoleFromMember(member, limboRole).complete();
                        }
                    }
                } catch (HierarchyException hex) {
                    log.warn("Unable to assign " + newMemberRole.get().getName() + " role to " + member.getUser().getName() + " (" + member.getEffectiveName() + ") [" + member.getId() + "], lacking hierarchy.");
                }
            }
        } else {
            log.warn("Role with ID " + "" + " does not exist.");
        }
    }

    public static Pair<EmbedBuilder, EmbedBuilder> getActivityEmbeds(Member member) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(member.getId());

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
