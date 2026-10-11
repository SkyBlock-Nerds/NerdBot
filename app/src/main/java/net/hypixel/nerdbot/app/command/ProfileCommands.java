package net.hypixel.nerdbot.app.command;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashAutocompleteHandler;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashComponentHandler;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.hypixel.nerdbot.app.channel.LogChannel;
import net.hypixel.nerdbot.app.command.util.CommandReplies;
import net.hypixel.nerdbot.app.command.util.SuggestionListings;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.profile.MojangProfileLookup;
import net.hypixel.nerdbot.app.profile.PendingVerifications;
import net.hypixel.nerdbot.app.profile.ProfileEmbeds;
import net.hypixel.nerdbot.app.profile.ProfileLinker;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.app.storage.MissingUserRecordException;
import net.hypixel.nerdbot.app.user.BirthdayService;
import net.hypixel.nerdbot.discord.util.pagination.PaginationManager;
import net.hypixel.nerdbot.marmalade.discord.EmbedFactory;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.MojangProfile;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
public class ProfileCommands {

    static final String NO_RECORD_MESSAGE = "Your profile couldn't be loaded. Please contact a moderator.";
    static final String NOT_IN_SERVER_MESSAGE = "You must be in SkyBlock Nerds to use this command!";
    static final String STILL_PENDING_MESSAGE = "Your previous verification request has not been reviewed. You will be contacted via DM if any further information is required.";
    static final String DECLINED_MESSAGE = "Your last verification request was declined. Please contact a moderator if you think this is a mistake.";
    static final String REQUEST_SENT_MESSAGE = "Your verification request has been sent. You will be contacted via DM if any further information is required.";
    static final String EXPIRED_MESSAGE = "This request expired or was lost when the bot restarted. Ask them to run /verify again.";
    static final String PAGINATION_ID = "profile-page";

    private static final String VERIFICATION_PREFIX = "verification";
    private static final int KICK_CONFIRMATIONS = 3;
    private static final int MAX_AUTOCOMPLETE_CHOICES = 25;

    private final DiscordUserStore users;
    private final Provider<NerdBotConfig> config;
    private final JDA jda;
    private final MojangProfileLookup lookup;
    private final ProfileLinker linker;
    private final PendingVerifications pending;
    private final ProfileEmbeds embeds;
    private final SuggestionListings listings;
    private final BirthdayService birthdays;
    private final LogChannel logChannel;
    private final Executor backgroundWork;

    @Inject
    public ProfileCommands(DiscordUserStore users, Provider<NerdBotConfig> config, JDA jda, MojangProfileLookup lookup, ProfileLinker linker,
                           PendingVerifications pending, ProfileEmbeds embeds, SuggestionListings listings, BirthdayService birthdays,
                           LogChannel logChannel, Executor backgroundWork) {
        this.users = users;
        this.config = config;
        this.jda = jda;
        this.lookup = lookup;
        this.linker = linker;
        this.pending = pending;
        this.embeds = embeds;
        this.listings = listings;
        this.birthdays = birthdays;
        this.logChannel = logChannel;
        this.backgroundWork = backgroundWork;
    }

    @SlashCommand(name = "verify", description = "Send a request to link your Mojang Profile to your account.", guildOnly = true)
    public void requestLinkProfile(SlashCommandInteractionEvent event, @SlashOption(description = "Your Minecraft IGN to link. Use the account you applied with.") String username) {
        requestVerification(event, username);
    }

    CompletableFuture<Void> requestVerification(IReplyCallback event, String username) {
        InteractionHook hook = CommandReplies.deferPrivately(event);
        Member member = event.getMember();

        switch (pending.state(member.getId())) {
            case PENDING -> {
                hook.editOriginal(STILL_PENDING_MESSAGE).queue();
                return CompletableFuture.completedFuture(null);
            }
            case DECLINED -> {
                hook.editOriginal(DECLINED_MESSAGE).queue();
                return CompletableFuture.completedFuture(null);
            }
            case NONE -> {
            }
        }

        return answer(hook, "send a verification request for " + member.getId(), "Failed to process the verification request. Please try again later.",
            CompletableFuture.supplyAsync(() -> users.require(member.getId()), backgroundWork)
                .thenCompose(user -> lookup.lookup(username, member.getUser().getName(), true))
                .thenApply(result -> switch (result) {
                    case MojangProfileLookup.Rejected rejected -> rejected.reason();
                    case MojangProfileLookup.Found found -> {
                        pending.submit(member.getId(), found.profile());
                        postForReview(member, found.profile());
                        yield REQUEST_SENT_MESSAGE;
                    }
                }));
    }

