package net.hypixel.skyblocknerds.discordbot.embed;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.List;

@Getter
@AllArgsConstructor
public class CustomEmbedCollection {

    private final List<EmbedCreator> embeds;

    public static CustomEmbedCollection of(EmbedCreator... embeds) {
        return new CustomEmbedCollection(List.of(embeds));
    }

    public List<MessageEmbed> build() {
        return embeds.stream()
            .map(embedCreator -> embedCreator.create().build())
            .toList();
    }
}
