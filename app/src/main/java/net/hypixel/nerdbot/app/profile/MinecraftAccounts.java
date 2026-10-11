package net.hypixel.nerdbot.app.profile;

import net.hypixel.nerdbot.marmalade.exception.HttpException;
import net.hypixel.nerdbot.marmalade.functional.Result;
import net.hypixel.nerdbot.marmalade.json.HypixelPlayerResponse;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.MojangProfile;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** The Mojang and Hypixel APIs, behind an interface so profile lookups can be tested offline. */
public interface MinecraftAccounts {

    CompletableFuture<Result<MojangProfile, HttpException>> mojangProfile(String username);

    CompletableFuture<Result<HypixelPlayerResponse, HttpException>> hypixelPlayer(UUID uniqueId);
}
