package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.CommandScope;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GlobalSlashEvent;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.freya02.botcommands.api.application.slash.autocomplete.annotations.AutocompletionHandler;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
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
import net.hypixel.nerdbot.bot.config.RoleConfig;
import net.hypixel.nerdbot.bot.config.objects.RoleRestrictedChannelGroup;
import net.hypixel.nerdbot.cache.ChannelCache;
import net.hypixel.nerdbot.cache.suggestion.Suggestion;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.DiscordUtils;
import net.hypixel.nerdbot.util.HttpUtils;
import net.hypixel.nerdbot.util.exception.HttpException;
import net.hypixel.nerdbot.util.json.http.HypixelPlayerResponse;
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.commons.lang.time.DateUtils;

import java.awt.Color;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

@Slf4j
public class ProfileCommands extends ApplicationCommand {

    public static final Cache<String, MojangProfile> VERIFY_CACHE = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofDays(1L))
        .scheduler(Scheduler.systemScheduler())
        .build();
    private static final Pattern DURATION = Pattern.compile("((\\d+)w)?((\\d+)d)?((\\d+)h)?((\\d+)m)?((\\d+)s)?");

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

        discordUserRepository.findOrCreateByIdAsync(event.getMember().getId())
            .thenCompose(discordUser -> {
                if (VERIFY_CACHE.getIfPresent(event.getMember().getId()) != null) {
                    TranslationManager.edit(event.getHook(), discordUser, "commands.verify.already_requested");
                    return CompletableFuture.completedFuture(null);
                }

                return requestMojangProfileAsync(event.getMember(), username, true)
                    .thenAccept(mojangProfile -> {
                        if (mojangProfile == null) {
                            return;
                        }

                        if (mojangProfile.getErrorMessage() != null) {
                            event.getHook().editOriginal(mojangProfile.getErrorMessage()).queue();
                            return;
                        }

                        VERIFY_CACHE.put(event.getMember().getId(), mojangProfile);
                        TranslationManager.edit(event.getHook(), discordUser, "commands.verify.request_sent");

                        ChannelCache.getVerifyLogChannel().ifPresentOrElse(textChannel -> textChannel.sendMessage("<@&" + NerdBotApp.getBot().getConfig().getRoleConfig().getModeratorRoleId() + ">").addEmbeds(
                                new EmbedBuilder()
                                    .appendDescription("<@&" + NerdBotApp.getBot().getConfig().getRoleConfig().getModeratorRoleId() + ">")
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
                    });
            })
            .exceptionally(throwable -> {
                log.error("Error during profile verification request", throwable);
                event.getHook().editOriginal("Failed to process verification request: " + throwable.getMessage()).queue();
                return null;
            });
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

        DiscordUtils.getMainGuild().retrieveMemberById(event.getUser().getId())
            .submit()
            .thenCompose(member -> {
                if (member == null) {
                    event.getHook().editOriginal("You must be in SkyBlock Nerds to use this command!").queue();
                    return CompletableFuture.completedFuture(null);
                }

                DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
                return discordUserRepository.findOrCreateByIdAsync(event.getUser().getId())
                    .thenCompose(discordUser ->
                        requestMojangProfileAsync(member, username, true)
                            .thenAccept(mojangProfile -> {
                                if (mojangProfile == null) {
                                    return;
                                }

                                if (mojangProfile.getErrorMessage() != null) {
                                    event.getHook().editOriginal(mojangProfile.getErrorMessage()).queue();
                                    return;
                                }

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
                            })
                    );
            })
            .exceptionally(throwable -> {
                log.error("Error during profile linking", throwable);
                event.getHook().editOriginal("Failed to link profile: " + throwable.getMessage()).queue();
                return null;
            });
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

        discordUserRepository.findByIdAsync(event.getMember().getId())
            .thenAccept(discordUser -> {
                if (discordUser == null) {
                    TranslationManager.edit(event.getHook(), "generic.user_not_found");
                    return;
                }

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
            })
            .exceptionally(throwable -> {
                log.error("Error loading user profile", throwable);
                event.getHook().editOriginal("Failed to load profile: " + throwable.getMessage()).queue();
                return null;
            });
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

        discordUserRepository.findByIdAsync(event.getMember().getId())
            .thenAccept(discordUser -> {
                if (discordUser == null) {
                    TranslationManager.edit(event.getHook(), "generic.user_not_found");
                    return;
                }

                event.getHook().editOriginalEmbeds(createBadgesEmbed(event.getMember(), discordUser, true)).queue();
            })
            .exceptionally(throwable -> {
                log.error("Error loading user badges", throwable);
                event.getHook().editOriginal("Failed to load badges: " + throwable.getMessage()).queue();
                return null;
            });
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

        discordUserRepository.findOrCreateByIdAsync(event.getMember().getId())
            .thenAccept(discordUser -> {
                final int pageNum = Math.max(page == null ? 1 : page, 1);
                final Suggestion.ChannelType finalType = (type == null ? Suggestion.ChannelType.NORMAL : type);

                List<Suggestion> suggestions = SuggestionCommands.getSuggestions(event.getMember(), event.getMember().getIdLong(), tags, title, finalType);

                if (suggestions.isEmpty()) {
                    TranslationManager.edit(event.getHook(), discordUser, "cache.suggestions.filtered_none_found");
                    return;
                }

                event.getHook().editOriginalEmbeds(
                    SuggestionCommands.buildSuggestionsEmbed(event.getMember(), suggestions, tags, title, finalType, pageNum, false, true)
                        .setAuthor(event.getMember().getEffectiveName())
                        .setThumbnail(event.getMember().getEffectiveAvatarUrl())
                        .build()
                ).queue();
            })
            .exceptionally(throwable -> {
                log.error("Error loading user suggestions", throwable);
                event.getHook().editOriginal("Failed to load suggestions: " + throwable.getMessage()).queue();
                return null;
            });
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

        discordUserRepository.findByIdAsync(event.getMember().getId())
            .thenAccept(discordUser -> {
                if (discordUser == null) {
                    TranslationManager.edit(event.getHook(), "generic.user_not_found");
                    return;
                }

                if (discordUser.getBirthdayData().getTimer() != null) {
                    discordUser.getBirthdayData().getTimer().cancel();
                }

                discordUser.setBirthdayData(new BirthdayData());
                discordUserRepository.cacheObject(discordUser);

                TranslationManager.edit(event.getHook(), discordUser, "commands.birthday.removed");
            })
            .exceptionally(throwable -> {
                log.error("Error removing birthday", throwable);
                event.getHook().editOriginal("Failed to remove birthday: " + throwable.getMessage()).queue();
                return null;
            });
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

        discordUserRepository.findByIdAsync(member.getId())
            .thenAccept(discordUser -> {
                if (discordUser == null) {
                    TranslationManager.edit(event.getHook(), "generic.user_not_found");
                    return;
                }

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
            })
            .exceptionally(throwable -> {
                log.error("Error setting birthday", throwable);
                event.getHook().editOriginal("Failed to set birthday: " + throwable.getMessage()).queue();
                return null;
            });
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

        repository.findByIdAsync(event.getMember().getId())
            .thenAccept(user -> {
                if (user == null) {
                    TranslationManager.edit(event.getHook(), "generic.user_not_found");
                    return;
                }

                user.setLanguage(language);
                TranslationManager.edit(event.getHook(), user, "commands.language.language_set", language.getName());
            })
            .exceptionally(throwable -> {
                log.error("Error setting language", throwable);
                event.getHook().editOriginal("Failed to set language: " + throwable.getMessage()).queue();
                return null;
            });
    }

    @AutocompletionHandler(name = "languages")
    public List<UserLanguage> getLanguages(CommandAutoCompleteInteractionEvent event) {
        return List.of(UserLanguage.VALUES);
    }

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

    public static CompletableFuture<MojangProfile> requestMojangProfileAsync(Member member, String username, boolean enforceSocial) {
        return HttpUtils.getMojangProfileAsync(username)
            .thenCompose(mojangProfile -> {
                if (mojangProfile == null) {
                    return CompletableFuture.completedFuture(null);
                }

                if (mojangProfile.getErrorMessage() != null) {
                    MojangProfile errorProfile = new MojangProfile();
                    errorProfile.setErrorMessage(mojangProfile.getErrorMessage());
                    return CompletableFuture.completedFuture(errorProfile);
                }

                return HttpUtils.getHypixelPlayerAsync(mojangProfile.getUniqueId())
                    .thenApply(hypixelPlayerResponse -> {
                        if (!hypixelPlayerResponse.isSuccess()) {
                            MojangProfile errorProfile = new MojangProfile();
                            errorProfile.setErrorMessage("Unable to look up `" + mojangProfile.getUsername() + "`: " + hypixelPlayerResponse.getCause());
                            return errorProfile;
                        }

                        if (hypixelPlayerResponse.getPlayer().getSocialMedia() == null) {
                            MojangProfile errorProfile = new MojangProfile();
                            errorProfile.setErrorMessage("The Hypixel profile for `" + mojangProfile.getUsername() + "` does not have any social media linked!");
                            return errorProfile;
                        }

                        String discord = hypixelPlayerResponse.getPlayer().getSocialMedia().getLinks().get(HypixelPlayerResponse.SocialMedia.Service.DISCORD);
                        String discordName = member.getUser().getName();

                        if (!member.getUser().getDiscriminator().equalsIgnoreCase("0000")) {
                            discordName += "#" + member.getUser().getDiscriminator();
                        }

                        if (enforceSocial && !discordName.equalsIgnoreCase(discord)) {
                            MojangProfile errorProfile = new MojangProfile();
                            errorProfile.setErrorMessage("The Discord account `" + discordName + "` does not match the social media linked on the Hypixel profile for `" + mojangProfile.getUsername() + "`! It is currently set to `" + discord + "`");
                            return errorProfile;
                        }

                        return mojangProfile;
                    });
            });
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

        List<MessageEmbed> embeds = new ArrayList<>();

        RoleConfig roleConfig = NerdBotApp.getBot().getConfig().getRoleConfig();
        int inactivityCheckDays = roleConfig.getDaysRequiredForInactivityCheck();
        int requiredMessages = roleConfig.getMessagesRequiredForInactivityCheck();
        int requiredVotes = roleConfig.getVotesRequiredForInactivityCheck();
        int requiredComments = roleConfig.getCommentsRequiredForInactivityCheck();

        int promotionDays = roleConfig.getDaysRequiredForVoteHistory();
        int promotionVotes = roleConfig.getMinimumVotesRequiredForPromotion();
        int promotionComments = roleConfig.getMinimumCommentsRequiredForPromotion();

        // General Activity
        embeds.add(new EmbedBuilder().setColor(Color.GREEN)
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
            .setFooter(String.format(
                "Activity Requirements (per %d days): %d messages, %d votes, %d comments",
                inactivityCheckDays, requiredMessages, requiredVotes, requiredComments
            ))
            .build());

        // Suggestions
        embeds.add(new EmbedBuilder().setColor(Color.YELLOW)
            .setTitle("Suggestion Activity")
            .addField("Last Created", lastActivity.toRelativeTimestampList(LastActivity::getSuggestionCreationHistory), true)
            .addField("Last Voted", lastActivity.toRelativeTimestampMap(LastActivity::getSuggestionVoteHistoryMap), true)
            .addField("Last Commented", lastActivity.toRelativeTimestampList(LastActivity::getSuggestionCommentHistory), true)
            .addField("Create History", String.format(
                """
                24 Hours: %s
                7 Days: %s
                30 Days: %s""",
                lastActivity.toTotalPeriodList(LastActivity::getSuggestionCreationHistory, Duration.of(24, ChronoUnit.HOURS)),
                lastActivity.toTotalPeriodList(LastActivity::getSuggestionCreationHistory, Duration.of(7, ChronoUnit.DAYS)),
                lastActivity.toTotalPeriodList(LastActivity::getSuggestionCreationHistory, Duration.of(30, ChronoUnit.DAYS))
            ), true)
            .addField("Vote History", String.format(
                """
                24 Hours: %s
                7 Days: %s
                30 Days: %s""",
                lastActivity.toTotalPeriodMap(LastActivity::getSuggestionVoteHistoryMap, Duration.of(24, ChronoUnit.HOURS)),
                lastActivity.toTotalPeriodMap(LastActivity::getSuggestionVoteHistoryMap, Duration.of(7, ChronoUnit.DAYS)),
                lastActivity.toTotalPeriodMap(LastActivity::getSuggestionVoteHistoryMap, Duration.of(30, ChronoUnit.DAYS))
            ), true)
            .addField("Comment History", String.format(
                """
                24 Hours: %s
                7 Days: %s
                30 Days: %s""",
                lastActivity.toTotalPeriodList(LastActivity::getSuggestionCommentHistory, Duration.of(24, ChronoUnit.HOURS)),
                lastActivity.toTotalPeriodList(LastActivity::getSuggestionCommentHistory, Duration.of(7, ChronoUnit.DAYS)),
                lastActivity.toTotalPeriodList(LastActivity::getSuggestionCommentHistory, Duration.of(30, ChronoUnit.DAYS))
            ), true)
            .setFooter(String.format(
                "Promotion Requirements (per %d days): %d votes, %d comments",
                promotionDays, promotionVotes, promotionComments
            ))
            .build());

        // Project Suggestion Activity
        embeds.add(new EmbedBuilder().setColor(Color.ORANGE)
            .setTitle("Project Suggestion Activity")
            .addField("Last Created", lastActivity.toRelativeTimestampList(LastActivity::getProjectSuggestionCreationHistory), true)
            .addField("Last Voted", lastActivity.toRelativeTimestampMap(LastActivity::getProjectSuggestionVoteHistoryMap), true)
            .addField("Last Commented", lastActivity.toRelativeTimestampList(LastActivity::getProjectSuggestionCommentHistory), true)
            .addField("Created History", String.format(
                """
                24 Hours: %s
                7 Days: %s
                30 Days: %s""",
                lastActivity.toTotalPeriodList(LastActivity::getProjectSuggestionCreationHistory, Duration.of(24, ChronoUnit.HOURS)),
                lastActivity.toTotalPeriodList(LastActivity::getProjectSuggestionCreationHistory, Duration.of(7, ChronoUnit.DAYS)),
                lastActivity.toTotalPeriodList(LastActivity::getProjectSuggestionCreationHistory, Duration.of(30, ChronoUnit.DAYS))
            ), true)
            .addField("Voted History", String.format(
                """
                24 Hours: %s
                7 Days: %s
                30 Days: %s""",
                lastActivity.toTotalPeriodMap(LastActivity::getProjectSuggestionVoteHistoryMap, Duration.of(24, ChronoUnit.HOURS)),
                lastActivity.toTotalPeriodMap(LastActivity::getProjectSuggestionVoteHistoryMap, Duration.of(7, ChronoUnit.DAYS)),
                lastActivity.toTotalPeriodMap(LastActivity::getProjectSuggestionVoteHistoryMap, Duration.of(30, ChronoUnit.DAYS))
            ), true)
            .addField("Commented History", String.format(
                """
                24 Hours: %s
                7 Days: %s
                30 Days: %s""",
                lastActivity.toTotalPeriodList(LastActivity::getProjectSuggestionCommentHistory, Duration.of(24, ChronoUnit.HOURS)),
                lastActivity.toTotalPeriodList(LastActivity::getProjectSuggestionCommentHistory, Duration.of(7, ChronoUnit.DAYS)),
                lastActivity.toTotalPeriodList(LastActivity::getProjectSuggestionCommentHistory, Duration.of(30, ChronoUnit.DAYS))
            ), true)
            .setFooter(String.format(
                "Promotion Requirements (per %d days): %d votes, %d comments",
                promotionDays, promotionVotes, promotionComments
            ))
            .build());

        // Alpha Suggestion Activity
        embeds.add(new EmbedBuilder().setColor(Color.RED)
            .setTitle("Alpha Suggestion Activity")
            .addField("Last Created", lastActivity.toRelativeTimestampList(LastActivity::getAlphaSuggestionCreationHistory), true)
            .addField("Last Voted", lastActivity.toRelativeTimestampMap(LastActivity::getAlphaSuggestionVoteHistoryMap), true)
            .addField("Last Commented", lastActivity.toRelativeTimestampList(LastActivity::getAlphaSuggestionCommentHistory), true)
            .addField("Create History", String.format(
                """
                24 Hours: %s
                7 Days: %s
                30 Days: %s""",
                lastActivity.toTotalPeriodList(LastActivity::getAlphaSuggestionCreationHistory, Duration.of(24, ChronoUnit.HOURS)),
                lastActivity.toTotalPeriodList(LastActivity::getAlphaSuggestionCreationHistory, Duration.of(7, ChronoUnit.DAYS)),
                lastActivity.toTotalPeriodList(LastActivity::getAlphaSuggestionCreationHistory, Duration.of(30, ChronoUnit.DAYS))
            ), true)
            .addField("Vote History", String.format(
                """
                24 Hours: %s
                7 Days: %s
                30 Days: %s""",
                lastActivity.toTotalPeriodMap(LastActivity::getAlphaSuggestionVoteHistoryMap, Duration.of(24, ChronoUnit.HOURS)),
                lastActivity.toTotalPeriodMap(LastActivity::getAlphaSuggestionVoteHistoryMap, Duration.of(7, ChronoUnit.DAYS)),
                lastActivity.toTotalPeriodMap(LastActivity::getAlphaSuggestionVoteHistoryMap, Duration.of(30, ChronoUnit.DAYS))
            ), true)
            .addField("Comment History", String.format(
                """
                24 Hours: %s
                7 Days: %s
                30 Days: %s""",
                lastActivity.toTotalPeriodList(LastActivity::getAlphaSuggestionCommentHistory, Duration.of(24, ChronoUnit.HOURS)),
                lastActivity.toTotalPeriodList(LastActivity::getAlphaSuggestionCommentHistory, Duration.of(7, ChronoUnit.DAYS)),
                lastActivity.toTotalPeriodList(LastActivity::getAlphaSuggestionCommentHistory, Duration.of(30, ChronoUnit.DAYS))
            ), true)
            .setFooter(String.format(
                "Promotion Requirements (per %d days): %d votes, %d comments",
                promotionDays, promotionVotes, promotionComments
            ))
            .build());

        // Role-Restricted Channel Activity
        List<RoleRestrictedChannelGroup> channelGroups = NerdBotApp.getBot().getConfig().getChannelConfig().getRoleRestrictedChannelGroups();

        if (!channelGroups.isEmpty()) {
            Color[] restrictedChannelColors = {
                Color.CYAN,
                Color.MAGENTA,
                Color.PINK,
                Color.BLUE,
                new Color(128, 0, 128), // Purple
                new Color(255, 165, 0), // Orange
                new Color(75, 0, 130),  // Indigo
                new Color(255, 20, 147) // Deep Pink
            };

            int colorIndex = 0;

            for (RoleRestrictedChannelGroup group : channelGroups) {
                boolean hasAccess = Arrays.stream(group.getRequiredRoleIds())
                    .anyMatch(roleId -> member.getRoles().stream()
                        .map(Role::getId)
                        .anyMatch(memberRoleId -> memberRoleId.equalsIgnoreCase(roleId)));

                if (!hasAccess) {
                    continue;
                }

                int messages24h = lastActivity.getRoleRestrictedChannelMessageCount(group.getIdentifier(), 1);
                int messages7d = lastActivity.getRoleRestrictedChannelMessageCount(group.getIdentifier(), 7);
                int messages30d = lastActivity.getRoleRestrictedChannelMessageCount(group.getIdentifier(), 30);

                int votes24h = lastActivity.getRoleRestrictedChannelVoteCount(group.getIdentifier(), 1);
                int votes7d = lastActivity.getRoleRestrictedChannelVoteCount(group.getIdentifier(), 7);
                int votes30d = lastActivity.getRoleRestrictedChannelVoteCount(group.getIdentifier(), 30);

                int comments24h = lastActivity.getRoleRestrictedChannelCommentCount(group.getIdentifier(), 1);
                int comments7d = lastActivity.getRoleRestrictedChannelCommentCount(group.getIdentifier(), 7);
                int comments30d = lastActivity.getRoleRestrictedChannelCommentCount(group.getIdentifier(), 30);

                String lastActivityTime = lastActivity.getRoleRestrictedChannelRelativeTimestamp(group.getIdentifier());

                Color embedColor = restrictedChannelColors[colorIndex % restrictedChannelColors.length];
                colorIndex++;

                EmbedBuilder embedBuilder = new EmbedBuilder()
                    .setColor(embedColor)
                    .setTitle(group.getDisplayName() + " Activity")
                    .addField("Last Activity", lastActivityTime, true)
                    .addBlankField(true)
                    .addBlankField(true)
                    .addField("Message History", String.format(
                        """
                        24 Hours: %d
                        7 Days: %d
                        30 Days: %d""",
                        messages24h, messages7d, messages30d
                    ), true)
                    .addField("Vote History", String.format(
                        """
                        24 Hours: %d
                        7 Days: %d
                        30 Days: %d""",
                        votes24h, votes7d, votes30d
                    ), true)
                    .addField("Comment History", String.format(
                        """
                        24 Hours: %d
                        7 Days: %d
                        30 Days: %d""",
                        comments24h, comments7d, comments30d
                    ), true);

                embedBuilder.setFooter(String.format(
                    "Activity Requirements (per %d days): %d messages, %d votes, %d comments",
                    group.getActivityCheckDays(),
                    group.getMinimumMessagesForActivity(),
                    group.getMinimumVotesForActivity(),
                    group.getMinimumCommentsForActivity()
                ));

                embeds.add(embedBuilder.build());
            }
        }

        return embeds;
    }
}
