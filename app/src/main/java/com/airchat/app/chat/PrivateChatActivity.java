package com.airchat.app.chat;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.airchat.app.R;
import com.airchat.app.adapter.MessageAdapter;
import com.airchat.app.model.ChatMessage;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import java.util.HashMap;
import java.util.Map;
public class PrivateChatActivity extends AppCompatActivity {
    private RecyclerView recyclerMessages;
    private EditText etMessage;
    private ImageButton btnSend, btnBack;
    private TextView tvName;
    private MessageAdapter adapter;
    private FirebaseFirestore db;
    private String myUid, myName, receiverUid, receiverName, chatId;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_private_chat);
        myUid = getIntent().getStringExtra("myUid");
        myName = getIntent().getStringExtra("myName");
        receiverUid = getIntent().getStringExtra("receiverUid");
        receiverName = getIntent().getStringExtra("receiverName");
        chatId = myUid.compareTo(receiverUid) < 0 ? myUid + "_" + receiverUid : receiverUid + "_" + myUid;
        db = FirebaseFirestore.getInstance();
        recyclerMessages = findViewById(R.id.recyclerMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnBack = findViewById(R.id.btnBack);
        tvName = findViewById(R.id.tvChatTitle);
        tvName.setText(receiverName);
        adapter = new MessageAdapter(false);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        recyclerMessages.setLayoutManager(lm);
        recyclerMessages.setAdapter(adapter);
        btnBack.setOnClickListener(v -> finish());
        btnSend.setOnClickListener(v -> sendMessage());
        listenMessages();
    }
    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;
        Map<String, Object> msg = new HashMap<>();
        msg.put("senderUid", myUid);
        msg.put("senderName", myName);
        msg.put("text", text);
        msg.put("timestamp", System.currentTimeMillis());
        db.collection("chats").document(chatId).collection("messages").add(msg)
            .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        adapter.addMessage(new ChatMessage(myName, text, ChatMessage.TYPE_SENT));
        etMessage.setText("");
        recyclerMessages.scrollToPosition(adapter.getItemCount() - 1);
    }
    private void listenMessages() {
        db.collection("chats").document(chatId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener((snapshots, e) -> {
                if (e != null || snapshots == null) return;
                for (com.google.firebase.firestore.DocumentChange dc : snapshots.getDocumentChanges()) {
                    if (dc.getType() == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        String senderUid = dc.getDocument().getString("senderUid");
                        String senderName = dc.getDocument().getString("senderName");
                        String text = dc.getDocument().getString("text");
                        if (senderUid != null && !senderUid.equals(myUid)) {
                            adapter.addMessage(new ChatMessage(senderName, text, ChatMessage.TYPE_RECEIVED));
                            recyclerMessages.scrollToPosition(adapter.getItemCount() - 1);
                        }
                    }
                }
            });
    }
}
