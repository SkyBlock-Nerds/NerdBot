package net.hypixel.nerdbot.discord.testsupport;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JDK-proxy fakes for JDA interfaces, so tests don't need a mocking library.
 *
 * <p>Methods named in {@code answers} return the given value, or run it when it's a
 * {@link CallRecorder.Answer}. Anything else returns an empty collection, zero or false, or null
 * for other object types, which keeps a fake small when the code under test only touches a few
 * methods.
 */
public final class Fakes {

    private static final Map<Class<?>, Object> PRIMITIVE_DEFAULTS = Map.of(
        boolean.class, false,
        byte.class, (byte) 0,
        short.class, (short) 0,
        int.class, 0,
        long.class, 0L,
        float.class, 0f,
        double.class, 0d,
        char.class, '\0'
    );

    private Fakes() {
    }

    public static <T> T of(Class<T> type, Map<String, Object> answers) {
        Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) -> {
            if (answers.containsKey(method.getName())) {
                Object answer = answers.get(method.getName());
                return answer instanceof CallRecorder.Answer function ? function.answer(args == null ? new Object[0] : args) : answer;
            }

            return switch (method.getName()) {
                case "toString" -> "Fake" + type.getSimpleName() + answers;
                case "hashCode" -> System.identityHashCode(self);
                case "equals" -> self == args[0];
                default -> defaultValue(method.getReturnType());
            };
        });

        return type.cast(proxy);
    }

    static Object defaultValue(Class<?> returnType) {
        if (returnType.isPrimitive()) {
            return PRIMITIVE_DEFAULTS.get(returnType);
        }
        if (List.class.isAssignableFrom(returnType)) {
            return List.of();
        }
        if (Set.class.isAssignableFrom(returnType)) {
            return Set.of();
        }
        if (Map.class.isAssignableFrom(returnType)) {
            return Map.of();
        }

        return null;
    }
}
