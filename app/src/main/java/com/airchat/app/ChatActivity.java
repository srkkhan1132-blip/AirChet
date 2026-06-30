package com.airchat.app;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_DEVICE_NAME = "device_name";
    public static final String EXTRA_DEVICE_ADDRESS = "device_address";
    public static final String EXTRA_CONNECTION_TYPE = "connection_type";

    private static final int REQUEST_PICK_IMAGE = 200;

    private RecyclerView recyclerMessages;
    private EditText etMessage;
    private ImageButton btnSend, btnAttach, btnBack;
    private TextView tvChatTitle, tvChatSubtitle;

    private MessageAdapter messageAdapter;
    private BluetoothChatManager bluetoothManager;
    private WifiDirectManager wifiManager;

    private String deviceName;
    private String deviceAddress;
    private int connectionType;
    private String myName;

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
        btnAttach = findViewById(R.id.btnAttach);
        btnBack = findViewById(R.id.btnBack);
        tvChatTitle = findViewById(R.id.tvChatTitle);
        tvChatSubtitle = findViewById(R.id.tvChatSubtitle);

        tvChatTitle.setText(deviceName != null ? deviceName : "Device");
        tvChatSubtitle.setText(getString(R.string.connected));

        messageAdapter = new MessageAdapter(false);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        recyclerMessages.setLayoutManager(lm);
        recyclerMessages.setAdapter(messageAdapter);

        bluetoothManager = AirChatApp.getInstance().getBluetoothManager();
        wifiManager = AirChatApp.getInstance().getWifiManager();

        bluetoothManager.setListener(bluetoothListener);
        wifiManager.setListener(wifiListener);

        messageAdapter.addMessage(new ChatMessage("System",
                "Connected to " + deviceName, ChatMessage.TYPE_SYSTEM));

        btnBack.setOnClickListener(v -> finish());
        btnSend.setOnClickListener(v -> sendTextMessage());
        btnAttach.setOnClickListener(v -> pickImage());
    }

    // ---------------- Sending ----------------

    private void sendTextMessage() {
        String text = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        String payload = Protocol.encodeText(myName, text);
        boolean sent = sendOverActiveTransport(payload);

        if (sent) {
            messageAdapter.addMessage(new ChatMessage(myName, text, ChatMessage.TYPE_SENT));
            etMessage.setText("");
            scrollToBottom();
        } else {
            Toast.makeText(this, "Failed to send — connection lost", Toast.LENGTH_SHORT).show();
        }
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) sendImage(uri);
        }
    }

    private void sendImage(Uri uri) {
        try {
            InputStream input = getContentResolver().openInputStream(uri);
            Bitmap original = BitmapFactory.decodeStream(input);
            if (original == null) {
                Toast.makeText(this, "Could not read image", Toast.LENGTH_SHORT).show();
                return;
            }

            // Downscale aggressively — Bluetooth RFCOMM is slow, so a huge
            // photo would make the chat feel frozen for many seconds.
            Bitmap scaled = scaleDownBitmap(original, 720);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 60, baos);
            byte[] bytes = baos.toByteArray();

            String payload = Protocol.encodeImage(myName, bytes);
            boolean sent = sendOverActiveTransport(payload);

            if (sent) {
                String localPath = saveImageLocally(bytes, "sent_" + System.currentTimeMillis());
                messageAdapter.addMessage(new ChatMessage(myName, null, localPath,
                        ChatMessage.TYPE_SENT, ChatMessage.CONTENT_IMAGE));
                scrollToBottom();
            } else {
                Toast.makeText(this, "Failed to send image — connection lost", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error reading image", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap scaleDownBitmap(Bitmap original, int maxDimension) {
        int width = original.getWidth();
        int height = original.getHeight();
        if (width <= maxDimension && height <= maxDimension) return original;

        float ratio = Math.min((float) maxDimension / width, (float) maxDimension / height);
        int newWidth = Math.round(width * ratio);
        int newHeight = Math.round(height * ratio);
        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
    }

    private String saveImageLocally(byte[] bytes, String name) {
        try {
            File dir = new File(getFilesDir(), "airchat_images");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, name + ".jpg");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(bytes);
            fos.close();
            return file.getAbsolutePath();
        } catch (IOException e) {
            return null;
        }
    }

    private boolean sendOverActiveTransport(String payload) {
        if (connectionType == NearbyDevice.TYPE_BLUETOOTH) {
            return bluetoothManager.sendTo(deviceAddress, payload);
        } else {
            return wifiManager.sendTo(deviceAddress, payload);
        }
    }

    private void scrollToBottom() {
        recyclerMessages.post(() ->
                recyclerMessages.scrollToPosition(messageAdapter.getItemCount() - 1));
    }

    // ---------------- Receiving ----------------

    private void handleIncoming(String fromAddress, Protocol.Decoded message) {
        if (!fromAddress.equals(deviceAddress)) return; // not from this chat's peer

        if ("image".equals(message.type) && message.imageBytes != null) {
            String localPath = saveImageLocally(message.imageBytes, "recv_" + System.currentTimeMillis());
            messageAdapter.addMessage(new ChatMessage(message.sender, null, localPath,
                    ChatMessage.TYPE_RECEIVED, ChatMessage.CONTENT_IMAGE));
        } else {
            messageAdapter.addMessage(new ChatMessage(message.sender, message.text, ChatMessage.TYPE_RECEIVED));
        }
        scrollToBottom();
    }

    // ---------------- Listeners ----------------

    private final BluetoothChatManager.Listener bluetoothListener = new BluetoothChatManager.Listener() {
        @Override public void onDeviceFound(NearbyDevice device) { }
        @Override public void onScanStarted() { }
        @Override public void onScanFinished() { }
        @Override public void onDeviceConnected(NearbyDevice device) { }

        @Override
        public void onDeviceDisconnected(String address) {
            if (address.equals(deviceAddress)) {
                tvChatSubtitle.setText(getString(R.string.disconnected));
                messageAdapter.addMessage(new ChatMessage("System",
                        deviceName + " disconnected", ChatMessage.TYPE_SYSTEM));
                scrollToBottom();
            }
        }

        @Override public void onConnectionFailed(String address, String reason) { }

        @Override
        public void onMessageReceived(String fromAddress, Protocol.Decoded message) {
            handleIncoming(fromAddress, message);
        }
    };

    private final WifiDirectManager.Listener wifiListener = new WifiDirectManager.Listener() {
        @Override public void onDeviceFound(NearbyDevice device) { }
        @Override public void onScanStarted() { }
        @Override public void onScanFinished() { }
        @Override public void onGroupFormed(boolean isGroupOwner, String ownerAddress) { }
        @Override public void onDeviceConnected(NearbyDevice device) { }

        @Override
        public void onDeviceDisconnected(String address) {
            if (address.equals(deviceAddress)) {
                tvChatSubtitle.setText(getString(R.string.disconnected));
                messageAdapter.addMessage(new ChatMessage("System",
                        deviceName + " disconnected", ChatMessage.TYPE_SYSTEM));
                scrollToBottom();
            }
        }

        @Override public void onConnectionFailed(String address, String reason) { }

        @Override
        public void onMessageReceived(String fromAddress, Protocol.Decoded message) {
            handleIncoming(fromAddress, message);
        }
    };
}
