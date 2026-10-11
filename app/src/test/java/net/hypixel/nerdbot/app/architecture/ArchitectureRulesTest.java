package net.hypixel.nerdbot.app.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import net.dv8tion.jda.api.requests.RestAction;
import org.junit.jupiter.api.Test;

import java.util.Timer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

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
        noClasses().that().resideOutsideOfPackage("..app.command..")
            .should().dependOnClassesThat().resideInAPackage("..app.command..")
            .because("commands sit at the edge; shared logic belongs in a service other code can use")
            .check(CLASSES);
    }

    private static DescribedPredicate<JavaMethodCall> calling(Class<?> owner, String methodName) {
        return DescribedPredicate.describe(owner.getSimpleName() + "." + methodName + "()",
            call -> call.getName().equals(methodName) && call.getTargetOwner().isAssignableTo(owner));
    }

    private static DescribedPredicate<JavaConstructorCall> constructing(Class<?> type) {
        return DescribedPredicate.describe("new " + type.getSimpleName() + "()", call -> call.getTargetOwner().isAssignableTo(type));
    }
}
