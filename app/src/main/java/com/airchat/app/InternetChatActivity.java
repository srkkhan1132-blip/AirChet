package com.airchat.app;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.airchat.app.adapter.MessageAdapter;
import com.airchat.app.internet.InternetChatManager;
import com.airchat.app.model.ChatMessage;
import com.airchat.app.model.Protocol;
public class InternetChatActivity extends AppCompatActivity {
    private LinearLayout panelJoin, panelChat;
    private EditText etYourName, etRoomName, etMessage;
    private Button btnJoinRoom;
    private ImageButton btnSend, btnBack;
    private RecyclerView recyclerMessages;
    private TextView tvRoomInfo;
    private MessageAdapter messageAdapter;
    private InternetChatManager internetManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_internet_chat);
        panelJoin = findViewById(R.id.panelJoin);
        panelChat = findViewById(R.id.panelChat);
        etYourName = findViewById(R.id.etYourName);
        etRoomName = findViewById(R.id.etRoomName);
        etMessage = findViewById(R.id.etMessage);
        btnJoinRoom = findViewById(R.id.btnJoinRoom);
        btnSend = findViewById(R.id.btnSend);
        btnBack = findViewById(R.id.btnBack);
        recyclerMessages = findViewById(R.id.recyclerMessages);
        tvRoomInfo = findViewById(R.id.tvRoomInfo);
        messageAdapter = new MessageAdapter(true);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        recyclerMessages.setLayoutManager(lm);
        recyclerMessages.setAdapter(messageAdapter);
        internetManager = new InternetChatManager();
        internetManager.setListener(new InternetChatManager.Listener() {
            @Override
            public void onMessageReceived(Protocol.Decoded message) {
                messageAdapter.addMessage(new ChatMessage(message.sender, message.text, ChatMessage.TYPE_RECEIVED));
                scrollToBottom();
            }
            @Override
            public void onConnected(String roomName) {
                panelJoin.setVisibility(View.GONE);
                panelChat.setVisibility(View.VISIBLE);
                tvRoomInfo.setText("Room: " + roomName + "  •  " + internetManager.getMyName());
                messageAdapter.addMessage(new ChatMessage("System", "Room \"" + roomName + "\" mein join ho gaye!", ChatMessage.TYPE_SYSTEM));
                scrollToBottom();
            }
            @Override
            public void onError(String error) {
                Toast.makeText(InternetChatActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
            }
        });
        btnJoinRoom.setOnClickListener(v -> joinRoom());
        btnSend.setOnClickListener(v -> sendMessage());
        btnBack.setOnClickListener(v -> finish());
    }
    private void joinRoom() {
        String name = etYourName.getText().toString().trim();
        String room = etRoomName.getText().toString().trim().toLowerCase().replaceAll("[^a-z0-9]", "");
        if (TextUtils.isEmpty(name)) { etYourName.setError("Naam zaroori hai"); return; }
        if (TextUtils.isEmpty(room)) { etRoomName.setError("Room naam zaroori hai"); return; }
        internetManager.joinRoom(room, name);
    }
    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;
        internetManager.sendMessage(text);
        messageAdapter.addMessage(new ChatMessage(internetManager.getMyName(), text, ChatMessage.TYPE_SENT));
        etMessage.setText("");
        scrollToBottom();
    }
    private void scrollToBottom() {
        recyclerMessages.post(() -> recyclerMessages.scrollToPosition(messageAdapter.getItemCount() - 1));
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        internetManager.leaveRoom();
    }
}
