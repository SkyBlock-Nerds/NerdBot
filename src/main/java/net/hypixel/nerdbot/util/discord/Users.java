package net.hypixel.nerdbot.util.discord;

import net.dv8tion.jda.api.entities.User;
import net.hypixel.nerdbot.NerdBotApp;

public enum Users {
    AERH("165438405155487744");

    private final String userId;

    Users(String userId) {
        this.userId = userId;
    }

    public static User getUser(String userId) {
        return NerdBotApp.getBot().getJDA().retrieveUserById(userId).complete();
    }

    public static User getUser(Users user) {
        return getUser(user.getUserId());
    }

    public String getUserId() {
        return userId;
    }
}
