package dev.glasshome.launcher;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

/** Reports the current network name for the Wi-Fi pill. Event-driven; no polling. */
final class NetworkStatus {
    interface Listener {
        void onNetworkLabel(String label);
    }

    private final Context context;
    private final Listener listener;
    private final ConnectivityManager connectivity;
    private final WifiManager wifi;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean registered;

    private final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
            listener.onNetworkLabel(labelFor(capabilities));
        }

        @Override
        public void onLost(Network network) {
            listener.onNetworkLabel(context.getString(R.string.offline));
        }
    };

    NetworkStatus(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.connectivity = context.getSystemService(ConnectivityManager.class);
        this.wifi = context.getApplicationContext().getSystemService(WifiManager.class);
    }

    void start() {
        if (connectivity != null && !registered) {
            try {
                connectivity.registerDefaultNetworkCallback(callback, main);
                registered = true;
            } catch (RuntimeException ignored) {
                // Fall back to one-off refreshes in onResume.
            }
        }
        refresh();
    }

    void refresh() {
        Network network = connectivity == null ? null : connectivity.getActiveNetwork();
        NetworkCapabilities caps = network == null ? null : connectivity.getNetworkCapabilities(network);
        listener.onNetworkLabel(labelFor(caps));
    }

    void stop() {
        if (registered) {
            try {
                connectivity.unregisterNetworkCallback(callback);
            } catch (RuntimeException ignored) {
                // Already unregistered.
            }
            registered = false;
        }
    }

    private String labelFor(NetworkCapabilities caps) {
        if (caps == null) {
            return context.getString(R.string.offline);
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return context.getString(R.string.ethernet);
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return ssid();
        }
        return context.getString(R.string.wifi);
    }

    @SuppressWarnings("deprecation")
    private String ssid() {
        WifiInfo info = wifi == null ? null : wifi.getConnectionInfo();
        String ssid = info == null ? null : info.getSSID();
        if (ssid == null || ssid.isEmpty() || "<unknown ssid>".equals(ssid)) {
            return context.getString(R.string.wifi);
        }
        if (ssid.length() > 1 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length() - 1);
        }
        return ssid;
    }
}
