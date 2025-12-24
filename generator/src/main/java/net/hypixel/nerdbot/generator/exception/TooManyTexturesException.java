package net.hypixel.nerdbot.generator.exception;

public class TooManyTexturesException extends NbtParseException {

    public TooManyTexturesException() {
        super("There seems to be more than 1 texture in the player head's NBT data.");
    }

    public TooManyTexturesException(String message) {
        super(message);
    }
}
