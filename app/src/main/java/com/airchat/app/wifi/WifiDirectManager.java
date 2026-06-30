package com.airchat.app.wifi;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.ContextCompat;

import com.airchat.app.model.NearbyDevice;
import com.airchat.app.model.Protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles Wi-Fi Direct (P2P) discovery + group formation, plus a TCP
 * socket layer on top for actually exchanging chat data once a group
 * is formed. The group owner acts as a small TCP server that every
 * other member connects to — this naturally supports group chat,
 * since the owner can relay messages to all connected clients.
 */
public class WifiDirectManager {

    private static final int PORT = 8988;

    public interface Listener {
        void onDeviceFound(NearbyDevice device);
        void onScanStarted();
        void onScanFinished();
        void onGroupFormed(boolean isGroupOwner, String ownerAddress);
        void onDeviceConnected(NearbyDevice device);
        void onDeviceDisconnected(String address);
        void onConnectionFailed(String address, String reason);
        void onMessageReceived(String fromAddress, Protocol.Decoded message);
    }

    private final Context context;
    private final WifiP2pManager manager;
    private final WifiP2pManager.Channel channel;
    private Listener listener;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean isGroupOwner = false;
    private ServerSocket serverSocket;
    private final ConcurrentHashMap<String, Socket> activeSockets = new ConcurrentHashMap<>();
    private Socket ownerSocket; // used when this device is a client, not the owner

