package hu.craftlive.android;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
        void onLiveEnded();
        void onGiftCatalog(List<GiftCatalogItem> gifts);
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
        if (target instanceof CompletableFuture<?> future) {
            Object connectedClient = future.getNow(null);
            if (connectedClient == null) future.cancel(true);
            target = connectedClient;
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
            builder = register(builder, "onConnected", args -> {
                listener.onGiftCatalog(extractGiftCatalog(clientFrom(args)));
                listener.onConnected();
            });
            builder = register(builder, "onReconnecting", args -> listener.onWaiting());
            builder = register(builder, "onDisconnected", args -> listener.onWaiting());
            builder = register(builder, "onLiveEnded", args -> listener.onLiveEnded());
            builder = register(builder, "onLiveUnpaused", args -> listener.onConnected());
            // A szobainformáció csak működő LIVE kapcsolatnál érkezik, ezért
            // tartalék jelzésként is aktívra állíthatja az állapotot.
            builder = register(builder, "onRoomInfo", args -> listener.onConnected());
            builder = register(builder, "onGift", args -> {
                Object event = eventFrom(args);
                Object giftObject = invokeIfPresent(event, "getGift");
                String gift = string(giftObject, "getName");
                GiftCatalogItem catalogItem = giftItem(giftObject);
                if (catalogItem != null) {
                    listener.onGiftCatalog(Collections.singletonList(catalogItem));
                }
                String user = userName(event);
                int combo = number(event, "getCombo", 1);
                listener.onEvent(InteractionSlot.TriggerType.GIFT, gift, Math.max(1, combo), user);
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
            Object connection = build.invoke(builder);
            builderOrClient = connection;
            if (connection instanceof CompletableFuture<?> future) {
                future.whenComplete((client, error) -> {
                    if (error != null && !stopped.get()) {
                        listener.onError(errorMessage(error));
                    } else if (client != null && !stopped.get()) {
                        builderOrClient = client;
                    }
                });
            }
        } catch (Throwable error) {
            if (!stopped.get()) {
                listener.onError(errorMessage(error));
            }
        }
    }

    private static String errorMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.trim().isEmpty()
                ? current.getClass().getSimpleName() : message;
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
                            invokeIfPresent(settings, "setFetchGifts", true);
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

    private static Object clientFrom(Object[] args) {
        return args != null && args.length > 0 ? args[0] : null;
    }

    private static List<GiftCatalogItem> extractGiftCatalog(Object client) {
        ArrayList<GiftCatalogItem> result = new ArrayList<>();
        Object manager = invokeIfPresent(client, "getGiftManager");
        if (manager == null) manager = invokeIfPresent(client, "getGiftsManager");
        Object values = invokeIfPresent(manager, "toList");
        if (values instanceof Iterable<?>) {
            for (Object value : (Iterable<?>) values) {
                GiftCatalogItem item = giftItem(value);
                if (item != null) result.add(item);
            }
        }
        return result;
    }

    private static GiftCatalogItem giftItem(Object gift) {
        if (gift == null) return null;
        String name = string(gift, "getName");
        if (name.isEmpty() || "undefined".equalsIgnoreCase(name)) return null;
        int id = number(gift, "getId", -1);
        int cost = number(gift, "getDiamondCost", 0);
        Object picture = invokeIfPresent(gift, "getPicture");
        String imageUrl = firstUrl(picture);
        return new GiftCatalogItem(id, name, cost, imageUrl, "🎁");
    }

    private static String firstUrl(Object picture) {
        if (picture == null) return "";
        String[] methods = {"getLink", "getUrl", "getUrls", "getUrlList"};
        for (String method : methods) {
            Object value = invokeIfPresent(picture, method);
            if (value == null) continue;
            if (value instanceof Iterable<?>) {
                for (Object item : (Iterable<?>) value) {
                    if (item != null && !String.valueOf(item).isEmpty()) return String.valueOf(item);
                }
                continue;
            }
            if (value.getClass().isArray() && Array.getLength(value) > 0) {
                Object item = Array.get(value, 0);
                if (item != null) return String.valueOf(item);
                continue;
            }
            String text = String.valueOf(value);
            if (!text.isEmpty()) return text;
        }
        return "";
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
