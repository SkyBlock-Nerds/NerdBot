package net.hypixel.nerdbot.app.profile;

import jakarta.inject.Inject;
import net.hypixel.nerdbot.app.util.HttpUtils;
import net.hypixel.nerdbot.marmalade.exception.HttpException;
import net.hypixel.nerdbot.marmalade.functional.Result;
import net.hypixel.nerdbot.marmalade.json.HypixelPlayerResponse;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.MojangProfile;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class HttpMinecraftAccounts implements MinecraftAccounts {

    @Inject
    public HttpMinecraftAccounts() {
    }

    @Override
    public CompletableFuture<Result<MojangProfile, HttpException>> mojangProfile(String username) {
        return HttpUtils.getMojangProfileAsync(username);
    }

    @Override
    public CompletableFuture<Result<HypixelPlayerResponse, HttpException>> hypixelPlayer(UUID uniqueId) {
        return HttpUtils.getHypixelPlayerAsync(uniqueId);
    }
}
