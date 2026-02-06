package net.hypixel.nerdbot.tooling.minecraft.colorextractor;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * ASM visitor that maps MobEffects field names to effect string IDs.
 */
public class EffectFieldMapper extends ClassVisitor {

    private final Map<String, String> fieldToName = new HashMap<>();
    private final Consumer<String> debugLog;

    public EffectFieldMapper(Consumer<String> debugLog) {
        super(Opcodes.ASM9);
        this.debugLog = debugLog;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (name.equals("<clinit>")) {
            return new StaticInitVisitor();
        }

        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }

    public Map<String, String> getFieldToName() {
        return fieldToName;
    }

    private class StaticInitVisitor extends MethodVisitor {
        private String pendingName = null;

        StaticInitVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visitLdcInsn(Object value) {
            if (value instanceof String str && isEffectName(str)) {
                pendingName = str;
            }
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String fieldName, String descriptor) {
            if (opcode == Opcodes.PUTSTATIC && pendingName != null) {
                fieldToName.put(fieldName, pendingName);
                debugLog.accept("MobEffects field '" + fieldName + "' = '" + pendingName + "'");
                pendingName = null;
            }
        }

        private boolean isEffectName(String str) {
            return !str.contains(":") && !str.contains("/") && !str.contains(".") && str.length() < 30;
        }
    }
}