    private void postForReview(Member member, MojangProfile profile) {
        TextChannel channel = verifyLogChannel();
        if (channel == null) {
            log.warn("Profile verification log channel not found!");
            return;
        }

        String moderatorMention = "<@&" + config.get().getRoleConfig().getModeratorRoleId() + ">";
        channel.sendMessage(moderatorMention)
            .addEmbeds(new EmbedBuilder()
                .setTitle("Mojang Profile Verification")
                .setDescription(member.getAsMention() + " has sent a Mojang verification request. This discord account matches the social set for this Mojang Profile.")
                .setColor(Color.PINK)
                .setThumbnail(member.getEffectiveAvatarUrl())
                .setFooter("This request expires in 1 day.")
                .addField("Username", profile.getUsername(), false)
                .addField("UUID / SkyCrypt", ProfileEmbeds.skyCryptLink(profile), false)
                .build())
            .addActionRow(
                Button.success(VERIFICATION_PREFIX + "-accept-" + member.getId(), "Accept"),
                Button.danger(VERIFICATION_PREFIX + "-deny-" + member.getId(), "Deny"))
            .queue();
    }

    private @Nullable TextChannel verifyLogChannel() {
        String channelId = config.get().getChannelConfig().getVerifyLogChannelId();
        return channelId == null || channelId.isBlank() ? null : jda.getTextChannelById(channelId);
    }

    @SlashCommand(name = "profile", subcommand = "link", description = "Change your linked Mojang Profile.")
    public void linkProfile(SlashCommandInteractionEvent event, @SlashOption(description = "Your Minecraft IGN to link.") String username) {
        changeLinkedProfile(event, username);
    }

    CompletableFuture<Void> changeLinkedProfile(IReplyCallback event, String username) {
        InteractionHook hook = CommandReplies.deferPrivately(event);
        String userId = event.getUser().getId();
        Guild guild = jda.getGuildById(config.get().getGuildId());

        if (guild == null) {
            CommandReplies.fail(hook, "Failed to link the profile. Please try again later.",
                new IllegalStateException("The configured guild " + config.get().getGuildId() + " isn't available"));
            return CompletableFuture.completedFuture(null);
        }

        return answer(hook, "link a profile for " + userId, "Failed to link the profile. Please try again later.",
            guild.retrieveMemberById(userId).submit().thenComposeAsync(member -> {
                DiscordUser user = users.require(userId);
                if (user.noProfileAssigned()) {
                    return CompletableFuture.completedFuture("You have not yet verified your account. Please do so before attempting to change it.");
                }

                return lookup.lookup(username, member.getUser().getName(), true).thenCompose(result -> switch (result) {
                    case MojangProfileLookup.Rejected rejected -> CompletableFuture.completedFuture(rejected.reason());
                    case MojangProfileLookup.Found found -> linker.link(member, found.profile()).thenApply(linked -> {
                        logChannel.send(EmbedFactory.success("Mojang Profile Link", member.getAsMention() + " has linked their Mojang Profile.")
                            .setThumbnail(member.getEffectiveAvatarUrl())
                            .addField("Username", found.profile().getUsername(), false)
                            .addField("UUID / SkyCrypt", ProfileEmbeds.skyCryptLink(found.profile()), false)
                            .build());
                        return String.format("Updated your Mojang profile to %s (`%s`)", found.profile().getUsername(), found.profile().getUniqueId());
                    });
                });
            }, backgroundWork));
    }

    @SlashCommand(name = "profile", subcommand = "activity", description = "View your activity.", guildOnly = true)
    public void myActivity(SlashCommandInteractionEvent event) {
        showActivity(event);
    }

    void showActivity(IReplyCallback event) {
        InteractionHook hook = CommandReplies.deferPrivately(event);
        withRecord(hook, event.getMember().getId(), user -> CommandReplies.showEmbeds(hook, embeds.activity(event.getMember(), user)));
    }

    @SlashCommand(name = "profile", subcommand = "view", description = "View your profile.", guildOnly = true)
    public void myInfo(SlashCommandInteractionEvent event) {
        showProfile(event);
    }

