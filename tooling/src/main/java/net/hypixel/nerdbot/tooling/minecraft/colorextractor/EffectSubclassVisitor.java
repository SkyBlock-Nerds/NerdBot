package net.hypixel.nerdbot.tooling.minecraft.colorextractor;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * ASM visitor that extracts color from a MobEffect subclass constructor.
 */
public class EffectSubclassVisitor extends ClassVisitor {

    private final String mobEffectClass;
    private final String effectName = null;
    private int color = 0;

    public EffectSubclassVisitor(String mobEffectClass) {
        super(Opcodes.ASM9);
        this.mobEffectClass = mobEffectClass;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (name.equals("<init>")) {
            return new ConstructorVisitor();
        }
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }

    public String getEffectName() {
        return effectName;
    }

    public int getColor() {
        return color;
    }

    private class ConstructorVisitor extends MethodVisitor {
        private int lastInt = 0;
        private boolean foundLargeInt = false;

        ConstructorVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visitLdcInsn(Object value) {
            if (value instanceof Integer i && Math.abs(i) > 100000) {
                lastInt = i;
                foundLargeInt = true;
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (name.equals("<init>") && owner.equals(mobEffectClass) && foundLargeInt) {
                color = lastInt;
            }
        }
    }
}