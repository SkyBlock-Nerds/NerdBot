package net.hypixel.nerdbot.tooling.minecraft.colorextractor;

import net.hypixel.nerdbot.tooling.minecraft.DyeColorData;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * ASM visitor that extracts color values from DyeColor enum.
 */
public class DyeColorVisitor extends ClassVisitor {

    private final Map<String, DyeColorData> colors = new LinkedHashMap<>();
    private final Consumer<String> debugLog;

    public DyeColorVisitor(Consumer<String> debugLog) {
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

    public Map<String, DyeColorData> getColors() {
        return colors;
    }

    private class StaticInitVisitor extends MethodVisitor {
        private final List<Integer> pendingInts = new ArrayList<>();
        private String pendingEnumName = null;

        StaticInitVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visitLdcInsn(Object value) {
            if (value instanceof String str) {
                pendingEnumName = str;
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
        public void visitInsn(int opcode) {
            if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) {
                pendingInts.add(opcode - Opcodes.ICONST_0);
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (name.equals("<init>") && pendingEnumName != null && pendingInts.size() >= 4) {
                List<Integer> colorCandidates = new ArrayList<>();
                for (int val : pendingInts) {
                    if (val > 100000 || val < -100000) {
                        colorCandidates.add(val);
                    }
                }

                if (colorCandidates.size() >= 2) {
                    int dyeColor = colorCandidates.get(0);
                    int fireworkColor = colorCandidates.get(1);
                    colors.put(pendingEnumName.toLowerCase(), new DyeColorData(dyeColor, fireworkColor));
                    debugLog.accept("Found DyeColor: " + pendingEnumName + " -> dye=" + dyeColor + ", firework=" + fireworkColor);
                }

                pendingEnumName = null;
                pendingInts.clear();
            }
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            if (opcode == Opcodes.PUTSTATIC) {
                pendingEnumName = null;
                pendingInts.clear();
            }
        }
    }
}