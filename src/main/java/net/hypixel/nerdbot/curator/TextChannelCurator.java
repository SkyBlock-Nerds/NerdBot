package net.hypixel.nerdbot.curator;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.hypixel.nerdbot.api.database.GreenlitMessage;

import java.util.List;

public class TextChannelCurator extends Curator<TextChannel> {

    @Override
    public List<GreenlitMessage> curate(List<TextChannel> list) {
        // TODO implement the old curator
        return null;
    }
}
