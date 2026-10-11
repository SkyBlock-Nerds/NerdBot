package net.hypixel.nerdbot.app.storage;

/** Every member gets a record when they join, so this is a bug to log rather than an answer to give. */
public class MissingUserRecordException extends IllegalStateException {

    public MissingUserRecordException(String discordId) {
        super("Member " + discordId + " has no user record");
    }
}
