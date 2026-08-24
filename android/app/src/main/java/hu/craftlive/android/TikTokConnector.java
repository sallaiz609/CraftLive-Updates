package hu.craftlive.android;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A TikTok kliens szándékosan reflexión keresztül van bekötve. A nem hivatalos
 * TikTok protokoll változásakor így külön cserélhető a csatlakozó anélkül, hogy
 * a Bedrock-vezérlést vagy a felhasználói felületet át kellene írni.
 */
public final class TikTokConnector {
    public interface Listener {
        void onConnected();
        void onWaiting();
        void onEvent(InteractionSlot.TriggerType type, String key, int amount, String user);
        void onError(String message);
    }

    private final Listener listener;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private volatile Object builderOrClient;

    public TikTokConnector(Listener listener) {
        this.listener = listener;
    }

    public void connect(String username) {
        stopped.set(false);
        Thread thread = new Thread(() -> connectReflectively(username), "craftlive-tiktok");
        thread.setDaemon(true);
        thread.start();
    }

    public void disconnect() {
        stopped.set(true);
        Object target = builderOrClient;
        if (target instanceof CompletableFuture<?>) {
            target = ((CompletableFuture<?>) target).getNow(null);
        }
        invokeIfPresent(target, "disconnect");
        invokeIfPresent(target, "close");
        builderOrClient = null;
    }

    private void connectReflectively(String username) {
        try {
            Class<?> api = Class.forName("io.github.jwdeveloper.tiktok.TikTokLive");
            Method newClient = api.getMethod("newClient", String.class);
            Object builder = newClient.invoke(null, username);
            builder = register(builder, "onConnected", args -> listener.onConnected());
            builder = register(builder, "onReconnecting", args -> listener.onWaiting());
            builder = register(builder, "onDisconnected", args -> listener.onWaiting());
            builder = register(builder, "onGift", args -> {
                Object event = eventFrom(args);
                String gift = nestedString(event, "getGift", "getName");
                String user = userName(event);
                listener.onEvent(InteractionSlot.TriggerType.GIFT, gift, 1, user);
            });
            builder = register(builder, "onLike", args -> {
                Object event = eventFrom(args);
                int likes = number(event, "getLikes", 1);
                String user = userName(event);
                listener.onEvent(InteractionSlot.TriggerType.LIKE, "", Math.max(1, likes), user);
            });
            builder = register(builder, "onFollow", args -> listener.onEvent(
                    InteractionSlot.TriggerType.FOLLOW, "", 1,
                    userName(eventFrom(args))));
            builder = register(builder, "onSubscribe", args -> listener.onEvent(
                    InteractionSlot.TriggerType.SUBSCRIBE, "", 1,
                    userName(eventFrom(args))));
            builder = register(builder, "onShare", args -> listener.onEvent(
                    InteractionSlot.TriggerType.SHARE, "", 1,
                    userName(eventFrom(args))));
            builder = register(builder, "onComment", args -> listener.onEvent(
                    InteractionSlot.TriggerType.COMMENT,
                    string(eventFrom(args), "getText"), 1,
                    userName(eventFrom(args))));
            builder = register(builder, "onError", args -> {
                Object event = eventFrom(args);
                String message = nestedString(event, "getException", "getMessage");
                if (message.isEmpty()) message = "TikTok connection error";
                listener.onError(message);
            });

            // A könyvtár saját automatikus újracsatlakozását használjuk, ha elérhető.
            Object configured = configureRetry(builder);
            if (configured != null) builder = configured;
            Method build = findMethod(builder.getClass(), "buildAndConnectAsync", 0);
            if (build == null) build = findMethod(builder.getClass(), "buildAndConnect", 0);
            if (build == null) throw new IllegalStateException("TikTok connect method is unavailable");
            builderOrClient = build.invoke(builder);
        } catch (Throwable error) {
            if (!stopped.get()) {
                String message = error.getCause() != null ? error.getCause().getMessage() : error.getMessage();
                listener.onError(message == null ? error.getClass().getSimpleName() : message);
            }
        }
    }

    private Object configureRetry(Object builder) {
        try {
            Method configure = findMethod(builder.getClass(), "configure", 1);
            if (configure == null) return builder;
            Class<?> handlerType = configure.getParameterTypes()[0];
            Object proxy = Proxy.newProxyInstance(handlerType.getClassLoader(), new Class[]{handlerType},
                    (proxyObject, method, args) -> {
                        if (args != null && args.length > 0) {
                            Object settings = args[0];
                            invokeIfPresent(settings, "setRetryOnConnectionFailure", true);
                            invokeIfPresent(settings, "setRetryConnectionTimeout", Duration.ofSeconds(5));
                        }
                        return defaultValue(method.getReturnType());
                    });
            Object result = configure.invoke(builder, proxy);
            return result == null ? builder : result;
        } catch (Throwable ignored) {
            return builder;
        }
    }

    private Object register(Object builder, String methodName, EventConsumer consumer) throws Exception {
        Method method = findMethod(builder.getClass(), methodName, 1);
        if (method == null) return builder;
        Class<?> handlerType = method.getParameterTypes()[0];
        InvocationHandler invocationHandler = (proxyObject, called, args) -> {
            if (called.getDeclaringClass() == Object.class) return objectMethod(proxyObject, called, args);
            if (!stopped.get()) consumer.accept(args);
            return defaultValue(called.getReturnType());
        };
        Object proxy = Proxy.newProxyInstance(handlerType.getClassLoader(), new Class[]{handlerType}, invocationHandler);
        Object result = method.invoke(builder, proxy);
        return result == null ? builder : result;
    }

    private static Object eventFrom(Object[] args) {
        return args != null && args.length > 1 ? args[1] : (args != null && args.length == 1 ? args[0] : null);
    }

    private static Method findMethod(Class<?> type, String name, int parameters) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameters) return method;
        }
        return null;
    }

    private static String nestedString(Object target, String first, String second) {
        Object nested = invokeIfPresent(target, first);
        return string(nested, second);
    }

    private static String userName(Object event) {
        Object user = invokeIfPresent(event, "getUser");
        String name = string(user, "getProfileName");
        if (name.isEmpty()) name = string(user, "getName");
        return name;
    }

    private static String string(Object target, String methodName) {
        Object value = invokeIfPresent(target, methodName);
        return value == null ? "" : String.valueOf(value);
    }

    private static int number(Object target, String methodName, int fallback) {
        Object value = invokeIfPresent(target, methodName);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static Object invokeIfPresent(Object target, String methodName, Object... arguments) {
        if (target == null) return null;
        try {
            for (Method method : target.getClass().getMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == arguments.length) {
                    return method.invoke(target, arguments);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "CraftLiveTikTokHandler";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (args == null ? null : args[0]);
            default -> null;
        };
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        return null;
    }

    @FunctionalInterface
    private interface EventConsumer {
        void accept(Object[] arguments);
    }
}