    void showProfile(IReplyCallback event) {
        InteractionHook hook = CommandReplies.deferPrivately(event);
        Member member = event.getMember();

        withRecord(hook, member.getId(), user -> hook.editOriginalEmbeds(new EmbedBuilder()
            .setAuthor(member.getEffectiveName() + " (ID: " + member.getId() + ")")
            .setTitle("Your Profile")
            .setThumbnail(member.getEffectiveAvatarUrl())
            .setColor(member.getColor())
            .addField("Mojang Profile", ProfileEmbeds.linkedProfile(user), false)
            .addField("Badges", ProfileEmbeds.badgeCount(user), true)
            .addField("Birthday", ProfileEmbeds.birthday(user), true)
            .build()).queue());
    }

    @SlashCommand(name = "profile", subcommand = "badges", description = "View your badges.", guildOnly = true)
    public void myBadges(SlashCommandInteractionEvent event, @SlashOption(required = false) Boolean showPublicly) {
        showBadges(event, showPublicly);
    }

    void showBadges(IReplyCallback event, @Nullable Boolean showPublicly) {
        event.deferReply(!Boolean.TRUE.equals(showPublicly)).queue();
        InteractionHook hook = event.getHook();
        withRecord(hook, event.getMember().getId(), user -> hook.editOriginalEmbeds(embeds.badges(event.getMember(), user, true)).queue());
    }

    @SlashCommand(name = "profile", subcommand = "suggestions", description = "View your suggestions.", guildOnly = true)
    public void mySuggestions(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Tags to filter for (comma separated).", required = false) String tags,
        @SlashOption(description = "Words to filter title for.", required = false) String title,
        @SlashOption(description = "Show suggestions from a specific category.", autocompleteId = "suggestion-types", required = false) String type
    ) {
        Member member = event.getMember();
        listings.show(event, new SuggestionListings.Request(member.getIdLong(), member.getEffectiveName(), member.getEffectiveAvatarUrl(), false, true),
            tags, title, type, PAGINATION_ID);
    }

    @SlashCommand(name = "profile", group = "birthday", subcommand = "remove", description = "Remove your birthday.", guildOnly = true)
    public void removeBirthday(SlashCommandInteractionEvent event) {
        String discordId = event.getUser().getId();
        changeBirthday(event, service -> service.removeBirthday(discordId), "Failed to remove the birthday. Please try again later.");
    }

    @SlashCommand(name = "profile", group = "birthday", subcommand = "timezone", description = "Update your birthday notification timezone.", guildOnly = true)
    public void setBirthdayTimezone(
        SlashCommandInteractionEvent event,
        @SlashOption(autocompleteId = "timezones", description = "Your timezone (e.g., America/New_York, Europe/London, Asia/Tokyo, UTC).") String timezone
    ) {
        String discordId = event.getUser().getId();
        changeBirthday(event, service -> service.setTimezone(discordId, timezone), "Failed to update the timezone. Please try again later.");
    }

    @SlashCommand(name = "profile", group = "birthday", subcommand = "set", description = "Set your birthday.", guildOnly = true)
    public void setBirthday(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Your birthday in the format MM/DD/YYYY.") String birthday,
        @SlashOption(description = "Whether to announce your age.", required = false) Boolean announceAge,
        @SlashOption(autocompleteId = "timezones", description = "Your timezone (e.g., America/New_York, Europe/London, Asia/Tokyo). Defaults to UTC.", required = false) String timezone
    ) {
        String discordId = event.getUser().getId();
        changeBirthday(event, service -> service.setBirthday(discordId, birthday, Boolean.TRUE.equals(announceAge), timezone),
            "Failed to set the birthday. Please try again later.");
    }

    CompletableFuture<Void> changeBirthday(IReplyCallback event, Function<BirthdayService, BirthdayService.Outcome> change, String failureMessage) {
        InteractionHook hook = CommandReplies.deferPrivately(event);
        return CommandReplies.finish(CompletableFuture.supplyAsync(() -> change.apply(birthdays), backgroundWork),
            hook, "change the birthday of " + event.getUser().getId(), failureMessage, outcome -> hook.editOriginal(outcome.message()).queue());
    }

