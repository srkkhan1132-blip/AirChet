package com.airchat.app.wifi;
import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
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
import java.util.concurrent.ConcurrentHashMap;
public class WifiDirectManager {
    private static final int PORT = 8988;
    private static final int SOCKET_TIMEOUT = 5000;
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
    private boolean receiverRegistered = false;
    public WifiDirectManager(Context context) {
        this.context = context.getApplicationContext();
        this.manager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        this.channel = manager != null ? manager.initialize(context, Looper.getMainLooper(), null) : null;
        registerReceiver();
    }
    public void setListener(Listener listener) { this.listener = listener; }
    public boolean isSupported() { return manager != null && channel != null; }
    public boolean hasPermissions() {
        boolean fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (Build.VERSION.SDK_INT >= 33) {
            boolean nearby = ContextCompat.checkSelfPermission(context, "android.permission.NEARBY_WIFI_DEVICES") == PackageManager.PERMISSION_GRANTED;
            return fine && nearby;
        }
        return fine;
    }
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            String action = intent.getAction();
            if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                if (!hasPermissions()) return;
                try {
                    manager.requestPeers(channel, peers -> {
                        for (WifiP2pDevice device : peers.getDeviceList()) {
                            NearbyDevice nd = new NearbyDevice(
                                device.deviceName != null ? device.deviceName : "Unknown",
                                device.deviceAddress, NearbyDevice.TYPE_WIFI_DIRECT, NearbyDevice.STATUS_AVAILABLE);
                            if (listener != null) mainHandler.post(() -> listener.onDeviceFound(nd));
                        }
                    });
                } catch (SecurityException e) {}
            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                WifiP2pInfo info = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
                if (info != null && info.groupFormed) {
                    isGroupOwner = info.isGroupOwner;
                    String ownerIp = info.groupOwnerAddress != null ? info.groupOwnerAddress.getHostAddress() : null;
                    if (listener != null) mainHandler.post(() -> listener.onGroupFormed(isGroupOwner, ownerIp));
                    if (isGroupOwner) startServer();
                    else if (ownerIp != null) connectToOwner(ownerIp);
                }
            }
        }
    };
    private void registerReceiver() {
        if (receiverRegistered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        context.registerReceiver(receiver, filter);
        receiverRegistered = true;
    }
    public void startDiscovery() {
        if (!isSupported() || !hasPermissions()) return;
        try {
            manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override public void onSuccess() { if (listener != null) mainHandler.post(listener::onScanStarted); }
                @Override public void onFailure(int r) { if (listener != null) mainHandler.post(listener::onScanFinished); }
            });
        } catch (SecurityException e) {}
    }
    public void stopDiscovery() {
        if (!isSupported()) return;
        try { manager.stopPeerDiscovery(channel, null); } catch (SecurityException e) {}
    }
    public void connectToDevice(String deviceAddress) {
        if (!hasPermissions()) return;
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = deviceAddress;
        config.groupOwnerIntent = 0;
        try {
            manager.connect(channel, config, new WifiP2pManager.ActionListener() {
                @Override public void onSuccess() {}
                @Override public void onFailure(int r) {
                    if (listener != null) mainHandler.post(() -> listener.onConnectionFailed(deviceAddress, "Code: " + r));
                }
            });
        } catch (SecurityException e) {
            if (listener != null) listener.onConnectionFailed(deviceAddress, "Permission denied");
        }
    }
    private void startServer() {
        new Thread(() -> {
            try {
                if (serverSocket != null) try { serverSocket.close(); } catch (IOException e) {}
                serverSocket = new ServerSocket(PORT);
                serverSocket.setSoTimeout(0);
                while (!serverSocket.isClosed()) {
                    try {
                        Socket client = serverSocket.accept();
                        client.setKeepAlive(true);
                        String addr = client.getInetAddress().getHostAddress();
                        activeSockets.put(addr, client);
                        NearbyDevice nd = new NearbyDevice("Peer", addr, NearbyDevice.TYPE_WIFI_DIRECT, NearbyDevice.STATUS_CONNECTED);
                        if (listener != null) mainHandler.post(() -> listener.onDeviceConnected(nd));
                        new Thread(() -> listenOnSocket(addr, client)).start();
                    } catch (IOException e) { break; }
                }
            } catch (IOException e) {}
        }).start();
    }
    private void connectToOwner(String ownerIp) {
        new Thread(() -> {
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    Thread.sleep(1000 * attempt);
                    Socket socket = new Socket();
                    socket.setKeepAlive(true);
                    socket.connect(new InetSocketAddress(ownerIp, PORT), SOCKET_TIMEOUT);
                    activeSockets.put(ownerIp, socket);
                    NearbyDevice nd = new NearbyDevice("Group Owner", ownerIp, NearbyDevice.TYPE_WIFI_DIRECT, NearbyDevice.STATUS_CONNECTED);
                    if (listener != null) mainHandler.post(() -> listener.onDeviceConnected(nd));
                    listenOnSocket(ownerIp, socket);
                    return;
                } catch (Exception e) {
                    if (attempt == 2 && listener != null) mainHandler.post(() -> listener.onConnectionFailed(ownerIp, "Cannot reach group owner"));
                }
            }
        }).start();
    }
    private void listenOnSocket(String address, Socket socket) {
        StringBuilder buffer = new StringBuilder();
        byte[] chunk = new byte[4096];
        try {
            InputStream input = socket.getInputStream();
            int bytesRead;
            while ((bytesRead = input.read(chunk)) != -1) {
                buffer.append(new String(chunk, 0, bytesRead));
                int idx;
                while ((idx = buffer.indexOf("\n")) != -1) {
                    String line = buffer.substring(0, idx).trim();
                    buffer.delete(0, idx + 1);
                    if (!line.isEmpty()) {
                        Protocol.Decoded decoded = Protocol.decode(line);
                        if (listener != null) mainHandler.post(() -> listener.onMessageReceived(address, decoded));
                        if (isGroupOwner) relayToOthers(address, line);
                    }
                }
            }
        } catch (IOException e) {}
        activeSockets.remove(address);
        try { socket.close(); } catch (IOException e) {}
        if (listener != null) mainHandler.post(() -> listener.onDeviceDisconnected(address));
    }
    private void relayToOthers(String exceptAddress, String payload) {
        for (String addr : new ArrayList<>(activeSockets.keySet())) {
            if (!addr.equals(exceptAddress)) sendTo(addr, payload);
        }
    }
    public boolean sendTo(String address, String payload) {
        Socket socket = activeSockets.get(address);
        if (socket == null || socket.isClosed()) return false;
        try {
            OutputStream out = socket.getOutputStream();
            out.write((payload + "\n").getBytes("UTF-8"));
            out.flush();
            return true;
        } catch (IOException e) {
            activeSockets.remove(address);
            return false;
        }
    }
    public void broadcast(String payload) {
        for (String addr : new ArrayList<>(activeSockets.keySet())) sendTo(addr, payload);
    }
    public boolean isConnectedToAny() { return !activeSockets.isEmpty(); }
    public java.util.List<String> getConnectedAddresses() { return new java.util.ArrayList<>(activeSockets.keySet()); }
    public void disconnectGroup() {
        if (!isSupported()) return;
        try { manager.removeGroup(channel, null); } catch (SecurityException e) {}
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException e) {}
        for (Socket s : activeSockets.values()) try { s.close(); } catch (IOException e) {}
        activeSockets.clear();
    }
    public void cleanup() {
        stopDiscovery();
        disconnectGroup();
        if (receiverRegistered) {
            try { context.unregisterReceiver(receiver); } catch (IllegalArgumentException e) {}
            receiverRegistered = false;
        }
    }
}
