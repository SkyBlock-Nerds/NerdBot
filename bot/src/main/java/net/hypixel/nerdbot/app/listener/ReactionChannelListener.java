package net.hypixel.nerdbot.app.listener;

import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.discord.cache.EmojiCache;
import net.hypixel.nerdbot.discord.config.objects.ReactionChannel;
import net.hypixel.nerdbot.discord.config.objects.ReactionRule;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class ReactionChannelListener {

    @SubscribeEvent
    public void onMessageReceive(MessageReceivedEvent event) {
        List<ReactionChannel> reactionChannels = DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getReactionChannels();

        if (reactionChannels == null) {
            return;
        }

        Optional<ReactionChannel> reactionChannel = reactionChannels.stream()
            .filter(channel -> channel.discordChannelId().equals(event.getChannel().getId()))
            .findFirst();

        if (reactionChannel.isPresent()) {
            ReactionChannel channelConfig = reactionChannel.get();
            Message message = event.getMessage();

            if (!channelConfig.reactionRules().isEmpty()) {
                List<ReactionRule> matchingRules = channelConfig.reactionRules().stream()
                    .filter(rule -> shouldApplyRule(message, rule))
                    .toList();

                if (!matchingRules.isEmpty()) {
                    applyReactionRules(message, channelConfig, matchingRules);
                }
                return;
            }

            applyDefaultReactions(message, channelConfig);
            return;
        }

        String pollChannelId = DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getPollChannelId();

        if (event.getChannel().getId().equalsIgnoreCase(pollChannelId)) {
            EmojiParser.extractEmojis(event.getMessage().getContentRaw()).stream()
                .map(Emoji::fromUnicode)
                .forEach(emoji -> {
                    event.getMessage().addReaction(emoji).queue();
                    log.info("[Polls] [" + pollChannelId + "] Added reaction '" + emoji.getName() + "' to message " + event.getMessage().getId());
                });
        }
    }

    private void applyReactionRules(Message message, ReactionChannel reactionChannel, List<ReactionRule> rules) {
        Set<String> addedReactions = new HashSet<>();
        boolean createThread = false;

        for (ReactionRule rule : rules) {
            createThread = createThread || rule.thread();
            addReactions(message, reactionChannel.name(), rule.name(), rule.emojiIds(), addedReactions);
        }

        if (createThread) {
            createThread(message, reactionChannel.name(), "rule-based reaction channel");
        }
    }

    private void applyDefaultReactions(Message message, ReactionChannel reactionChannel) {
        if (reactionChannel.emojiIds().isEmpty()) {
            return;
        }

        addReactions(message, reactionChannel.name(), "channel-default", reactionChannel.emojiIds(), new HashSet<>());

        if (reactionChannel.thread()) {
            createThread(message, reactionChannel.name(), "reaction channel");
        }
    }

    private void addReactions(Message message, String channelName, String ruleName, List<String> emojiIds, Set<String> addedReactions) {
        emojiIds.stream()
            .map(this::resolveEmoji)
            .flatMap(Optional::stream)
            .forEach(emoji -> {
                String reactionKey = getReactionKey(emoji);
                if (addedReactions.add(reactionKey)) {
                    message.addReaction(emoji).queue();
                    log.info("[Reaction Channel] Added reaction '" + emoji.getName() + "' (rule: " + ruleName + ") to message " + message.getId() + " in reaction channel " + channelName);
                }
            });
    }

    private void createThread(Message message, String channelName, String context) {
        String threadName = buildThreadName(message);

        message.createThreadChannel(threadName).queue(thread -> {
            log.info("[Reaction Channel] Created thread '" + threadName + "' for message " + message.getId() + " in " + context + " " + channelName);
        });
    }

    private boolean shouldApplyRule(Message message, ReactionRule rule) {
        boolean contentMatches = rule.contentContains().isEmpty() || containsAnyIgnoreCase(message.getContentRaw(), rule.contentContains());
        boolean titleMatches = rule.embedTitleContains().isEmpty() || message.getEmbeds()
            .stream()
            .map(MessageEmbed::getTitle)
            .filter(Objects::nonNull)
            .anyMatch(title -> containsAnyIgnoreCase(title, rule.embedTitleContains()));
        boolean descriptionMatches = rule.embedDescriptionContains().isEmpty() || message.getEmbeds()
            .stream()
            .map(MessageEmbed::getDescription)
            .filter(Objects::nonNull)
            .anyMatch(description -> containsAnyIgnoreCase(description, rule.embedDescriptionContains()));

        return contentMatches && titleMatches && descriptionMatches;
    }

    private boolean containsAnyIgnoreCase(String text, List<String> keywords) {
        return keywords.stream().anyMatch(keyword -> text.toLowerCase().contains(keyword.toLowerCase()));
    }

    private String getReactionKey(Emoji emoji) {
        return emoji.getType().name() + ":" + emoji.getName();
    }

    private String buildThreadName(Message message) {
        String embedTitle = message.getEmbeds().stream()
            .map(MessageEmbed::getTitle)
            .filter(title -> title != null && !title.isBlank())
            .findFirst()
            .orElse(null);

        String targetName = findTargetName(message);
        String embedAuthor = message.getEmbeds().stream()
            .map(MessageEmbed::getAuthor)
            .filter(Objects::nonNull)
            .map(MessageEmbed.AuthorInfo::getName)
            .filter(author -> author != null && !author.isBlank())
            .findFirst()
            .orElse(null);

        String messageContent = message.getContentDisplay();
        String authorName = message.getAuthor() != null ? message.getAuthor().getName() : "Unknown user";

        String baseName;
        if (targetName != null && embedTitle != null) {
            baseName = embedTitle + " — " + targetName;
        } else if (targetName != null) {
            baseName = "Thread for " + targetName;
        } else if (embedTitle != null) {
            baseName = embedTitle;
        } else if (embedAuthor != null) {
            baseName = "Thread for " + embedAuthor;
        } else if (!messageContent.isBlank()) {
            baseName = messageContent;
        } else {
            baseName = "Thread for " + authorName;
        }

        String sanitized = baseName.replaceAll("[\\r\\n]+", " ").trim();
        if (sanitized.length() > 90) {
            sanitized = sanitized.substring(0, 87) + "...";
        }

        return sanitized.isEmpty() ? "Related messages here" : sanitized;
    }

    private String findTargetName(Message message) {
        String mentionId = message.getEmbeds().stream()
            .map(MessageEmbed::getDescription)
            .filter(Objects::nonNull)
            .map(this::extractFirstMentionId)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);

        if (mentionId != null) {
            return Optional.of(message.getGuild())
                .map(guild -> guild.getMemberById(mentionId))
                .map(Member::getEffectiveName)
                .orElse("User " + mentionId);
        }

        return message.getEmbeds().stream()
            .map(MessageEmbed::getDescription)
            .filter(Objects::nonNull)
            .map(this::extractFirstBoldedPhrase)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    private String extractFirstMentionId(String text) {
        String mentionRegex = "<@!?(\\d+)>";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(mentionRegex).matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String extractFirstBoldedPhrase(String text) {
        String boldRegex = "\\*\\*(.+?)\\*\\*";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(boldRegex).matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private Optional<Emoji> resolveEmoji(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }

        String trimmed = identifier.trim();

        if (trimmed.matches("\\d+")) {
            Optional<Emoji> cached = EmojiCache.getEmojiById(trimmed);
            if (cached.isPresent()) {
                return cached;
            }
        }

        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            String possibleId = trimmed.replaceAll("[^0-9]", "");
            if (!possibleId.isBlank()) {
                Optional<Emoji> cached = EmojiCache.getEmojiById(possibleId);
                if (cached.isPresent()) {
                    return cached;
                }
            }
        }

        if (EmojiParser.removeAllEmojis(trimmed).isEmpty()) {
            return Optional.of(Emoji.fromUnicode(trimmed));
        }

        return Optional.empty();
    }
}