    @SlashCommand(name = "profile", group = "preference", subcommand = "hide", description = "Change your default hide behavior for generator commands.")
    public void setHidePreference(SlashCommandInteractionEvent event, @SlashOption(description = "The value to set as your default hide behavior") boolean autohide) {
        changeHidePreference(event, autohide);
    }

    CompletableFuture<Void> changeHidePreference(IReplyCallback event, boolean autohide) {
        InteractionHook hook = CommandReplies.deferPrivately(event);
        String userId = event.getUser().getId();

        return answer(hook, "save the hide preference of " + userId, "An error occurred while saving your preference, please try again later.",
            CompletableFuture.supplyAsync(() -> users.require(userId), backgroundWork).thenCompose(user -> {
                user.setAutoHideGenCommands(autohide);
                return users.save(user);
            }).thenApply(saved -> "Your default image generator message behavior has been set to " + (autohide ? "hidden" : "public")));
    }

    @SlashComponentHandler(id = "verification", patterns = {"verification-*"})
    public void handleVerificationButtons(ButtonInteractionEvent event) {
        handleVerification(event);
    }

    /** Button ids are verification-accept-ID, -deny-ID, -kick-ID-CLICKSLEFT and -kicknow-ID; older messages still use them. */
    CompletableFuture<Void> handleVerification(ButtonInteraction event) {
        String[] parts = event.getComponentId().split("-");
        if (parts.length < 3) {
            event.deferEdit().queue();
            return CompletableFuture.completedFuture(null);
        }

        String memberId = parts[2];
        return switch (parts[1]) {
            case "accept" -> accept(event, memberId);
            case "deny" -> {
                pending.decline(memberId);
                event.editComponents(ActionRow.of(
                    Button.danger(VERIFICATION_PREFIX + "-kick-" + memberId + "-" + KICK_CONFIRMATIONS, "Kick (" + KICK_CONFIRMATIONS + ")"),
                    Button.danger(VERIFICATION_PREFIX + "-done", "Denied").asDisabled())).queue();
                yield CompletableFuture.completedFuture(null);
            }
            case "kick" -> {
                confirmKick(event, memberId, parts.length > 3 ? parts[3] : "1");
                yield CompletableFuture.completedFuture(null);
            }
            case "kicknow" -> kick(event, memberId);
            default -> {
                log.warn("Unknown verification button {}", event.getComponentId());
                event.deferEdit().queue();
                yield CompletableFuture.completedFuture(null);
            }
        };
    }

    private CompletableFuture<Void> accept(ButtonInteraction event, String memberId) {
        MojangProfile profile = pending.accept(memberId).orElse(null);
        if (profile == null) {
            event.editComponents(ActionRow.of(Button.danger(VERIFICATION_PREFIX + "-done", "Expired").asDisabled())).queue();
            event.getHook().sendMessage(EXPIRED_MESSAGE).setEphemeral(true).queue();
            return CompletableFuture.completedFuture(null);
        }

        event.deferEdit().queue();
        InteractionHook hook = event.getHook();

        return event.getGuild().retrieveMemberById(memberId).submit()
            .thenCompose(member -> linker.link(member, profile))
            .thenRun(() -> hook.editOriginalComponents(ActionRow.of(Button.success(VERIFICATION_PREFIX + "-done", "Accepted").asDisabled())).queue())
            .exceptionally(error -> {
                Throwable cause = unwrap(error);
                if (isUnknownMember(cause)) {
                    hook.editOriginalComponents(ActionRow.of(Button.secondary(VERIFICATION_PREFIX + "-done", "Left Server").asDisabled())).queue();
                } else {
                    // Put it back so the moderator can try again
                    pending.submit(memberId, profile);
                    CommandReplies.logFailure("Failed to accept the verification of " + memberId, cause);
                    hook.sendMessage(cause instanceof MissingUserRecordException
                        ? "That member has no user record, so the profile couldn't be linked."
                        : "Couldn't link the profile. Please try again.").setEphemeral(true).queue();
                }
                return null;
            });
    }

