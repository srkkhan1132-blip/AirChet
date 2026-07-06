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
import com.airchat.app.model.ChatMessage;
import com.airchat.app.model.NearbyDevice;
import com.airchat.app.model.Protocol;
import com.airchat.app.bluetooth.BluetoothChatManager;
import com.airchat.app.wifi.WifiDirectManager;
public class ChatActivity extends AppCompatActivity {
    public static final String EXTRA_DEVICE_NAME = "device_name";
    public static final String EXTRA_DEVICE_ADDRESS = "device_address";
    public static final String EXTRA_CONNECTION_TYPE = "connection_type";
    private static final int REQUEST_PICK_IMAGE = 200;
    private RecyclerView recyclerMessages;
    private EditText etMessage;
    private ImageButton btnSend, btnBack, btnAttach;
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
        myName = android.os.Build.MODEL != null ? android.os.Build.MODEL : "Me";
        recyclerMessages = findViewById(R.id.recyclerMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnBack = findViewById(R.id.btnBack);
        btnAttach = findViewById(R.id.btnAttach);
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
        messageAdapter.addMessage(new ChatMessage("System","Connected to "+deviceName,ChatMessage.TYPE_SYSTEM));
        btnBack.setOnClickListener(v -> finish());
        btnSend.setOnClickListener(v -> sendTextMessage());
        btnAttach.setOnClickListener(v -> Toast.makeText(this,"Coming soon",Toast.LENGTH_SHORT).show());
    }
    private void sendTextMessage() {
        String text = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;
        String payload = Protocol.encodeText(myName, text);
        boolean sent = connectionType == NearbyDevice.TYPE_BLUETOOTH ? bluetoothManager.sendTo(deviceAddress, payload) : wifiManager.sendTo(deviceAddress, payload);
        if (sent) {
            messageAdapter.addMessage(new ChatMessage(myName, text, ChatMessage.TYPE_SENT));
            etMessage.setText("");
            recyclerMessages.scrollToPosition(messageAdapter.getItemCount()-1);
        } else {
            Toast.makeText(this,"Failed to send",Toast.LENGTH_SHORT).show();
        }
    }
    private void handleIncoming(String fromAddress, Protocol.Decoded message) {
        if (!fromAddress.equals(deviceAddress)) return;
        messageAdapter.addMessage(new ChatMessage(message.sender, message.text, ChatMessage.TYPE_RECEIVED));
        recyclerMessages.scrollToPosition(messageAdapter.getItemCount()-1);
    }
    private final BluetoothChatManager.Listener btListener = new BluetoothChatManager.Listener() {
        @Override public void onDeviceFound(NearbyDevice d) {}
        @Override public void onScanStarted() {}
        @Override public void onScanFinished() {}
        @Override public void onDeviceConnected(NearbyDevice d) {}
        @Override public void onDeviceDisconnected(String address) { if(address.equals(deviceAddress)) tvChatSubtitle.setText("Disconnected"); }
        @Override public void onConnectionFailed(String address, String reason) {}
        @Override public void onMessageReceived(String fromAddress, Protocol.Decoded message) { handleIncoming(fromAddress, message); }
    };
    private final WifiDirectManager.Listener wifiListener = new WifiDirectManager.Listener() {
        @Override public void onDeviceFound(NearbyDevice d) {}
        @Override public void onScanStarted() {}
        @Override public void onScanFinished() {}
        @Override public void onGroupFormed(boolean isOwner, String ownerAddress) {}
        @Override public void onDeviceConnected(NearbyDevice d) {}
        @Override public void onDeviceDisconnected(String address) { if(address.equals(deviceAddress)) tvChatSubtitle.setText("Disconnected"); }
        @Override public void onConnectionFailed(String address, String reason) {}
        @Override public void onMessageReceived(String fromAddress, Protocol.Decoded message) { handleIncoming(fromAddress, message); }
    };
}
