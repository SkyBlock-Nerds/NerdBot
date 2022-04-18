package net.hypixel.nerdbot.channel;

public enum Channel {

    SUGGESTIONS("965225519891251240"),
    GREENLIT("965225533933760542"),
    CURATE("965243148597088367");

    private String id;

    Channel(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

}
