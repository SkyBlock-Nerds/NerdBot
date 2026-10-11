package net.hypixel.nerdbot.app.command;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.hypixel.nerdbot.app.channel.LogChannel;
import net.hypixel.nerdbot.app.command.util.CommandReplies;
import net.hypixel.nerdbot.app.profile.MinecraftAccounts;
import net.hypixel.nerdbot.app.profile.MojangProfileLookup;
import net.hypixel.nerdbot.app.profile.ProfileEmbeds;
import net.hypixel.nerdbot.app.profile.ProfileLinker;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.app.storage.MissingUserRecordException;
import net.hypixel.nerdbot.app.user.LegacyUsernames;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.MojangProfile;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** /user: moderator tools for looking at and fixing members' records. */
@Slf4j
public class UserCommands {

    private static final int MAX_DESCRIPTION_LENGTH = MessageEmbed.DESCRIPTION_MAX_LENGTH;

    private final DiscordUserStore users;
    private final MojangProfileLookup lookup;
    private final ProfileLinker linker;
    private final ProfileEmbeds embeds;
    private final MinecraftAccounts accounts;
    private final LogChannel logChannel;
    private final Executor backgroundWork;

    @Inject
    public UserCommands(DiscordUserStore users, MojangProfileLookup lookup, ProfileLinker linker, ProfileEmbeds embeds,
                        MinecraftAccounts accounts, LogChannel logChannel, Executor backgroundWork) {
        this.users = users;
        this.lookup = lookup;
        this.linker = linker;
        this.embeds = embeds;
        this.accounts = accounts;
        this.logChannel = logChannel;
        this.backgroundWork = backgroundWork;
    }

