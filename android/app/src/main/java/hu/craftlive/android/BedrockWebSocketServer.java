package hu.craftlive.android;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class BedrockWebSocketServer extends WebSocketServer {
    static final String BIND_HOST = "0.0.0.0";
    static final int PORT = 19134;

    interface Listener {
        void onBedrockListening();

        void onBedrockConnected();

        void onBedrockDisconnected();

        void onBedrockCommandResponse(String requestId, boolean successful, String message);

        void onBedrockError(String message);
    }

    private final Set<WebSocket> clients = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Listener listener;

    BedrockWebSocketServer(Listener listener) {
        // Androidon a Minecraft folyamatából érkező loopback kapcsolat egyes
        // készülékeken nem ugyanabba a hálózati névtérbe kerül. A wildcard bind
        // ezért a telefon helyi IPv4-címén is elérhetővé teszi a hidat.
        super(new InetSocketAddress(BIND_HOST, PORT));
        this.listener = listener;
        setConnectionLostTimeout(20);
        setReuseAddr(true);
    }

    void startSafely() {
        if (!started.compareAndSet(false, true)) return;
        try {
            start();
        } catch (Throwable error) {
            started.set(false);
            listener.onBedrockError(readable(error));
        }
    }

    boolean isMinecraftConnected() {
        clients.removeIf(client -> client == null || !client.isOpen());
        return !clients.isEmpty();
    }

    boolean sendCommand(String command) {
        if (!isMinecraftConnected()) return false;
        try {
            BedrockWebSocketProtocol.CommandRequest request =
                    BedrockWebSocketProtocol.commandRequest(command);
            boolean delivered = false;
            for (WebSocket client : clients) {
                if (client != null && client.isOpen()) {
                    client.send(request.json);
                    delivered = true;
                }
            }
            return delivered;
        } catch (Throwable error) {
            listener.onBedrockError(readable(error));
            return false;
        }
    }

    void stopSafely() {
        clients.clear();
        if (!started.getAndSet(false)) return;
        try {
            stop(1_000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onOpen(WebSocket connection, ClientHandshake handshake) {
        if (connection == null || connection.getRemoteSocketAddress() == null
                || !BedrockConnectionAddresses.isLocalAddress(
                connection.getRemoteSocketAddress().getAddress())) {
            if (connection != null) connection.close(1008, "CraftLive accepts local Minecraft only");
            return;
        }
        clients.add(connection);
        listener.onBedrockConnected();
    }

    @Override
    public void onClose(WebSocket connection, int code, String reason, boolean remote) {
        clients.remove(connection);
        if (clients.isEmpty()) listener.onBedrockDisconnected();
    }

    @Override
    public void onMessage(WebSocket connection, String message) {
        BedrockWebSocketProtocol.CommandResponse response =
                BedrockWebSocketProtocol.parseResponse(message);
        if (response != null) {
            listener.onBedrockCommandResponse(response.requestId,
                    response.successful(), response.statusMessage);
        }
    }

    @Override
    public void onError(WebSocket connection, Exception error) {
        if (connection != null) {
            clients.remove(connection);
            if (clients.isEmpty()) listener.onBedrockDisconnected();
        } else {
            listener.onBedrockError(readable(error));
        }
    }

    @Override
    public void onStart() {
        listener.onBedrockListening();
    }

    private static String readable(Throwable error) {
        if (error == null) return "Unknown WebSocket error";
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.trim().isEmpty()
                ? current.getClass().getSimpleName() : message;
    }
}
