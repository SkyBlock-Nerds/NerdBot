package net.hypixel.nerdbot.tooling.minecraft.colorextractor;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * ASM visitor that finds which effects are referenced by potions.
 */
public class PotionVisitor extends ClassVisitor {

    private final String mobEffectsClass;
    private final Map<String, String> fieldToEffectName;
    private final Set<String> effectsWithPotions = new HashSet<>();
    private final Consumer<String> debugLog;

    public PotionVisitor(String mobEffectsClass, Map<String, String> fieldToEffectName, Consumer<String> debugLog) {
        super(Opcodes.ASM9);
        this.mobEffectsClass = mobEffectsClass;
        this.fieldToEffectName = fieldToEffectName;
        this.debugLog = debugLog;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (name.equals("<clinit>")) {
            return new StaticInitVisitor();
        }

        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }

    public Set<String> getEffectsWithPotions() {
        return effectsWithPotions;
    }

    private class StaticInitVisitor extends MethodVisitor {
        StaticInitVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String fieldName, String descriptor) {
            if (opcode == Opcodes.GETSTATIC && owner.equals(mobEffectsClass)) {
                String effectName = fieldToEffectName.get(fieldName);
                if (effectName != null) {
                    effectsWithPotions.add(effectName);
                    debugLog.accept("Potion uses effect: " + effectName);
                }
            }
        }
    }
}