    @SlashCommand(name = "user", subcommand = "link", description = "Link a Mojang Profile to a member's account.", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"})
    public void linkProfile(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Member to link to.") Member member,
        @SlashOption(description = "Your Minecraft IGN to link.") String username,
        @SlashOption(description = "Bypass hypixel social check.", required = false) Boolean bypassSocial
    ) {
        link(event, member, username, Boolean.TRUE.equals(bypassSocial));
    }

    CompletableFuture<Void> link(IReplyCallback event, Member member, String username, boolean bypassSocial) {
        InteractionHook hook = CommandReplies.deferPrivately(event);

        return CompletableFuture.supplyAsync(() -> users.require(member.getId()), backgroundWork)
            .thenCompose(user -> lookup.lookup(username, member.getUser().getName(), !bypassSocial))
            .thenCompose(result -> switch (result) {
                case MojangProfileLookup.Rejected rejected -> CompletableFuture.completedFuture(rejected.reason().contains("does not match")
                    ? String.format("Profile mismatch error for %s: %s", username, rejected.reason())
                    : rejected.reason());
                case MojangProfileLookup.Found found -> linker.link(member, found.profile()).thenApply(linked -> {
                    MojangProfile profile = found.profile();
                    logChannel.send(new EmbedBuilder()
                        .setTitle("Mojang Profile Change")
                        .setThumbnail(member.getAvatarUrl())
                        .setDescription(event.getUser().getAsMention() + " updated the Mojang Profile for " + member.getAsMention() + ".")
                        .addField("Username", profile.getUsername(), false)
                        .addField("UUID / SkyCrypt", ProfileEmbeds.skyCryptLink(profile), false)
                        .build());
                    return String.format("Updated profile for %s to %s (UUID: %s)", member.getAsMention(), profile.getUsername(), profile.getUniqueId());
                });
            })
            .handle((message, error) -> {
                if (error == null) {
                    hook.editOriginal(message).queue();
                } else {
                    failForMember(hook, member, "Failed to link that profile. Please try again later.", error);
                }
                return null;
            });
    }

    @SlashCommand(name = "user", subcommand = "missing", description = "List any user with no assigned Mojang Profile.", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"})
    public void userMissingProfile(SlashCommandInteractionEvent event) {
        listMissing(event);
    }

    void listMissing(IReplyCallback event) {
        InteractionHook hook = CommandReplies.deferPrivately(event);

        event.getGuild().loadMembers()
            .onSuccess(members -> CommandReplies.finish(CompletableFuture.supplyAsync(() -> unlinkedRecordIds(), backgroundWork), hook,
                "list members without a linked profile", "An error occurred while checking for missing profiles. Please try again later.",
                unlinked -> {
                    List<String> mentions = members.stream()
                        .filter(member -> !member.getUser().isBot() && unlinked.contains(member.getId()))
                        .map(Member::getAsMention)
                        .toList();

                    if (mentions.isEmpty()) {
                        hook.editOriginal("No users with missing Mojang Profiles found.").queue();
                        return;
                    }

                    hook.editOriginalEmbeds(new EmbedBuilder()
                        .setColor(Color.MAGENTA)
                        .setTitle("Missing Mojang Profiles (" + mentions.size() + ")")
                        .setDescription(joinWithinLimit(mentions, MAX_DESCRIPTION_LENGTH))
                        .build()).queue();
                }))
            .onError(error -> CommandReplies.fail(hook, "Failed to load guild members", error));
    }

    private Set<String> unlinkedRecordIds() {
        return users.getAllFromDatabase().stream()
            .filter(DiscordUser::noProfileAssigned)
            .map(DiscordUser::getDiscordId)
            .collect(Collectors.toSet());
    }

    /** Joins with ", ", ending with "and N more" when everything won't fit in {@code limit} characters. */
    static String joinWithinLimit(List<String> items, int limit) {
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            String separator = i == 0 ? "" : ", ";
            String more = " and " + (items.size() - i) + " more";
            if (joined.length() + separator.length() + items.get(i).length() + (i == items.size() - 1 ? 0 : more.length()) > limit) {
                return joined + more;
            }
            joined.append(separator).append(items.get(i));
        }
        return joined.toString();
    }

    @SlashCommand(name = "user", subcommand = "info", description = "View information about a user", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"})
    public void userInfo(SlashCommandInteractionEvent event, @SlashOption(description = "The user to search") Member member) {
        showInfo(event, member);
    }

    void showInfo(IReplyCallback event, Member member) {
        InteractionHook hook = CommandReplies.deferPrivately(event);

        withRecord(hook, member, user -> {
            List<MessageEmbed> shown = new ArrayList<>();
            shown.add(new EmbedBuilder()
                .setAuthor(member.getEffectiveName())
                .setThumbnail(member.getEffectiveAvatarUrl())
                .addField("ID", member.getId(), false)
                .addField("Mojang Profile", ProfileEmbeds.linkedProfile(user), false)
                .addField("Birthday", ProfileEmbeds.birthday(user), false)
                .build());
            shown.add(embeds.badges(member, user, false));
            shown.addAll(embeds.activity(member, user));
            CommandReplies.showEmbeds(hook, shown);
        });
    }

    @SlashCommand(name = "user", subcommand = "badges", description = "View the badges of a user", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"})
    public void viewUserBadges(SlashCommandInteractionEvent event, @SlashOption(description = "The user to search") Member member) {
        showBadges(event, member);
    }

    void showBadges(IReplyCallback event, Member member) {
        InteractionHook hook = CommandReplies.deferPrivately(event);
        withRecord(hook, member, user -> hook.editOriginalEmbeds(embeds.badges(member, user, event.getUser().getId().equals(member.getId()))).queue());
    }

    @SlashCommand(name = "user", group = "migrate", subcommand = "names", description = "Attempts to migrate any user with no assigned Mojang Profile using their display name.", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"})
    public void migrateUsernames(SlashCommandInteractionEvent event) {
        migrateNames(event);
    }

    // Intentionally uses the legacy display-name heuristic: this admin tool migrates pre-verification
    // members who never self-assigned a Mojang profile, and no non-heuristic source exists for them.
    @SuppressWarnings("deprecation")
    void migrateNames(IReplyCallback event) {
        InteractionHook hook = CommandReplies.deferPrivately(event);

        event.getGuild().loadMembers()
            .onSuccess(members -> {
                AtomicInteger migrated = new AtomicInteger();
                AtomicInteger failed = new AtomicInteger();

                List<CompletableFuture<Void>> migrations = members.stream()
                    .filter(member -> !member.getUser().isBot())
                    .map(member -> LegacyUsernames.fromDisplayName(member).map(name -> migrate(member, name, migrated, failed)))
                    .flatMap(Optional::stream)
                    .toList();

                CommandReplies.finish(CompletableFuture.allOf(migrations.toArray(CompletableFuture[]::new)), hook,
                    "migrate member names", "Migration failed. Please try again later.",
                    done -> hook.editOriginal("Migration complete! Migrated " + migrated.get() + " users."
                        + (failed.get() > 0 ? " " + failed.get() + " couldn't be migrated; see the logs." : "")).queue());
            })
            .onError(error -> CommandReplies.fail(hook, "Failed to load guild members", error));
    }

    private CompletableFuture<Void> migrate(Member member, String name, AtomicInteger migrated, AtomicInteger failed) {
        return CompletableFuture.supplyAsync(() -> users.findById(member.getId()), backgroundWork)
            .thenCompose(record -> {
                if (record.isEmpty() || record.get().isProfileAssigned()) {
                    return CompletableFuture.completedFuture(null);
                }

                return accounts.mojangProfile(name).thenCompose(result -> {
                    MojangProfile profile = result.orElse(null);
                    if (profile == null || profile.getErrorMessage() != null || profile.getUniqueId() == null) {
                        failed.incrementAndGet();
                        log.warn("Unable to migrate {} (ID: {}) from the name {}", member.getEffectiveName(), member.getId(), name);
                        return CompletableFuture.completedFuture(null);
                    }

                    DiscordUser user = record.get();
                    user.setMojangProfile(profile);
                    return users.save(user).thenRun(() -> {
                        migrated.incrementAndGet();
                        log.info("Migrated {} [{}] ({}) to {} ({})", member.getEffectiveName(), member.getUser().getName(), member.getId(), profile.getUsername(), profile.getUniqueId());
                    });
                });
            })
            .exceptionally(error -> {
                failed.incrementAndGet();
                log.error("Failed to migrate {} (ID: {})", member.getEffectiveName(), member.getId(), error);
                return null;
            });
    }

    private void withRecord(InteractionHook hook, Member member, Consumer<DiscordUser> show) {
        DiscordUser user;
        try {
            user = users.require(member.getId());
        } catch (MissingUserRecordException exception) {
            CommandReplies.fail(hook, member.getAsMention() + " has no user record.", exception);
            return;
        }
        show.accept(user);
    }

    private static void failForMember(InteractionHook hook, Member member, String failureMessage, Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        CommandReplies.fail(hook, cause instanceof MissingUserRecordException ? member.getAsMention() + " has no user record." : failureMessage, cause);
    }
}
