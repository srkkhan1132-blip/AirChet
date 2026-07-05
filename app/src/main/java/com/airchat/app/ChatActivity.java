package com.airchat.app;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.airchat.app.adapter.MessageAdapter;
import com.airchat.app.bluetooth.BluetoothChatManager;
import com.airchat.app.model.ChatMessage;
import com.airchat.app.model.NearbyDevice;
import com.airchat.app.model.Protocol;
import com.airchat.app.wifi.WifiDirectManager;
public class ChatActivity extends AppCompatActivity {
    public static final String EXTRA_DEVICE_NAME = "device_name";
    public static final String EXTRA_DEVICE_ADDRESS = "device_address";
    public static final String EXTRA_CONNECTION_TYPE = "connection_type";
    private RecyclerView recyclerMessages;
    private EditText etMessage;
    private ImageButton btnSend, btnBack;
    private TextView tvChatTitle, tvChatSubtitle;
    private MessageAdapter messageAdapter;
    private BluetoothChatManager bluetoothManager;
    private WifiDirectManager wifiManager;
    private String deviceName, deviceAddress, myName;
    private int connectionType;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        deviceName = getIntent().getStringExtra(EXTRA_DEVICE_NAME);
        deviceAddress = getIntent().getStringExtra(EXTRA_DEVICE_ADDRESS);
        connectionType = getIntent().getIntExtra(EXTRA_CONNECTION_TYPE, NearbyDevice.TYPE_BLUETOOTH);
        myName = android.os.Build.MODEL;
        recyclerMessages = findViewById(R.id.recyclerMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnBack = findViewById(R.id.btnBack);
        tvChatTitle = findViewById(R.id.tvChatTitle);
        tvChatSubtitle = findViewById(R.id.tvChatSubtitle);
        tvChatTitle.setText(deviceName != null ? deviceName : "Device");
        tvChatSubtitle.setText("Connected");
        messageAdapter = new MessageAdapter(false);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        recyclerMessages.setLayoutManager(lm);
        recyclerMessages.setAdapter(messageAdapter);
        bluetoothManager = AirChatApp.getInstance().getBluetoothManager();
        wifiManager = AirChatApp.getInstance().getWifiManager();
        bluetoothManager.setListener(btListener);
        wifiManager.setListener(wifiListener);
        btnBack.setOnClickListener(v -> finish());
        btnSend.setOnClickListener(v -> sendMessage());
        messageAdapter.addMessage(new ChatMessage("System", "Connected to " + deviceName, ChatMessage.TYPE_SYSTEM));
    }
    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;
        String payload = Protocol.encodeText(myName, text);
        boolean sent = connectionType == NearbyDevice.TYPE_BLUETOOTH ? bluetoothManager.sendTo(deviceAddress, payload) : wifiManager.sendTo(deviceAddress, payload);
        if (sent) {
            messageAdapter.addMessage(new ChatMessage(myName, text, ChatMessage.TYPE_SENT));
            etMessage.setText("");
            recyclerMessages.scrollToPosition(messageAdapter.getItemCount() - 1);
        } else {
            Toast.makeText(this, "Send failed", Toast.LENGTH_SHORT).show();
        }
    }
    private void handleIncoming(String from, Protocol.Decoded msg) {
        if (!from.equals(deviceAddress)) return;
        messageAdapter.addMessage(new ChatMessage(msg.sender, msg.text, ChatMessage.TYPE_RECEIVED));
        recyclerMessages.scrollToPosition(messageAdapter.getItemCount() - 1);
    }
    private final BluetoothChatManager.Listener btListener = new BluetoothChatManager.Listener() {
        @Override public void onDeviceFound(NearbyDevice d) {}
        @Override public void onScanStarted() {}
        @Override public void onScanFinished() {}
        @Override public void onDeviceConnected(NearbyDevice d) {}
        @Override public void onDeviceDisconnected(String a) { if (a.equals(deviceAddress)) tvChatSubtitle.setText("Disconnected"); }
        @Override public void onConnectionFailed(String a, String r) {}
        @Override public void onMessageReceived(String from, Protocol.Decoded msg) { handleIncoming(from, msg); }
    };
    private final WifiDirectManager.Listener wifiListener = new WifiDirectManager.Listener() {
        @Override public void onDeviceFound(NearbyDevice d) {}
        @Override public void onScanStarted() {}
        @Override public void onScanFinished() {}
        @Override public void onGroupFormed(boolean o, String a) {}
        @Override public void onDeviceConnected(NearbyDevice d) {}
        @Override public void onDeviceDisconnected(String a) { if (a.equals(deviceAddress)) tvChatSubtitle.setText("Disconnected"); }
        @Override public void onConnectionFailed(String a, String r) {}
        @Override public void onMessageReceived(String from, Protocol.Decoded msg) { handleIncoming(from, msg); }
    };
}
