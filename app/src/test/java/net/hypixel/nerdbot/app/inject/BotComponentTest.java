package net.hypixel.nerdbot.app.inject;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.tools.pack.ResourcePackService;
import net.aerh.slashcommands.api.annotations.SlashAutocompleteHandler;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashComponentHandler;
import net.aerh.slashcommands.api.annotations.SlashModalHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.discord.cache.MessageCache;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import net.hypixel.nerdbot.marmalade.storage.database.Database;
import net.hypixel.nerdbot.marmalade.storage.database.repository.PunishmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BotComponentTest {

    private static final List<Class<? extends Annotation>> HANDLER_ANNOTATIONS = List.of(
        SlashCommand.class, SlashComponentHandler.class, SlashModalHandler.class, SlashAutocompleteHandler.class);

    // No URI, so no connection and no repositories registered
    private final BotComponent component = DaggerBotComponent.factory().create(Fakes.of(JDA.class, Map.of()), new Database(null, null), Fakes.of(BotState.class, Map.of()),
        new ResourcePackService(new PackRepository()));

    @Test
    void registersEveryClassWithSlashHandlersExactlyOnce() {
        Set<Object> commands = component.slashCommands();
        Set<Class<?>> registered = commands.stream().map(Object::getClass).collect(Collectors.toSet());

        assertEquals(commandClasses(), registered);
        assertEquals(registered.size(), commands.size());
    }

    @Test
    void commandsBuildWithoutADatabase() {
        assertEquals(commandClasses().size(), component.slashCommands().size());
    }

    @Test
    void registersEveryEventListenerExactlyOnce() {
        Set<Class<?>> registered = component.eventListeners().stream().map(Object::getClass).collect(Collectors.toSet());
        Set<Class<?>> expected = new HashSet<>(appClasses(type -> Arrays.stream(type.getDeclaredMethods()).anyMatch(method -> method.isAnnotationPresent(SubscribeEvent.class))));
        expected.add(MessageCache.class);

        assertEquals(expected, registered);
        assertEquals(registered.size(), component.eventListeners().size());
    }

    @Test
    void everyFeatureCanBeBuilt() {
        Set<Class<?>> features = appClasses(type -> BotFeature.class.isAssignableFrom(type) && !Modifier.isAbstract(type.getModifiers()));

        assertEquals(features, component.features().keySet());
        component.features().forEach((type, provider) -> assertInstanceOf(type, provider.get()));
    }

    @Test
    void theSchedulerIsSharedAcrossTheComponent() {
        assertSame(component.scheduler(), component.scheduler());
        assertSame(component.reminderScheduler(), component.reminderScheduler());
    }

    @Test
    void missingRepositorySaysWhy() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
            () -> StorageModule.repository(new Database(null, null), PunishmentRepository.class));

        assertEquals("PunishmentRepository is not registered; is the database connected?", thrown.getMessage());
    }

    private static Set<Class<?>> commandClasses() {
        return appClasses(type -> Arrays.stream(type.getDeclaredMethods()).anyMatch(BotComponentTest::isHandler));
    }

    private static Set<Class<?>> appClasses(Predicate<Class<?>> matching) {
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("net.hypixel.nerdbot.app")
            .stream()
            .map(JavaClass::reflect)
            .filter(matching)
            .collect(Collectors.toSet());
    }

    private static boolean isHandler(Method method) {
        return HANDLER_ANNOTATIONS.stream().anyMatch(method::isAnnotationPresent);
    }

    @AfterEach
    void stopThreads() {
        component.scheduler().shutdownNow();
        component.backgroundWorkPool().shutdownNow();
        component.imageRenderingPool().shutdownNow();
        component.memberWorkPool().shutdownNow();
    }
}
