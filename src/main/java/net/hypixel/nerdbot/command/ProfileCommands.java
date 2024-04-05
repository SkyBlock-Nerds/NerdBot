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
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.badge.Badge;
import net.hypixel.nerdbot.api.badge.BadgeManager;
import net.hypixel.nerdbot.api.badge.TieredBadge;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.badge.BadgeEntry;
import net.hypixel.nerdbot.api.database.model.user.birthday.BirthdayData;
import net.hypixel.nerdbot.api.database.model.user.language.UserLanguage;
import net.hypixel.nerdbot.api.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.api.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.api.language.TranslationManager;
import net.hypixel.nerdbot.cache.ChannelCache;
import net.hypixel.nerdbot.cache.suggestion.Suggestion;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.exception.HttpException;
import net.hypixel.nerdbot.util.exception.MojangProfileException;
import net.hypixel.nerdbot.util.exception.MojangProfileMismatchException;
import net.hypixel.nerdbot.util.gson.HypixelPlayerResponse;
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.commons.lang.time.DateUtils;

import java.awt.Color;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

@Log4j2
public class ProfileCommands extends ApplicationCommand {

    public static final Cache<String, MojangProfile> VERIFY_CACHE = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofDays(1L))
        .scheduler(Scheduler.systemScheduler())
        .build();
    private static final Pattern DURATION = Pattern.compile("((\\d+)w)?((\\d+)d)?((\\d+)h)?((\\d+)m)?((\\d+)s)?");

    public static MessageEmbed createBadgesEmbed(Member member, DiscordUser discordUser, boolean viewingSelf) {
        List<BadgeEntry> badges = discordUser.getBadges().stream()
            .sorted((o1, o2) -> {
                if (BadgeManager.isTieredBadge(o1.getBadgeId()) && BadgeManager.isTieredBadge(o2.getBadgeId())) {
                    TieredBadge tieredBadge1 = BadgeManager.getTieredBadgeById(o1.getBadgeId());
                    TieredBadge tieredBadge2 = BadgeManager.getTieredBadgeById(o2.getBadgeId());

                    if (o1.getTier() != null && o2.getTier() != null) {
                        return Integer.compare(tieredBadge2.getTier(o2.getTier()).getTier(), tieredBadge1.getTier(o1.getTier()).getTier());
                    } else if (o1.getTier() != null) {
                        return -1;
                    } else if (o2.getTier() != null) {
                        return 1;
                    }
                } else if (BadgeManager.isTieredBadge(o1.getBadgeId())) {
                    return -1;
                } else if (BadgeManager.isTieredBadge(o2.getBadgeId())) {
                    return 1;
                }

                return Long.compare(o2.getObtainedAt(), o1.getObtainedAt());
            })
            .toList();

        EmbedBuilder embedBuilder = new EmbedBuilder()
            .setTitle(viewingSelf ? "Your Badges" : (member.getEffectiveName().endsWith("s") ? member.getEffectiveName() + "'" : member.getEffectiveName() + "'s") + " Badges")
            .setColor(Color.PINK)
            .setDescription(badges.stream().map(badgeEntry -> {
                Badge badge = BadgeManager.getBadgeById(badgeEntry.getBadgeId());
                StringBuilder stringBuilder = new StringBuilder("**");

                if (BadgeManager.isTieredBadge(badgeEntry.getBadgeId()) && badgeEntry.getTier() != null && badgeEntry.getTier() >= 1) {
                    TieredBadge tieredBadge = BadgeManager.getTieredBadgeById(badgeEntry.getBadgeId());
                    stringBuilder.append(tieredBadge.getTier(badgeEntry.getTier()).getFormattedName());
                } else {
                    stringBuilder.append(badge.getFormattedName());
                }

                stringBuilder.append("**\nObtained on ").append(DateFormatUtils.format(badgeEntry.getObtainedAt(), "MMMM dd, yyyy"));
                return stringBuilder.toString();
            }).reduce((s, s2) -> s + "\n\n" + s2).orElse("No badges :("));

        if (discordUser.getBadges().isEmpty()) {
            embedBuilder.setImage("https://i.imgur.com/fs71kmJ.png");
        }

        return embedBuilder.build();
    }

    public static MojangProfile requestMojangProfile(Member member, String username, boolean enforceSocial) throws HttpException, MojangProfileException {
        MojangProfile mojangProfile = Util.getMojangProfile(username);

        if (mojangProfile.getErrorMessage() != null) {
            throw new MojangProfileException(mojangProfile.getErrorMessage());
        }

        HypixelPlayerResponse hypixelPlayerResponse = Util.getHypixelPlayer(mojangProfile.getUniqueId());

        if (!hypixelPlayerResponse.isSuccess()) {
            throw new HttpException("Unable to look up `" + mojangProfile.getUsername() + "`: " + hypixelPlayerResponse.getCause());
        }

        if (hypixelPlayerResponse.getPlayer().getSocialMedia() == null) {
            throw new MojangProfileMismatchException("The Hypixel profile for `" + mojangProfile.getUsername() + "` does not have any social media linked!");
        }

        String discord = hypixelPlayerResponse.getPlayer().getSocialMedia().getLinks().get(HypixelPlayerResponse.SocialMedia.Service.DISCORD);
        String discordName = member.getUser().getName();

        if (!member.getUser().getDiscriminator().equalsIgnoreCase("0000")) {
            discordName += "#" + member.getUser().getDiscriminator();
        }

        if (enforceSocial && !discordName.equalsIgnoreCase(discord)) {
            throw new MojangProfileMismatchException("The Discord account `" + discordName + "` does not match the social media linked on the Hypixel profile for `" + mojangProfile.getUsername() + "`! It is currently set to `" + discord + "`");
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

                    if (limboRoleId != null && !limboRoleId.isEmpty()) {
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
            log.warn("Role with ID " + newMemberRoleId + " does not exist.");
        }
    }

    public static List<MessageEmbed> getActivityEmbeds(Member member) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(member.getId());
        LastActivity lastActivity = discordUser.getLastActivity();

        return List.of(
            // General Activity
            new EmbedBuilder().setColor(Color.GREEN)
                .setTitle("General Activity")
                // General
                .addField("Last Seen", lastActivity.toRelativeTimestamp(LastActivity::getLastGlobalActivity), true)
                .addField("General Voice Chat", lastActivity.toRelativeTimestamp(LastActivity::getLastVoiceChannelJoinDate), true)
                .addField("Item Generator", lastActivity.toRelativeTimestamp(LastActivity::getLastItemGenUsage), true)
                // Project
                .addField("Projects", lastActivity.toRelativeTimestamp(LastActivity::getLastProjectActivity), true)
                .addField("Project Voice Chat", lastActivity.toRelativeTimestamp(LastActivity::getProjectVoiceJoinDate), true)
                .addBlankField(true)
                // Alpha
                .addField("Alpha", lastActivity.toRelativeTimestamp(LastActivity::getLastAlphaActivity), true)
                .addField("Alpha Voice Chat", lastActivity.toRelativeTimestamp(LastActivity::getAlphaVoiceJoinDate), true)
                .addBlankField(true)
                .build(),
            // Suggestions
            new EmbedBuilder().setColor(Color.YELLOW)
                .setTitle("Suggestion Activity")
                .addField("Last Created", lastActivity.toRelativeTimestamp(LastActivity::getSuggestionCreationHistory), true)
                .addField("Last Voted", lastActivity.toRelativeTimestamp(LastActivity::getSuggestionVoteHistory), true)
                .addField("Last Commented", lastActivity.toRelativeTimestamp(LastActivity::getSuggestionCommentHistory), true)
                .addField("Create History", String.format(
                    """
                    24 Hours: %s
                    7 Days: %s
                    30 Days: %s""",
                    lastActivity.toTotalPeriod(LastActivity::getSuggestionCreationHistory, Duration.of(24, ChronoUnit.HOURS)),
                    lastActivity.toTotalPeriod(LastActivity::getSuggestionCreationHistory, Duration.of(7, ChronoUnit.DAYS)),
                    lastActivity.toTotalPeriod(LastActivity::getSuggestionCreationHistory, Duration.of(30, ChronoUnit.DAYS))
                ), true)
                .addField("Vote History", String.format(
                    """
                    24 Hours: %s
                    7 Days: %s
                    30 Days: %s""",
                    lastActivity.toTotalPeriod(LastActivity::getSuggestionVoteHistory, Duration.of(24, ChronoUnit.HOURS)),
                    lastActivity.toTotalPeriod(LastActivity::getSuggestionVoteHistory, Duration.of(7, ChronoUnit.DAYS)),
                    lastActivity.toTotalPeriod(LastActivity::getSuggestionVoteHistory, Duration.of(30, ChronoUnit.DAYS))
                ), true)
                .addField("Comment History", String.format(
                    """
                    24 Hours: %s
                    7 Days: %s
                    30 Days: %s""",
                    lastActivity.toTotalPeriod(LastActivity::getSuggestionCommentHistory, Duration.of(24, ChronoUnit.HOURS)),
                    lastActivity.toTotalPeriod(LastActivity::getSuggestionCommentHistory, Duration.of(7, ChronoUnit.DAYS)),
                    lastActivity.toTotalPeriod(LastActivity::getSuggestionCommentHistory, Duration.of(30, ChronoUnit.DAYS))
                ), true)
                .build(),
            // Project Suggestion Activity
            new EmbedBuilder().setColor(Color.ORANGE)
                .setTitle("Project Suggestion Activity")
                .addField("Last Created", lastActivity.toRelativeTimestamp(LastActivity::getProjectSuggestionCreationHistory), true)
                .addField("Last Voted", lastActivity.toRelativeTimestamp(LastActivity::getProjectSuggestionVoteHistory), true)
                .addField("Last Commented", lastActivity.toRelativeTimestamp(LastActivity::getProjectSuggestionCommentHistory), true)
                .addField("Created History", String.format(
                    """
                    24 Hours: %s
                    7 Days: %s
                    30 Days: %s""",
                    lastActivity.toTotalPeriod(LastActivity::getProjectSuggestionCreationHistory, Duration.of(24, ChronoUnit.HOURS)),
                    lastActivity.toTotalPeriod(LastActivity::getProjectSuggestionCreationHistory, Duration.of(7, ChronoUnit.DAYS)),
                    lastActivity.toTotalPeriod(LastActivity::getProjectSuggestionCreationHistory, Duration.of(30, ChronoUnit.DAYS))
                ), true)
                .addField("Voted History", String.format(
                    """
                    24 Hours: %s
                    7 Days: %s
                    30 Days: %s""",
                    lastActivity.toTotalPeriod(LastActivity::getProjectSuggestionVoteHistory, Duration.of(24, ChronoUnit.HOURS)),
                    lastActivity.toTotalPeriod(LastActivity::getProjectSuggestionVoteHistory, Duration.of(7, ChronoUnit.DAYS)),
                    lastActivity.toTotalPeriod(LastActivity::getProjectSuggestionVoteHistory, Duration.of(30, ChronoUnit.DAYS))
                ), true)
                .addField("Commented History", String.format(
                    """
                    24 Hours: %s
                    7 Days: %s
                    30 Days: %s""",
                    lastActivity.toTotalPeriod(LastActivity::getProjectSuggestionCommentHistory, Duration.of(24, ChronoUnit.HOURS)),
                    lastActivity.toTotalPeriod(LastActivity::getProjectSuggestionCommentHistory, Duration.of(7, ChronoUnit.DAYS)),
                    lastActivity.toTotalPeriod(LastActivity::getProjectSuggestionCommentHistory, Duration.of(30, ChronoUnit.DAYS))
                ), true)
                .build(),
            // Alpha Suggestion Activity
            new EmbedBuilder().setColor(Color.RED)
                .setTitle("Alpha Suggestion Activity")
                .addField("Last Created", lastActivity.toRelativeTimestamp(LastActivity::getAlphaSuggestionCreationHistory), true)
                .addField("Last Voted", lastActivity.toRelativeTimestamp(LastActivity::getAlphaSuggestionVoteHistory), true)
                .addField("Last Commented", lastActivity.toRelativeTimestamp(LastActivity::getAlphaSuggestionCommentHistory), true)
                .addField("Create History", String.format(
                    """
                    24 Hours: %s
                    7 Days: %s
                    30 Days: %s""",
                    lastActivity.toTotalPeriod(LastActivity::getAlphaSuggestionCreationHistory, Duration.of(24, ChronoUnit.HOURS)),
                    lastActivity.toTotalPeriod(LastActivity::getAlphaSuggestionCreationHistory, Duration.of(7, ChronoUnit.DAYS)),
                    lastActivity.toTotalPeriod(LastActivity::getAlphaSuggestionCreationHistory, Duration.of(30, ChronoUnit.DAYS))
                ), true)
                .addField("Vote History", String.format(
                    """
                    24 Hours: %s
                    7 Days: %s
                    30 Days: %s""",
                    lastActivity.toTotalPeriod(LastActivity::getAlphaSuggestionVoteHistory, Duration.of(24, ChronoUnit.HOURS)),
                    lastActivity.toTotalPeriod(LastActivity::getAlphaSuggestionVoteHistory, Duration.of(7, ChronoUnit.DAYS)),
                    lastActivity.toTotalPeriod(LastActivity::getAlphaSuggestionVoteHistory, Duration.of(30, ChronoUnit.DAYS))
                ), true)
                .addField("Comment History", String.format(
                    """
                    24 Hours: %s
                    7 Days: %s
                    30 Days: %s""",
                    lastActivity.toTotalPeriod(LastActivity::getAlphaSuggestionCommentHistory, Duration.of(24, ChronoUnit.HOURS)),
                    lastActivity.toTotalPeriod(LastActivity::getAlphaSuggestionCommentHistory, Duration.of(7, ChronoUnit.DAYS)),
                    lastActivity.toTotalPeriod(LastActivity::getAlphaSuggestionCommentHistory, Duration.of(30, ChronoUnit.DAYS))
                ), true)
                .build()
        );
    }

    @JDASlashCommand(
        name = "verify",
        description = "Send a request to link your Mojang Profile to your account."
    )
    public void requestLinkProfile(GuildSlashEvent event, @AppOption(description = "Your Minecraft IGN to link. Use the account you applied with.") String username) {
        event.deferReply(true).complete();

        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            TranslationManager.edit(event.getHook(), "database.not_connected");
            return;
        }

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findOrCreateById(event.getMember().getId());

        if (VERIFY_CACHE.getIfPresent(event.getMember().getId()) != null) {
            TranslationManager.edit(event.getHook(), discordUser, "commands.verify.already_requested");
            return;
        }

        try {
            MojangProfile mojangProfile = requestMojangProfile(event.getMember(), username, true);

            if (mojangProfile.getErrorMessage() != null) {
                throw new MojangProfileException(mojangProfile.getErrorMessage());
            }

            VERIFY_CACHE.put(event.getMember().getId(), mojangProfile);
            TranslationManager.edit(event.getHook(), discordUser, "commands.verify.request_sent");

            ChannelCache.getVerifyLogChannel().ifPresentOrElse(textChannel -> textChannel.sendMessageEmbeds(
                    new EmbedBuilder()
                        .setTitle("Mojang Profile Verification")
                        .setDescription(event.getMember().getAsMention() + " has sent a Mojang verification request. This discord account matches the social set for this Mojang Profile.")
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
                .queue(), () -> {
                log.warn("Profile verification log channel not found!");
            });
        } catch (HttpException | MojangProfileException exception) {
            event.getHook().sendMessage(exception.getMessage()).queue();
        } catch (Exception exception) {
            log.error("Encountered an error while requesting verification for " + event.getMember().getUser().getName() + " (ID: " + event.getMember().getId() + ") with username " + username + "!", exception);
            TranslationManager.edit(event.getHook(), discordUser, "commands.verify.error");
        }
    }

    @JDASlashCommand(
        name = "profile",
        subcommand = "link",
        description = "Change your linked Mojang Profile.",
        scope = CommandScope.GLOBAL
    )
    public void linkProfile(GlobalSlashEvent event, @AppOption(description = "Your Minecraft IGN to link.") String username) {
        event.deferReply(true).complete();

        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            TranslationManager.edit(event.getHook(), "database.not_connected");
            return;
        }

        Member member = Util.getMainGuild().retrieveMemberById(event.getUser().getId()).complete();
        if (member == null) {
            event.getHook().editOriginal("You must be in SkyBlock Nerds to use this command!").queue();
            return;
        }

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findOrCreateById(event.getUser().getId());

        try {
            MojangProfile mojangProfile = requestMojangProfile(member, username, true);
            updateMojangProfile(member, mojangProfile);

            TranslationManager.edit(event.getHook(), discordUser, "commands.verify.profile_updated", mojangProfile.getUsername(), mojangProfile.getUniqueId());

            ChannelCache.getLogChannel().ifPresentOrElse(textChannel -> {
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
            }, () -> log.warn("Log channel not found!"));

        } catch (HttpException | MojangProfileMismatchException exception) {
            event.getHook().sendMessage(exception.getMessage()).queue();
        } catch (Exception exception) {
            log.error("Encountered an error while linking " + member.getUser().getName() + " (ID: " + member.getId() + ") to " + username + "!", exception);
        }
    }

    @JDASlashCommand(
        name = "profile",
        subcommand = "activity",
        description = "View your activity."
    )
    public void myActivity(GuildSlashEvent event) {
        event.deferReply(true).complete();

        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            TranslationManager.edit(event.getHook(), "database.not_connected");
            return;
        }

        List<MessageEmbed> activityEmbeds = getActivityEmbeds(event.getMember());
        event.getHook().editOriginalEmbeds(activityEmbeds.toArray(new MessageEmbed[]{})).queue();
    }

    @JDASlashCommand(
        name = "profile",
        subcommand = "view",
        description = "View your profile."
    )
    public void myInfo(GuildSlashEvent event) {
        event.deferReply(true).complete();

        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            TranslationManager.edit(event.getHook(), "database.not_connected");
            return;
        }

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        String profile = discordUser.isProfileAssigned() ?
            discordUser.getMojangProfile().getUsername() + " (" + discordUser.getMojangProfile().getUniqueId().toString() + ")" :
            "*Missing Data*";

        event.getHook().editOriginalEmbeds(
            new EmbedBuilder()
                .setAuthor(event.getMember().getEffectiveName() + " (ID: " + event.getMember().getId() + ")")
                .setTitle("Your Profile")
                .setThumbnail(event.getMember().getEffectiveAvatarUrl())
                .setColor(event.getMember().getColor())
                .addField("Mojang Profile", profile, false)
                .addField("Badges", discordUser.getBadges().isEmpty() ? "None" : String.valueOf(discordUser.getBadges().size()), true)
                .addField("Language", discordUser.getLanguage().getName(), true)
                .addField("Birthday", (discordUser.getBirthdayData().isBirthdaySet() ? DateFormatUtils.format(discordUser.getBirthdayData().getBirthday(), "dd MMMM yyyy") : "Not Set"), true)
                .build()
        ).queue();
    }

    @JDASlashCommand(
        name = "profile",
        subcommand = "badges",
        description = "View your badges."
    )
    public void myBadges(GuildSlashEvent event, @AppOption @Optional Boolean showPublicly) {
        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            TranslationManager.edit(event.getHook(), "database.not_connected");
            return;
        }

        showPublicly = (showPublicly == null || !showPublicly);
        event.deferReply(showPublicly).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        event.getHook().editOriginalEmbeds(createBadgesEmbed(event.getMember(), discordUser, true)).queue();
    }

    @JDASlashCommand(
        name = "profile",
        subcommand = "suggestions",
        description = "View your suggestions."
    )
    public void mySuggestions(
        GuildSlashEvent event,
        @AppOption @Optional Integer page,
        @AppOption(description = "Tags to filter for (comma separated).") @Optional String tags,
        @AppOption(description = "Words to filter title for.") @Optional String title,
        @AppOption(description = "Show suggestions from a specific category.", autocomplete = "suggestion-types") @Optional Suggestion.ChannelType type
    ) {
        event.deferReply(true).complete();

        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            TranslationManager.edit(event.getHook(), "database.not_connected");
            return;
        }

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findOrCreateById(event.getMember().getId());

        page = (page == null) ? 1 : page;
        final int pageNum = Math.max(page, 1);
        type = (type == null ? Suggestion.ChannelType.NORMAL : type);

        List<Suggestion> suggestions = SuggestionCommands.getSuggestions(event.getMember(), event.getMember().getIdLong(), tags, title, type);

        if (suggestions.isEmpty()) {
            TranslationManager.edit(event.getHook(), discordUser, "cache.suggestions.filtered_none_found");
            return;
        }

        event.getHook().editOriginalEmbeds(
            SuggestionCommands.buildSuggestionsEmbed(event.getMember(), suggestions, tags, title, type, pageNum, false, true)
                .setAuthor(event.getMember().getEffectiveName())
                .setThumbnail(event.getMember().getEffectiveAvatarUrl())
                .build()
        ).queue();
    }

    @JDASlashCommand(
        name = "profile",
        group = "birthday",
        subcommand = "remove",
        description = "Remove your birthday."
    )
    public void removeBirthday(GuildSlashEvent event) {
        event.deferReply(true).complete();

        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            TranslationManager.edit(event.getHook(), "database.not_connected");
            return;
        }

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser.getBirthdayData().getTimer() != null) {
            discordUser.getBirthdayData().getTimer().cancel();
        }

        discordUser.setBirthdayData(new BirthdayData());
        discordUserRepository.cacheObject(discordUser);

        TranslationManager.edit(event.getHook(), discordUser, "commands.birthday.removed");
    }

    @JDASlashCommand(
        name = "profile",
        group = "birthday",
        subcommand = "set",
        description = "Set your birthday."
    )
    public void setBirthday(GuildSlashEvent event, @AppOption(description = "Your birthday in the format MM/DD/YYYY.") String birthday, @AppOption(description = "Whether to announce your age.") @Optional Boolean announceAge) {
        event.deferReply(true).complete();

        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            TranslationManager.edit(event.getHook(), "database.not_connected");
            return;
        }

        Member member = event.getMember();
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(member.getId());

        try {
            BirthdayData birthdayData = discordUser.getBirthdayData();

            if (birthdayData.getTimer() != null) {
                birthdayData.getTimer().cancel();
            }

            Date date = DateUtils.parseDate(birthday, new String[]{"MM/dd/yyyy"});
            discordUser.setBirthday(date);
            birthdayData.setShouldAnnounceAge(announceAge != null && announceAge);
            discordUser.scheduleBirthdayReminder(birthdayData.getBirthdayThisYear());
            TranslationManager.edit(event.getHook(), discordUser, "commands.birthday.set", DateFormatUtils.format(date, "dd MMMM yyyy"));
        } catch (Exception exception) {
            TranslationManager.edit(event.getHook(), discordUser, "commands.birthday.bad_date");
        }
    }

    @JDASlashCommand(
        name = "profile",
        subcommand = "language",
        description = "Set your language."
    )
    public void setLanguage(GuildSlashEvent event, @AppOption(autocomplete = "languages") UserLanguage language) {
        event.deferReply(true).complete();

        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            TranslationManager.edit(event.getHook(), "database.not_connected");
            return;
        }

        DiscordUserRepository repository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser user = repository.findById(event.getMember().getId());

        if (user == null) {
            TranslationManager.edit(event.getHook(), "generic.user_not_found");
            return;
        }

        user.setLanguage(language);
        TranslationManager.edit(event.getHook(), user, "commands.language.language_set", language.getName());
    }
}
