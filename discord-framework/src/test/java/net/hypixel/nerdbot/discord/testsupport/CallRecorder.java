package net.hypixel.nerdbot.discord.testsupport;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fakes for JDA interfaces that record every call made on them, for asserting what a handler sent
 * back to Discord.
 *
 * <p>A fake made here answers a call in this order:
 * <ol>
 *     <li>a method named in {@code answers} returns that value, or runs it when it's an {@link Answer};</li>
 *     <li>a default method runs its real implementation, so {@code reply(String)} goes through
 *     {@code deferReply()} like it does in JDA;</li>
 *     <li>a method whose return type the fake itself satisfies returns the fake, so builder chains
 *     such as {@code setEphemeral(true).queue()} keep recording;</li>
 *     <li>a method returning another interface returns a recording fake of that interface, the same
 *     one each time, so {@code getHook()} called twice gives one hook;</li>
 *     <li>anything else returns the same empty, zero, false or null value as {@link Fakes}.</li>
 * </ol>
 */
public final class CallRecorder {

    @FunctionalInterface
    public interface Answer {
        Object answer(Object[] args);
    }

    /** One call. A varargs method's trailing array is flattened into {@code args}. */
    public record Call(Class<?> type, String method, List<Object> args) {

        public Object arg(int index) {
            return args.get(index);
        }
    }

    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private final Map<Object, Map<Method, Object>> nestedFakes = new ConcurrentHashMap<>();

    public <T> T fake(Class<T> type) {
        return fake(type, Map.of());
    }

    public <T> T fake(Class<T> type, Map<String, ?> answers) {
        InvocationHandler handler = (self, method, args) -> {
            Object[] arguments = args == null ? new Object[0] : args;

            switch (method.getName()) {
                case "toString" -> {
                    if (arguments.length == 0) {
                        return "Recorded" + type.getSimpleName();
                    }
                }
                case "hashCode" -> {
                    if (arguments.length == 0) {
                        return System.identityHashCode(self);
                    }
                }
                case "equals" -> {
                    if (arguments.length == 1) {
                        return self == arguments[0];
                    }
                }
                default -> {
                }
            }

            calls.add(new Call(type, method.getName(), flatten(method, arguments)));

            if (answers.containsKey(method.getName())) {
                Object answer = answers.get(method.getName());
                return answer instanceof Answer function ? function.answer(arguments) : answer;
            }

            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(self, method, args);
            }

            Class<?> returnType = method.getReturnType();
            if (returnType != Object.class && returnType.isInstance(self)) {
                return self;
            }

            if (returnType.isInterface() && !Collection.class.isAssignableFrom(returnType) && !Map.class.isAssignableFrom(returnType)) {
                return nestedFakes.computeIfAbsent(self, ignored -> new ConcurrentHashMap<>())
                    .computeIfAbsent(method, ignored -> fake(returnType));
            }

            return Fakes.defaultValue(returnType);
        };

        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    public List<Call> calls() {
        return List.copyOf(calls);
    }

    public List<Call> calls(String method) {
        return calls.stream().filter(call -> call.method().equals(method)).toList();
    }

    public boolean called(String method) {
        return !calls(method).isEmpty();
    }

    /**
     * @throws AssertionError when {@code method} was never called
     */
    public Call last(String method) {
        List<Call> matching = calls(method);
        if (matching.isEmpty()) {
            throw new AssertionError("Expected a call to " + method + " but got " + calls.stream().map(Call::method).toList());
        }
        return matching.getLast();
    }

    private static List<Object> flatten(Method method, Object[] args) {
        List<Object> flattened = new ArrayList<>(Arrays.asList(args));
        if (method.isVarArgs() && !flattened.isEmpty() && flattened.getLast() instanceof Object[] varargs) {
            flattened.removeLast();
            flattened.addAll(Arrays.asList(varargs));
        }
        return flattened;
    }
}
