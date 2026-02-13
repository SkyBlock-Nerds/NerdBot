package net.hypixel.nerdbot.app.ticket;

import lombok.experimental.UtilityClass;
import net.hypixel.nerdbot.discord.config.channel.TicketConfig;

/**
 * Utility class for validating ticket-related input.
 * All validation methods throw {@link IllegalArgumentException} on invalid input.
 */
@UtilityClass
public class TicketValidation {

    private static final int MIN_DESCRIPTION_LENGTH = 10;
    private static final int MAX_DESCRIPTION_LENGTH = 4_000;

    /**
     * Validates that the category ID exists in the configuration.
     *
     * @param categoryId the category ID to validate
     * @param config     the ticket configuration
     *
     * @throws IllegalArgumentException if the category is null, blank, or doesn't exist
     */
    public static void validateCategoryId(String categoryId, TicketConfig config) {
        if (categoryId == null || categoryId.isBlank()) {
            throw new IllegalArgumentException("Category ID is required");
        }

        if (config.getCategoryById(categoryId).isEmpty()) {
            throw new IllegalArgumentException("Invalid category: " + categoryId);
        }
    }

    /**
     * Validates that a user is not blacklisted from creating tickets.
     *
     * @param userId the user ID to check
     * @param config the ticket configuration
     *
     * @throws IllegalArgumentException if the user is blacklisted
     */
    public static void validateUserNotBlacklisted(String userId, TicketConfig config) {
        if (config.isUserBlacklisted(userId)) {
            throw new IllegalArgumentException(config.getBlacklistMessage());
        }
    }

    /**
     * Validates a ticket description with default length constraints.
     *
     * @param description the description to validate
     *
     * @throws IllegalArgumentException if the description is invalid
     */
    public static void validateDescription(String description) {
        validateDescription(description, MIN_DESCRIPTION_LENGTH, MAX_DESCRIPTION_LENGTH);
    }

    /**
     * Validates a ticket description with custom length constraints.
     *
     * @param description the description to validate
     * @param minLength   minimum required length
     * @param maxLength   maximum allowed length
     *
     * @throws IllegalArgumentException if the description is invalid
     */
    public static void validateDescription(String description, int minLength, int maxLength) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description is required");
        }

        String trimmed = description.trim();
        if (trimmed.length() < minLength) {
            throw new IllegalArgumentException("Description must be at least " + minLength + " characters");
        }

        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException("Description cannot exceed " + maxLength + " characters");
        }
    }
}