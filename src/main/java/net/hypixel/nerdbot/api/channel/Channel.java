package net.hypixel.nerdbot.api.channel;

public enum Channel {

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
