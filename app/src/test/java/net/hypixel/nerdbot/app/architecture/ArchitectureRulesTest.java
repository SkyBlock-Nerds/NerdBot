package net.hypixel.nerdbot.app.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.requests.RestAction;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.inject.BotState;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.cache.ChannelCache;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.Timer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rules the cleanup agreed on. Rules wrapped in {@link FreezingArchRule} only fail on new
 * violations: the ones that existed when the rule was added are recorded under
 * src/test/resources/archunit-frozen-violations and drop out of that file as they get fixed, so
 * these can only get stricter.
 */
class ArchitectureRulesTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("net.hypixel.nerdbot.app", "net.hypixel.nerdbot.discord");

    /**
     * Code that gets what it needs through its constructor. Add to these as more of the bot moves
     * over; nothing in them may go back to looking things up through the global bot.
     */
    private static final String[] INJECTED_PACKAGES = {"..app.inject..", "..app.profile..", "..app.punishment..", "..app.reminder.."};
    private static final String[] INJECTED_CLASSES = {
        "net.hypixel.nerdbot.app.badge.BadgeCatalog",
        "net.hypixel.nerdbot.app.badge.BadgeService",
        "net.hypixel.nerdbot.app.badge.ConfiguredBadgeCatalog",
        "net.hypixel.nerdbot.app.channel.ChannelAccess",
        "net.hypixel.nerdbot.app.channel.ForumTagTransfer",
        "net.hypixel.nerdbot.app.channel.LogChannel",
        "net.hypixel.nerdbot.app.channel.RestrictedChannelGroups",
        "net.hypixel.nerdbot.app.command.BadgeCommands",
        "net.hypixel.nerdbot.app.command.CacheCommands",
        "net.hypixel.nerdbot.app.command.ChannelCommands",
        "net.hypixel.nerdbot.app.command.ConfigCommands",
        "net.hypixel.nerdbot.app.command.CurateCommands",
        "net.hypixel.nerdbot.app.command.DriveCommands",
        "net.hypixel.nerdbot.app.command.ExportCommands",
        "net.hypixel.nerdbot.app.command.GeneratorCommands",
        "net.hypixel.nerdbot.app.command.GeneratorRequests",
        "net.hypixel.nerdbot.app.command.InfoCommands",
        "net.hypixel.nerdbot.app.command.InviteCommands",
        "net.hypixel.nerdbot.app.command.NominationCommands",
        "net.hypixel.nerdbot.app.command.PackCommands",
        "net.hypixel.nerdbot.app.command.PostCommands",
        "net.hypixel.nerdbot.app.command.ProfileCommands",
        "net.hypixel.nerdbot.app.command.PunishmentCommands",
        "net.hypixel.nerdbot.app.command.ReminderCommands",
        "net.hypixel.nerdbot.app.command.RestrictedChannelCommands",
        "net.hypixel.nerdbot.app.command.RoleCommands",
        "net.hypixel.nerdbot.app.command.SuggestionCommands",
        "net.hypixel.nerdbot.app.command.TagTransferCommands",
        "net.hypixel.nerdbot.app.command.UserCommands",
        "net.hypixel.nerdbot.app.command.util.CommandReplies",
        "net.hypixel.nerdbot.app.command.util.PanelId",
        "net.hypixel.nerdbot.app.command.util.ProgressUpdates",
        "net.hypixel.nerdbot.app.command.util.SuggestionCommandUtils",
        "net.hypixel.nerdbot.app.command.util.SuggestionListings",
        "net.hypixel.nerdbot.app.config.ConfigEditor",
        "net.hypixel.nerdbot.app.config.ConfigStore",
        "net.hypixel.nerdbot.app.generation.DiscordGenerationContexts",
        "net.hypixel.nerdbot.app.metrics.MetricsServer",
        "net.hypixel.nerdbot.app.nomination.ActivityRequirements",
        "net.hypixel.nerdbot.app.nomination.NominationSweeps",
        "net.hypixel.nerdbot.app.storage.GreenlitStore",
        "net.hypixel.nerdbot.app.storage.MissingUserRecordException",
        "net.hypixel.nerdbot.app.storage.RepositoryDiscordUserStore",
        "net.hypixel.nerdbot.app.storage.RepositoryGreenlitStore",
        "net.hypixel.nerdbot.app.suggestion.SuggestionAutoArchiver",
        "net.hypixel.nerdbot.app.suggestion.SuggestionCache",
        "net.hypixel.nerdbot.app.suggestion.SuggestionFactory",
        "net.hypixel.nerdbot.app.user.BirthdayService"
    };

    @Test
    void nothingWritesToStandardOutOrErr() {
        NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.because("everything should go through the logger").check(CLASSES);
    }

    @Test
    void nothingPrintsStackTracesDirectly() {
        noClasses().should().callMethodWhere(calling(Throwable.class, "printStackTrace"))
            .because("stack traces should go through the logger")
            .check(CLASSES);
    }

    @Test
    void noNewTimerThreads() {
        ArchRule rule = noClasses().should().callConstructorWhere(constructing(Timer.class))
            .because("each Timer is its own thread; scheduled work should share one scheduler");
        FreezingArchRule.freeze(rule).check(CLASSES);
    }

    @Test
    void commandsAndListenersDoNotBlockOnRestActions() {
        ArchRule rule = noClasses().that().resideInAnyPackage("..app.command..", "..app.listener..")
            .should().callMethodWhere(calling(RestAction.class, "complete"))
            .because("blocking a JDA event or interaction thread stalls every other event; use queue() or submit()");
        FreezingArchRule.freeze(rule).check(CLASSES);
    }

    @Test
    void onlyCommandsUseCommandClasses() {
        noClasses().that().resideOutsideOfPackages("..app.command..", "..app.inject..")
            .should().dependOnClassesThat().resideInAPackage("..app.command..")
            .because("commands sit at the edge, wired up only by app.inject; shared logic belongs in a service other code can use")
            .check(CLASSES);
    }

    @Test
    void injectedCodeDoesNotReachForTheGlobalBot() {
        noClasses().that().resideInAnyPackage(INJECTED_PACKAGES).or(injectedClass())
            .should().dependOnClassesThat().belongToAnyOf(BotEnvironment.class, DiscordBotEnvironment.class, SkyBlockNerdsBot.class, ChannelCache.class)
            .because("injected code gets its dependencies through its constructor, so tests can hand it fakes")
            .check(CLASSES);
    }

    /**
     * The config is replaced by {@code /config reload}, and the pack updater and Drive service only
     * exist once startup reaches them, so a class handed one of these at construction would keep a
     * stale or empty copy. They have to come through a {@code Provider}.
     */
    @Test
    void liveValuesAreInjectedThroughAProvider() {
        Set<String> liveTypes = Set.of(NerdBotConfig.class.getName(), Optional.class.getName(), BotState.class.getName());

        constructors().that().areAnnotatedWith(Inject.class)
            .and().areDeclaredInClassesThat().resideOutsideOfPackage("..app.inject..")
            .should(new ArchCondition<JavaConstructor>("not take the config, an Optional or BotState directly") {
                @Override
                public void check(JavaConstructor constructor, ConditionEvents events) {
                    constructor.getRawParameterTypes().stream()
                        .filter(type -> liveTypes.contains(type.getName()))
                        .forEach(type -> events.add(SimpleConditionEvent.violated(constructor,
                            constructor.getFullName() + " takes " + type.getSimpleName() + "; inject Provider<" + type.getSimpleName() + "> instead")));
                }
            })
            .check(CLASSES);
    }

    @Test
    void injectedClassListNamesRealClasses() {
        for (String name : INJECTED_CLASSES) {
            assertTrue(CLASSES.contain(name), name + " is listed as injected but doesn't exist; was it renamed?");
        }
    }

    /** A listed class or one nested in it, such as a lambda's synthetic class. */
    private static DescribedPredicate<JavaClass> injectedClass() {
        return DescribedPredicate.describe("are listed as injected", javaClass -> Arrays.stream(INJECTED_CLASSES)
            .anyMatch(name -> javaClass.getName().equals(name) || javaClass.getName().startsWith(name + "$")));
    }

    private static DescribedPredicate<JavaMethodCall> calling(Class<?> owner, String methodName) {
        return DescribedPredicate.describe(owner.getSimpleName() + "." + methodName + "()",
            call -> call.getName().equals(methodName) && call.getTargetOwner().isAssignableTo(owner));
    }

    private static DescribedPredicate<JavaConstructorCall> constructing(Class<?> type) {
        return DescribedPredicate.describe("new " + type.getSimpleName() + "()", call -> call.getTargetOwner().isAssignableTo(type));
    }
}