    public WifiDirectManager(Context context) {
        this.context = context.getApplicationContext();
        this.manager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        this.channel = manager != null ? manager.initialize(context, Looper.getMainLooper(), null) : null;
        registerReceiver();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean isSupported() {
        return manager != null && channel != null;
    }

    public boolean hasPermissions() {
        boolean fineLocation = ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (Build.VERSION.SDK_INT >= 33) {
            boolean nearbyWifi = ContextCompat.checkSelfPermission(context,
                    "android.permission.NEARBY_WIFI_DEVICES") == PackageManager.PERMISSION_GRANTED;
            return fineLocation && nearbyWifi;
        }
        return fineLocation;
    }

    // ---------------- Discovery ----------------

    private final WifiP2pManager.PeerListListener peerListListener = peerList -> {
        WifiP2pDeviceList refreshedPeers = peerList;
        for (WifiP2pDevice device : refreshedPeers.getDeviceList()) {
            NearbyDevice nd = new NearbyDevice(
                    device.deviceName != null ? device.deviceName : "Unknown Device",
                    device.deviceAddress,
                    NearbyDevice.TYPE_WIFI_DIRECT,
                    NearbyDevice.STATUS_AVAILABLE);
            if (listener != null) listener.onDeviceFound(nd);
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String action = intent.getAction();
            if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                if (hasPermissions()) {
                    try {
                        manager.requestPeers(channel, peerListListener);
                    } catch (SecurityException e) { }
                }
            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                WifiP2pInfo info = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
                if (info != null && info.groupFormed) {
                    isGroupOwner = info.isGroupOwner;
                    String ownerAddress = info.groupOwnerAddress != null
                            ? info.groupOwnerAddress.getHostAddress() : null;
                    if (listener != null) {
                        mainHandler.post(() -> listener.onGroupFormed(isGroupOwner, ownerAddress));
                    }
                    if (isGroupOwner) {
                        startServerSocket();
                    } else if (ownerAddress != null) {
                        connectSocketToOwner(ownerAddress);
                    }
                }
            }
        }
    };

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        context.registerReceiver(receiver, filter);
    }

    public void startDiscovery() {
        if (!isSupported() || !hasPermissions()) return;
        try {
            manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    if (listener != null) mainHandler.post(listener::onScanStarted);
                }
                @Override
                public void onFailure(int reason) {
                    if (listener != null) mainHandler.post(listener::onScanFinished);
                }
            });
        } catch (SecurityException e) { }
    }

    public void stopDiscovery() {
        if (!isSupported()) return;
        try {
            manager.stopPeerDiscovery(channel, null);
        } catch (SecurityException e) { }
    }

    // ---------------- Connection ----------------

    public void connectToDevice(String deviceAddress) {
        if (!hasPermissions()) return;
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = deviceAddress;

        try {
            manager.connect(channel, config, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    // Connection result + socket setup handled by broadcast receiver above
                }
                @Override
                public void onFailure(int reason) {
                    if (listener != null) {
                        mainHandler.post(() -> listener.onConnectionFailed(deviceAddress, "Code: " + reason));
                    }
                }
            });
        } catch (SecurityException e) {
            if (listener != null) listener.onConnectionFailed(deviceAddress, "Permission denied");
        }
    }

    // ---------------- TCP transport over the P2P group ----------------

    private void startServerSocket() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                while (true) {
                    Socket client = serverSocket.accept();
                    String addr = client.getInetAddress().getHostAddress();
                    activeSockets.put(addr, client);
                    NearbyDevice nd = new NearbyDevice("Peer", addr, NearbyDevice.TYPE_WIFI_DIRECT,
                            NearbyDevice.STATUS_CONNECTED);
                    if (listener != null) mainHandler.post(() -> listener.onDeviceConnected(nd));
                    new Thread(() -> listenOnSocket(addr, client)).start();
                }
            } catch (IOException e) {
                // server socket closed / failed
            }
        }).start();
    }

    private void connectSocketToOwner(String ownerAddress) {
        new Thread(() -> {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(ownerAddress, PORT), 8000);
                ownerSocket = socket;
                activeSockets.put(ownerAddress, socket);
                NearbyDevice nd = new NearbyDevice("Group Owner", ownerAddress,
                        NearbyDevice.TYPE_WIFI_DIRECT, NearbyDevice.STATUS_CONNECTED);
                if (listener != null) mainHandler.post(() -> listener.onDeviceConnected(nd));
                listenOnSocket(ownerAddress, socket);
            } catch (IOException e) {
                if (listener != null) {
                    mainHandler.post(() -> listener.onConnectionFailed(ownerAddress, "Could not reach group owner"));
                }
            }
        }).start();
    }

    private void listenOnSocket(String address, Socket socket) {
        StringBuilder buffer = new StringBuilder();
        byte[] chunk = new byte[4096];
        try {
            InputStream input = socket.getInputStream();
            while (true) {
                int bytesRead = input.read(chunk);
                if (bytesRead == -1) break;
                buffer.append(new String(chunk, 0, bytesRead));
                int newlineIdx;
                while ((newlineIdx = buffer.indexOf("\n")) != -1) {
                    String line = buffer.substring(0, newlineIdx);
                    buffer.delete(0, newlineIdx + 1);
                    if (!line.trim().isEmpty()) {
                        Protocol.Decoded decoded = Protocol.decode(line);
                        if (listener != null) mainHandler.post(() -> listener.onMessageReceived(address, decoded));

                        // Group owner relays to every other connected client (group chat fan-out)
                        if (isGroupOwner) {
                            relayToOthers(address, line);
                        }
                    }
                }
            }
        } catch (IOException e) {
            // closed
        }
        activeSockets.remove(address);
        if (listener != null) mainHandler.post(() -> listener.onDeviceDisconnected(address));
    }

    private void relayToOthers(String exceptAddress, String payload) {
        for (String addr : new ArrayList<>(activeSockets.keySet())) {
            if (!addr.equals(exceptAddress)) {
                sendTo(addr, payload);
            }
        }
    }

    // ---------------- Sending ----------------

    public boolean sendTo(String address, String payload) {
        Socket socket = activeSockets.get(address);
        if (socket == null) return false;
        try {
            OutputStream out = socket.getOutputStream();
            out.write((payload + "\n").getBytes());
            out.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Sends to every connected socket. If this device is the owner, that
     *  relays to the whole group automatically via listenOnSocket(). */
    public void broadcast(String payload) {
        for (String address : new ArrayList<>(activeSockets.keySet())) {
            sendTo(address, payload);
        }
    }

    public boolean isConnectedToAny() {
        return !activeSockets.isEmpty();
    }

    public void disconnectGroup() {
        if (!isSupported()) return;
        manager.removeGroup(channel, null);
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) { }
        for (Socket s : activeSockets.values()) {
            try { s.close(); } catch (IOException e) { }
        }
        activeSockets.clear();
    }

    public void cleanup() {
        stopDiscovery();
        disconnectGroup();
        try {
            context.unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) { }
    }
}
