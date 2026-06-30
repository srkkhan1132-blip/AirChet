package com.airchat.app;

import android.app.Application;

import com.airchat.app.bluetooth.BluetoothChatManager;
import com.airchat.app.wifi.WifiDirectManager;

/**
 * Bluetooth sockets and the Wi-Fi Direct group must stay alive while the
 * user navigates between the device-list screen and the chat screen, so
 * the manager instances live here instead of inside a single Activity.
 */
public class AirChatApp extends Application {

    private static AirChatApp instance;

    private BluetoothChatManager bluetoothManager;
    private WifiDirectManager wifiManager;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        bluetoothManager = new BluetoothChatManager(this);
        wifiManager = new WifiDirectManager(this);
    }

    public static AirChatApp getInstance() {
        return instance;
    }

    public BluetoothChatManager getBluetoothManager() {
        return bluetoothManager;
    }

    public WifiDirectManager getWifiManager() {
        return wifiManager;
    }
}
