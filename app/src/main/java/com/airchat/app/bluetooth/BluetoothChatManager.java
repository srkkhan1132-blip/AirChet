package com.airchat.app.bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.ContextCompat;

import com.airchat.app.model.NearbyDevice;
import com.airchat.app.model.Protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles classic Bluetooth (RFCOMM) discovery + connections.
 * Supports multiple simultaneous connections so it can serve as the
 * transport for group chat: every connected device receives every
 * message this device sends (a simple broadcast/relay model).
 */
public class BluetoothChatManager {

    private static final UUID AIRCHAT_UUID =
            UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
    private static final String SERVICE_NAME = "AirChat";

    public interface Listener {
        void onDeviceFound(NearbyDevice device);
        void onScanStarted();
        void onScanFinished();
        void onDeviceConnected(NearbyDevice device);
        void onDeviceDisconnected(String address);
        void onConnectionFailed(String address, String reason);
        void onMessageReceived(String fromAddress, Protocol.Decoded message);
    }

    private final Context context;
    private final BluetoothAdapter adapter;
    private Listener listener;

    private final ConcurrentHashMap<String, BluetoothSocket> activeSockets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> deviceNames = new ConcurrentHashMap<>();
    private BluetoothServerSocket serverSocket;
    private Thread acceptThread;
    private boolean isServerRunning = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public BluetoothChatManager(Context context) {
        this.context = context.getApplicationContext();
        this.adapter = BluetoothAdapter.getDefaultAdapter();
        registerReceivers();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean isSupported() {
        return adapter != null;
    }

    public boolean isEnabled() {
        return adapter != null && adapter.isEnabled();
    }

    public boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    // ---------------- Discovery ----------------

    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device == null) return;
                if (!hasPermissions()) return;
                String name;
                try {
                    name = device.getName();
                } catch (SecurityException e) {
                    name = null;
                }
                if (name == null) name = "Unknown Device";
                NearbyDevice nd = new NearbyDevice(name, device.getAddress(),
                        NearbyDevice.TYPE_BLUETOOTH, NearbyDevice.STATUS_AVAILABLE);
                deviceNames.put(device.getAddress(), name);
                if (listener != null) mainHandler.post(() -> listener.onDeviceFound(nd));
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (listener != null) mainHandler.post(listener::onScanFinished);
            }
        }
    };

    private void registerReceivers() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        context.registerReceiver(discoveryReceiver, filter);
    }

    public void startDiscovery() {
        if (!hasPermissions() || !isEnabled()) return;
        try {
            if (adapter.isDiscovering()) adapter.cancelDiscovery();
            adapter.startDiscovery();
            if (listener != null) listener.onScanStarted();
        } catch (SecurityException e) {
            if (listener != null) listener.onScanFinished();
        }
    }

    public void stopDiscovery() {
        try {
            if (adapter != null && adapter.isDiscovering()) adapter.cancelDiscovery();
        } catch (SecurityException e) { }
    }

    public List<NearbyDevice> getPairedDevices() {
        List<NearbyDevice> result = new ArrayList<>();
        if (!hasPermissions() || adapter == null) return result;
        try {
            for (BluetoothDevice device : adapter.getBondedDevices()) {
                result.add(new NearbyDevice(device.getName(), device.getAddress(),
                        NearbyDevice.TYPE_BLUETOOTH, NearbyDevice.STATUS_AVAILABLE));
            }
        } catch (SecurityException e) { }
        return result;
    }

    // ---------------- Server (accept incoming connections) ----------------

    public void startServer() {
        if (isServerRunning || !hasPermissions()) return;
        isServerRunning = true;
        acceptThread = new Thread(() -> {
            try {
                serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, AIRCHAT_UUID);
                while (isServerRunning) {
                    BluetoothSocket socket;
                    try {
                        socket = serverSocket.accept();
                    } catch (IOException e) {
                        break;
                    }
                    if (socket != null) {
                        handleNewConnection(socket);
                    }
                }
            } catch (SecurityException | IOException e) {
                isServerRunning = false;
            }
        });
        acceptThread.start();
    }

    public void stopServer() {
        isServerRunning = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) { }
    }

    // ---------------- Client (connect out to a device) ----------------

    public void connectToDevice(String address) {
        if (!hasPermissions()) return;
        BluetoothDevice device = adapter.getRemoteDevice(address);

        new Thread(() -> {
            try {
                stopDiscovery();
                BluetoothSocket socket = device.createRfcommSocketToServiceRecord(AIRCHAT_UUID);
                socket.connect();
                handleNewConnection(socket);
            } catch (IOException | SecurityException e) {
                if (listener != null) {
                    String reason = e.getMessage() != null ? e.getMessage() : "Connection failed";
                    mainHandler.post(() -> listener.onConnectionFailed(address, reason));
                }
            }
        }).start();
    }

    private void handleNewConnection(BluetoothSocket socket) {
        String address = socket.getRemoteDevice().getAddress();
        activeSockets.put(address, socket);

        String name = deviceNames.containsKey(address) ? deviceNames.get(address) : "Device";
        NearbyDevice nd = new NearbyDevice(name, address,
                NearbyDevice.TYPE_BLUETOOTH, NearbyDevice.STATUS_CONNECTED);

        if (listener != null) mainHandler.post(() -> listener.onDeviceConnected(nd));

        new Thread(() -> listenForMessages(address, socket)).start();
    }

    private void listenForMessages(String address, BluetoothSocket socket) {
        InputStream input;
        try {
            input = socket.getInputStream();
        } catch (IOException e) {
            disconnectInternal(address);
            return;
        }

        StringBuilder buffer = new StringBuilder();
        byte[] chunk = new byte[4096];
        try {
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
                        if (listener != null) {
                            mainHandler.post(() -> listener.onMessageReceived(address, decoded));
                        }
                    }
                }
            }
        } catch (IOException e) {
            // socket closed
        }
        disconnectInternal(address);
    }

    private void disconnectInternal(String address) {
        BluetoothSocket socket = activeSockets.remove(address);
        if (socket != null) {
            try { socket.close(); } catch (IOException e) { }
        }
        if (listener != null) mainHandler.post(() -> listener.onDeviceDisconnected(address));
    }

    public void disconnect(String address) {
        disconnectInternal(address);
    }

    // ---------------- Sending ----------------

    /** Sends to one specific connected device. */
    public boolean sendTo(String address, String payload) {
        BluetoothSocket socket = activeSockets.get(address);
        if (socket == null) return false;
        try {
            OutputStream out = socket.getOutputStream();
            out.write((payload + "\n").getBytes());
            out.flush();
            return true;
        } catch (IOException e) {
            disconnectInternal(address);
            return false;
        }
    }

    /** Sends to every connected device — this is how group chat broadcast works. */
    public void broadcast(String payload) {
        for (String address : new ArrayList<>(activeSockets.keySet())) {
            sendTo(address, payload);
        }
    }

    public List<String> getConnectedAddresses() {
        return new ArrayList<>(activeSockets.keySet());
    }

    public boolean isConnectedToAny() {
        return !activeSockets.isEmpty();
    }

    public void cleanup() {
        stopDiscovery();
        stopServer();
        for (String addr : new ArrayList<>(activeSockets.keySet())) {
            disconnectInternal(addr);
        }
        try {
            context.unregisterReceiver(discoveryReceiver);
        } catch (IllegalArgumentException e) { }
    }
}
