package net.hypixel.nerdbot.tooling.minecraft.colorextractor;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * ASM visitor that extracts effect colors from the MobEffects registry.
 */
public class EffectRegistryVisitor extends ClassVisitor {

    private final Map<String, Integer> colors = new LinkedHashMap<>();
    private final Consumer<String> debugLog;

    public EffectRegistryVisitor(Consumer<String> debugLog) {
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

    public Map<String, Integer> getColors() {
        return colors;
    }

    private class StaticInitVisitor extends MethodVisitor {
        private final List<Integer> pendingInts = new ArrayList<>();
        private String pendingName = null;

        StaticInitVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visitLdcInsn(Object value) {
            if (value instanceof String str && isEffectName(str)) {
                pendingName = str;
                pendingInts.clear();
            } else if (value instanceof Integer i) {
                pendingInts.add(i);
            }
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
                pendingInts.add(operand);
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String mname, String descriptor, boolean isInterface) {
            if (mname.equals("<init>") && pendingName != null) {
                for (int val : pendingInts) {
                    if (Math.abs(val) > 100000) {
                        colors.put(pendingName, val);
                        debugLog.accept("Found MobEffect: " + pendingName + " -> " + val);
                        break;
                    }
                }
            }
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            if (opcode == Opcodes.PUTSTATIC) {
                pendingName = null;
                pendingInts.clear();
            }
        }

        private boolean isEffectName(String str) {
            return !str.contains(":") && !str.contains("/") && !str.contains(".") && str.length() < 30;
        }
    }
}