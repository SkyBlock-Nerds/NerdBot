package net.hypixel.nerdbot.service.orangejuice;

import net.hypixel.nerdbot.service.orangejuice.requestmodels.generator.ItemGeneratorRequest;

import java.io.IOException;

public class generateItemService extends generatorBaseService {

    public byte[] generateItem(ItemGeneratorRequest data) throws IOException, InterruptedException {
        return generateImage("/item", data);
    }
}