    private static void confirmKick(ButtonInteraction event, String memberId, String clicksLeftText) {
        int clicksLeft;
        try {
            clicksLeft = Integer.parseInt(clicksLeftText) - 1;
        } catch (NumberFormatException exception) {
            clicksLeft = 0;
        }

        if (clicksLeft <= 0) {
            event.reply("Are you sure you want to kick <@" + memberId + ">?")
                .setEphemeral(true)
                .addComponents(ActionRow.of(Button.danger(VERIFICATION_PREFIX + "-kicknow-" + memberId, "Kick Now")))
                .queue();
            return;
        }

        event.editComponents(ActionRow.of(
            Button.danger(VERIFICATION_PREFIX + "-kick-" + memberId + "-" + clicksLeft, "Kick (" + clicksLeft + ")"),
            Button.secondary(VERIFICATION_PREFIX + "-done", "Denied").asDisabled())).queue();
    }

    private CompletableFuture<Void> kick(ButtonInteraction event, String memberId) {
        event.deferReply().queue();
        InteractionHook hook = event.getHook();
        Guild guild = event.getGuild();

        return guild.retrieveMemberById(memberId).submit()
            .thenCompose(member -> {
                String name = displayName(member);
                return guild.kick(member).submit().thenApply(kicked -> name);
            })
            .thenAccept(name -> hook.editOriginalEmbeds(new EmbedBuilder()
                .setTitle("Member Kicked")
                .setDescription(name + " has been kicked from the server after failing verification.")
                .setColor(Color.RED)
                .build()).queue())
            .exceptionally(error -> {
                Throwable cause = unwrap(error);
                if (isUnknownMember(cause)) {
                    hook.editOriginal("<@" + memberId + "> has already left the server.").queue();
                } else {
                    CommandReplies.fail(hook, "Couldn't kick that member. Please check my permissions and try again.", cause);
                }
                return null;
            });
    }

    /** The member's Minecraft name when they've linked one, otherwise their server nickname. */
    private String displayName(Member member) {
        DiscordUser user = users.findById(member.getId()).orElse(null);
        if (user != null && user.isProfileAssigned()) {
            return user.getMojangProfile().getUsername();
        }
        return member.getNickname() == null ? member.getUser().getEffectiveName() : member.getNickname();
    }

    @SlashComponentHandler(id = "profile-pagination", patterns = {PAGINATION_ID + ":*"})
    public void handleProfilePagination(ButtonInteractionEvent event) {
        event.deferEdit().queue();

        if (!PaginationManager.handleButtonInteraction(event)) {
            log.warn("Could not find pagination for message ID: {}", event.getMessageId());
            event.getHook().editOriginal("This pagination has expired. Please run the command again.").queue();
        }
    }

    @SlashAutocompleteHandler(id = "timezones")
    public List<Command.Choice> timezoneAutocomplete(CommandAutoCompleteInteractionEvent event) {
        return timezoneChoices(event.getFocusedOption().getValue());
    }

    static List<Command.Choice> timezoneChoices(String typed) {
        String input = typed.toLowerCase(Locale.ROOT);

        return ZoneId.getAvailableZoneIds().stream()
            .filter(zone -> zone.toLowerCase(Locale.ROOT).contains(input))
            .sorted()
            .limit(MAX_AUTOCOMPLETE_CHOICES)
            .map(zone -> new Command.Choice(zone, zone))
            .toList();
    }

    /** Runs {@code show} with the member's record, or tells them it couldn't be loaded. */
    private void withRecord(InteractionHook hook, String memberId, Consumer<DiscordUser> show) {
        DiscordUser user;
        try {
            user = users.require(memberId);
        } catch (MissingUserRecordException exception) {
            CommandReplies.fail(hook, NO_RECORD_MESSAGE, exception);
            return;
        }
        show.accept(user);
    }

    /** Replaces the deferred reply with the message the work produces, or with a safe message when it fails. */
    private static CompletableFuture<Void> answer(InteractionHook hook, String action, String failureMessage, CompletableFuture<String> work) {
        return work.handle((message, error) -> {
            if (error == null) {
                hook.editOriginal(message).queue();
                return null;
            }

            Throwable cause = unwrap(error);
            if (cause instanceof MissingUserRecordException) {
                CommandReplies.fail(hook, NO_RECORD_MESSAGE, cause);
            } else if (isUnknownMember(cause)) {
                hook.editOriginal(NOT_IN_SERVER_MESSAGE).queue();
            } else {
                CommandReplies.logFailure("Failed to " + action, cause);
                hook.editOriginal(failureMessage).queue();
            }
            return null;
        });
    }

    private static boolean isUnknownMember(Throwable error) {
        return error instanceof ErrorResponseException response && response.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER;
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}
