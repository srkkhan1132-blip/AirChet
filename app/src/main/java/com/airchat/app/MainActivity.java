package com.airchat.app;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.airchat.app.adapter.DeviceAdapter;
import com.airchat.app.auth.SignupActivity;
import com.airchat.app.bluetooth.BluetoothChatManager;
import com.airchat.app.model.NearbyDevice;
import com.airchat.app.model.Protocol;
import com.airchat.app.wifi.WifiDirectManager;
import java.util.ArrayList;
import java.util.List;
public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSIONS = 100;
    private static final int REQUEST_ENABLE_BT = 101;
    public static final int MODE_BLUETOOTH = 0;
    public static final int MODE_WIFI = 1;
    private RecyclerView recyclerDevices;
    private TextView tvEmpty, tvStatus;
    private Button btnScan, btnModeBluetooth, btnModeWifi;
    private DeviceAdapter deviceAdapter;
    private BluetoothChatManager bluetoothManager;
    private WifiDirectManager wifiManager;
    private int currentMode = MODE_BLUETOOTH;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SharedPreferences prefs = getSharedPreferences("airchat", MODE_PRIVATE);
        if (prefs.getString("uid", null) == null) {
            startActivity(new Intent(this, SignupActivity.class));
            finish();
            return;
        }
        recyclerDevices = findViewById(R.id.recyclerDevices);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvStatus = findViewById(R.id.tvStatus);
        btnScan = findViewById(R.id.btnScan);
        btnModeBluetooth = findViewById(R.id.btnModeBluetooth);
        btnModeWifi = findViewById(R.id.btnModeWifi);
        deviceAdapter = new DeviceAdapter(this::onDeviceClicked);
        recyclerDevices.setLayoutManager(new LinearLayoutManager(this));
        recyclerDevices.setAdapter(deviceAdapter);
        bluetoothManager = AirChatApp.getInstance().getBluetoothManager();
        bluetoothManager.setListener(bluetoothListener);
        wifiManager = AirChatApp.getInstance().getWifiManager();
        wifiManager.setListener(wifiListener);
        btnScan.setOnClickListener(v -> onScanClicked());
        btnModeBluetooth.setOnClickListener(v -> switchMode(MODE_BLUETOOTH));
        btnModeWifi.setOnClickListener(v -> switchMode(MODE_WIFI));
        updateEmptyState();
        checkPermissions();
        bluetoothManager.startServer();
    }
    private void switchMode(int mode) {
        currentMode = mode;
        deviceAdapter.clear();
        updateEmptyState();
        if (mode == MODE_BLUETOOTH) {
            btnModeBluetooth.setBackgroundTintList(getColorStateList(R.color.accent_green));
            btnModeBluetooth.setTextColor(getColor(R.color.white));
            btnModeWifi.setBackgroundTintList(getColorStateList(R.color.bg_dark_secondary));
            btnModeWifi.setTextColor(getColor(R.color.text_primary));
            tvStatus.setText("Nearby Devices (Bluetooth)");
        } else {
            btnModeWifi.setBackgroundTintList(getColorStateList(R.color.accent_green));
            btnModeWifi.setTextColor(getColor(R.color.white));
            btnModeBluetooth.setBackgroundTintList(getColorStateList(R.color.bg_dark_secondary));
            btnModeBluetooth.setTextColor(getColor(R.color.text_primary));
            tvStatus.setText("Nearby Devices (Wi-Fi Direct)");
        }
    }
    private void checkPermissions() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed.add(Manifest.permission.BLUETOOTH_SCAN);
            needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            needed.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        }
        needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        needed.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33) {
            needed.add("android.permission.NEARBY_WIFI_DEVICES");
        }
        List<String> toRequest = new ArrayList<>();
        for (String perm : needed) {
            if (ActivityCompat.checkSelfPermission(this, perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                toRequest.add(perm);
            }
        }
        if (!toRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }
    private void onScanClicked() {
        deviceAdapter.clear();
        updateEmptyState();
        if (currentMode == MODE_BLUETOOTH) {
            if (!bluetoothManager.isEnabled()) {
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                try { startActivityForResult(enableIntent, REQUEST_ENABLE_BT); } catch (SecurityException e) {}
                return;
            }
            for (NearbyDevice d : bluetoothManager.getPairedDevices()) deviceAdapter.addOrUpdate(d);
            updateEmptyState();
            bluetoothManager.startDiscovery();
        } else {
            wifiManager.startDiscovery();
        }
    }
    private void onDeviceClicked(NearbyDevice device) {
        if (device.getStatus() == NearbyDevice.STATUS_CONNECTED) {
            openChat(device); return;
        }
        deviceAdapter.updateStatus(device.getAddress(), NearbyDevice.STATUS_CONNECTING);
        if (device.getConnectionType() == NearbyDevice.TYPE_BLUETOOTH) {
            bluetoothManager.connectToDevice(device.getAddress());
        } else {
            wifiManager.connectToDevice(device.getAddress());
        }
    }
    private void openChat(NearbyDevice device) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_DEVICE_NAME, device.getName());
        intent.putExtra(ChatActivity.EXTRA_DEVICE_ADDRESS, device.getAddress());
        intent.putExtra(ChatActivity.EXTRA_CONNECTION_TYPE, device.getConnectionType());
        startActivity(intent);
    }
    private void updateEmptyState() {
        tvEmpty.setVisibility(deviceAdapter.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerDevices.setVisibility(deviceAdapter.isEmpty() ? View.GONE : View.VISIBLE);
    }
    private final BluetoothChatManager.Listener bluetoothListener = new BluetoothChatManager.Listener() {
        @Override public void onDeviceFound(NearbyDevice device) { if (currentMode == MODE_BLUETOOTH) { deviceAdapter.addOrUpdate(device); updateEmptyState(); } }
        @Override public void onScanStarted() { tvStatus.setText("Scanning..."); }
        @Override public void onScanFinished() { tvStatus.setText("Nearby Devices (Bluetooth)"); }
        @Override public void onDeviceConnected(NearbyDevice device) { deviceAdapter.updateStatus(device.getAddress(), NearbyDevice.STATUS_CONNECTED); Toast.makeText(MainActivity.this, "Connected: " + device.getName(), Toast.LENGTH_SHORT).show(); }
        @Override public void onDeviceDisconnected(String address) { deviceAdapter.updateStatus(address, NearbyDevice.STATUS_DISCONNECTED); }
        @Override public void onConnectionFailed(String address, String reason) { deviceAdapter.updateStatus(address, NearbyDevice.STATUS_AVAILABLE); Toast.makeText(MainActivity.this, "Failed: " + reason, Toast.LENGTH_SHORT).show(); }
        @Override public void onMessageReceived(String fromAddress, Protocol.Decoded message) {}
    };
    private final WifiDirectManager.Listener wifiListener = new WifiDirectManager.Listener() {
        @Override public void onDeviceFound(NearbyDevice device) { if (currentMode == MODE_WIFI) { deviceAdapter.addOrUpdate(device); updateEmptyState(); } }
        @Override public void onScanStarted() { tvStatus.setText("Scanning..."); }
        @Override public void onScanFinished() { tvStatus.setText("Nearby Devices (Wi-Fi Direct)"); }
        @Override public void onGroupFormed(boolean isOwner, String ownerAddress) {}
        @Override public void onDeviceConnected(NearbyDevice device) { deviceAdapter.addOrUpdate(device); updateEmptyState(); }
        @Override public void onDeviceDisconnected(String address) { deviceAdapter.updateStatus(address, NearbyDevice.STATUS_DISCONNECTED); }
        @Override public void onConnectionFailed(String address, String reason) { Toast.makeText(MainActivity.this, "Failed: " + reason, Toast.LENGTH_SHORT).show(); }
        @Override public void onMessageReceived(String fromAddress, Protocol.Decoded message) {}
    };
    @Override protected void onDestroy() { super.onDestroy(); bluetoothManager.stopDiscovery(); wifiManager.stopDiscovery(); }
}
