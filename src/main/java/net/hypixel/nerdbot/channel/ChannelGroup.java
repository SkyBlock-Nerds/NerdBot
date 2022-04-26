package net.hypixel.nerdbot.channel;

public class ChannelGroup {

    private String name;

    private String guildId;

    private String from;

    private String to;

    public ChannelGroup() {
    }

    public ChannelGroup(String name, String guildId, String from, String to) {
        this.name = name;
        this.guildId = guildId;
        this.from = from;
        this.to = to;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

}
