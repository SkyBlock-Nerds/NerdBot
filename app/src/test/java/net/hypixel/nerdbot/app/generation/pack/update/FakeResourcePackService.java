package net.hypixel.nerdbot.app.generation.pack.update;

import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.tools.pack.PackDefinition;
import net.aerh.imagegenerator.tools.pack.PackExpectations;
import net.aerh.imagegenerator.tools.pack.PackRegistrationConfig;
import net.aerh.imagegenerator.tools.pack.PackReloadResult;
import net.aerh.imagegenerator.tools.pack.ResourcePackService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Answers reloads with a configurable result instead of loading the pack. */
class FakeResourcePackService extends ResourcePackService {

    record Reload(PackDefinition definition, Path zip, PackExpectations expectations) {
    }

    final List<Reload> reloads = new ArrayList<>();
    final List<PackRegistrationConfig> registrations = new ArrayList<>();
    private Function<Path, PackReloadResult> answer = zip -> applied(100, 90);

    FakeResourcePackService() {
        super(new PackRepository());
    }

    static PackReloadResult.Applied applied(int itemCount, int previousItemCount) {
        return new PackReloadResult.Applied(PackId.parse(PackFixtures.PACK_ID), itemCount, previousItemCount);
    }

    FakeResourcePackService answering(PackReloadResult result) {
        this.answer = zip -> result;
        return this;
    }

    FakeResourcePackService answering(Function<Path, PackReloadResult> answer) {
        this.answer = answer;
        return this;
    }

    FakeResourcePackService throwing(RuntimeException exception) {
        this.answer = zip -> {
            throw exception;
        };
        return this;
    }

    @Override
    public PackReloadResult reloadPack(PackDefinition definition, Path zip, PackExpectations expectations) {
        reloads.add(new Reload(definition, zip, expectations));
        return answer.apply(zip);
    }

    @Override
    public void registerConfiguredPacks(PackRegistrationConfig config) {
        registrations.add(config);
    }
}
