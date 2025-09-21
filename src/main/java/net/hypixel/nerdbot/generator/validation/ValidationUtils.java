package net.hypixel.nerdbot.generator.validation;

import net.hypixel.nerdbot.generator.exception.GeneratorValidationException;
import org.apache.commons.lang.StringUtils;

import java.util.function.Predicate;

public final class ValidationUtils {

    private ValidationUtils() {
        // Utility class
    }

    /**
     * Validates that a value is not null
     *
     * @param value The value to validate
     * @param fieldName The name of the field for error messages
     * @param <T> The type of the value
     * @return The validated value
     * @throws GeneratorValidationException if validation fails
     */
    public static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new GeneratorValidationException("Field '%s' cannot be null", fieldName);
        }
        return value;
    }

    /**
     * Validates that a string is not null or empty
     *
     * @param value The string to validate
     * @param fieldName The name of the field for error messages
     * @return The validated string
     * @throws GeneratorValidationException if validation fails
     */
    public static String requireNonEmpty(String value, String fieldName) {
        if (StringUtils.isEmpty(value)) {
            throw new GeneratorValidationException("Field '%s' cannot be null or empty", fieldName);
        }
        return value;
    }

    /**
     * Validates that a string is not null or blank
     *
     * @param value The string to validate
     * @param fieldName The name of the field for error messages
     * @return The validated string
     * @throws GeneratorValidationException if validation fails
     */
    public static String requireNonBlank(String value, String fieldName) {
        if (StringUtils.isBlank(value)) {
            throw new GeneratorValidationException("Field '%s' cannot be null or blank", fieldName);
        }
        return value;
    }

    /**
     * Validates that a numeric value is within the specified range
     *
     * @param value The value to validate
     * @param min The minimum allowed value (inclusive)
     * @param max The maximum allowed value (inclusive)
     * @param fieldName The name of the field for error messages
     * @return The validated value
     * @throws GeneratorValidationException if validation fails
     */
    public static int requireInRange(int value, int min, int max, String fieldName) {
        if (value < min || value > max) {
            throw new GeneratorValidationException("Field '%s' must be between %d and %d, but was %d",
                fieldName, String.valueOf(min), String.valueOf(max), String.valueOf(value));
        }
        return value;
    }

    /**
     * Validates that a value meets a custom condition
     *
     * @param value The value to validate
     * @param condition The condition to check
     * @param fieldName The name of the field for error messages
     * @param errorMessage The error message if validation fails
     * @param <T> The type of the value
     * @return The validated value
     * @throws GeneratorValidationException if validation fails
     */
    public static <T> T requireCondition(T value, Predicate<T> condition, String fieldName, String errorMessage) {
        if (!condition.test(value)) {
            throw new GeneratorValidationException("Field '%s' validation failed: %s", fieldName, errorMessage);
        }
        return value;
    }

    /**
     * Validates that a value is positive
     *
     * @param value The value to validate
     * @param fieldName The name of the field for error messages
     * @return The validated value
     * @throws GeneratorValidationException if validation fails
     */
    public static int requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new GeneratorValidationException("Field '%s' must be positive, but was %d", fieldName, String.valueOf(value));
        }
        return value;
    }

    /**
     * Validates that a value is non-negative
     *
     * @param value The value to validate
     * @param fieldName The name of the field for error messages
     * @return The validated value
     * @throws GeneratorValidationException if validation fails
     */
    public static int requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new GeneratorValidationException("Field '%s' must be non-negative, but was %d", fieldName, String.valueOf(value));
        }
        return value;
    }
}