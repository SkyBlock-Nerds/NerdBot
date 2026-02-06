package net.hypixel.nerdbot.generator.util;

import net.hypixel.nerdbot.generator.exception.GeneratorValidationException;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A result type that represents either a successful value or a user-facing error message.
 * Use this for user input validation instead of exceptions.
 *
 * @param <T> the type of the success value
 */
public sealed interface GeneratorResult<T> permits GeneratorResult.Success, GeneratorResult.Error {

    static <T> GeneratorResult<T> success(T value) {
        return new Success<>(value);
    }

    static <T> GeneratorResult<T> error(String message) {
        return new Error<>(message);
    }

    /**
     * Wraps a supplier that may throw GeneratorValidationException into a GeneratorResult.
     * User validation exceptions become Error results; actual exceptions propagate.
     */
    static <T> GeneratorResult<T> wrap(Supplier<T> supplier) {
        try {
            return success(supplier.get());
        } catch (GeneratorValidationException e) {
            return error(e.getMessage());
        }
    }

    boolean isSuccess();

    boolean isError();

    T getValue();

    String getErrorMessage();

    /**
     * Maps the success value to a new value.
     */
    <U> GeneratorResult<U> map(Function<T, U> mapper);

    /**
     * FlatMaps the success value to a new result.
     */
    <U> GeneratorResult<U> flatMap(Function<T, GeneratorResult<U>> mapper);

    /**
     * FlatMaps with a supplier that may throw GeneratorValidationException.
     * Combines flatMap with wrap for convenience.
     */
    <U> GeneratorResult<U> flatMapWrap(Function<T, U> mapper);

    /**
     * Handles both success and error cases. Terminal operation.
     */
    void handle(Consumer<T> onSuccess, Consumer<String> onError);

    /**
     * Runs the consumer if this is an error, then returns this result unchanged.
     * Useful for side effects like logging or sending error messages.
     */
    GeneratorResult<T> onError(Consumer<String> consumer);

    /**
     * Runs the consumer if this is a success, then returns this result unchanged.
     * Useful for side effects like logging or sending success responses.
     */
    GeneratorResult<T> onSuccess(Consumer<T> consumer);

    /**
     * Returns the value if success, otherwise throws IllegalStateException.
     */
    T orElseThrow();

    record Success<T>(T value) implements GeneratorResult<T> {
        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public boolean isError() {
            return false;
        }

        @Override
        public T getValue() {
            return value;
        }

        @Override
        public String getErrorMessage() {
            return null;
        }

        @Override
        public <U> GeneratorResult<U> map(Function<T, U> mapper) {
            return new Success<>(mapper.apply(value));
        }

        @Override
        public <U> GeneratorResult<U> flatMap(Function<T, GeneratorResult<U>> mapper) {
            return mapper.apply(value);
        }

        @Override
        public <U> GeneratorResult<U> flatMapWrap(Function<T, U> mapper) {
            return wrap(() -> mapper.apply(value));
        }

        @Override
        public void handle(Consumer<T> onSuccess, Consumer<String> onError) {
            onSuccess.accept(value);
        }

        @Override
        public GeneratorResult<T> onError(Consumer<String> consumer) {
            return this;
        }

        @Override
        public GeneratorResult<T> onSuccess(Consumer<T> consumer) {
            consumer.accept(value);
            return this;
        }

        @Override
        public T orElseThrow() {
            return value;
        }
    }

    record Error<T>(String message) implements GeneratorResult<T> {
        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public boolean isError() {
            return true;
        }

        @Override
        public T getValue() {
            return null;
        }

        @Override
        public String getErrorMessage() {
            return message;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <U> GeneratorResult<U> map(Function<T, U> mapper) {
            return (GeneratorResult<U>) this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <U> GeneratorResult<U> flatMap(Function<T, GeneratorResult<U>> mapper) {
            return (GeneratorResult<U>) this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <U> GeneratorResult<U> flatMapWrap(Function<T, U> mapper) {
            return (GeneratorResult<U>) this;
        }

        @Override
        public void handle(Consumer<T> onSuccess, Consumer<String> onError) {
            onError.accept(message);
        }

        @Override
        public GeneratorResult<T> onError(Consumer<String> consumer) {
            consumer.accept(message);
            return this;
        }

        @Override
        public GeneratorResult<T> onSuccess(Consumer<T> consumer) {
            return this;
        }

        @Override
        public T orElseThrow() {
            throw new IllegalStateException("Result is an error: " + message);
        }
    }
}
