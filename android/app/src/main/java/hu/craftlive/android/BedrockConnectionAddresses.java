package hu.craftlive.android;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class BedrockConnectionAddresses {
    private BedrockConnectionAddresses() {
    }

    static List<String> addresses() {
        Set<String> result = new LinkedHashSet<>();
        // Bedrock and CraftLive run on the same Android device. Prefer the stable
        // loopback address instead of a Wi-Fi/mobile address that may change.
        result.add("127.0.0.1");
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                for (NetworkInterface network : Collections.list(interfaces)) {
                    if (!network.isUp()) continue;
                    for (InetAddress address : Collections.list(network.getInetAddresses())) {
                        if (address instanceof Inet4Address && !address.isLoopbackAddress()
                                && !address.isLinkLocalAddress()) {
                            result.add(address.getHostAddress());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return new ArrayList<>(result);
    }

    static String preferredAddress() {
        return addresses().get(0);
    }

    static String command(String address) {
        return "/wsserver ws://" + address + ":" + BedrockWebSocketServer.PORT;
    }

    static boolean isLocalAddress(InetAddress address) {
        if (address == null || address.isLoopbackAddress() || address.isAnyLocalAddress()) return true;
        try {
            return NetworkInterface.getByInetAddress(address) != null;
        } catch (Exception ignored) {
            return false;
        }
    }
}
