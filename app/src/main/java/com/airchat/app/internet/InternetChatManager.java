package com.airchat.app.internet;
import android.os.Handler;
import android.os.Looper;
import com.airchat.app.model.Protocol;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.HashMap;
import java.util.Map;
public class InternetChatManager {
    public interface Listener {
        void onMessageReceived(Protocol.Decoded message);
        void onConnected(String roomName);
        void onError(String error);
    }
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Listener listener;
    private DatabaseReference roomRef;
    private ChildEventListener childListener;
    private String currentRoom;
    private String myName;
    private long joinedAt;
    public void setListener(Listener listener) { this.listener = listener; }
    public void joinRoom(String roomName, String userName) {
        leaveRoom();
        this.currentRoom = roomName;
        this.myName = userName;
        this.joinedAt = System.currentTimeMillis();
        roomRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomName).child("messages");
        childListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                try {
                    Map<String, Object> data = (Map<String, Object>) snapshot.getValue();
                    if (data == null) return;
                    long timestamp = data.containsKey("timestamp") ? ((Number) data.get("timestamp")).longValue() : 0;
                    if (timestamp < joinedAt) return;
                    String sender = (String) data.get("sender");
                    if (sender != null && sender.equals(myName)) return;
                    Protocol.Decoded decoded = new Protocol.Decoded();
                    decoded.type = "text";
                    decoded.sender = sender != null ? sender : "Unknown";
                    decoded.text = (String) data.get("text");
                    if (listener != null) mainHandler.post(() -> listener.onMessageReceived(decoded));
                } catch (Exception e) {}
            }
            @Override public void onChildChanged(DataSnapshot s, String p) {}
            @Override public void onChildRemoved(DataSnapshot s) {}
            @Override public void onChildMoved(DataSnapshot s, String p) {}
            @Override public void onCancelled(DatabaseError error) {
                if (listener != null) mainHandler.post(() -> listener.onError(error.getMessage()));
            }
        };
        roomRef.addChildEventListener(childListener);
        if (listener != null) listener.onConnected(roomName);
    }
    public void sendMessage(String text) {
        if (roomRef == null || myName == null) return;
        Map<String, Object> message = new HashMap<>();
        message.put("sender", myName);
        message.put("text", text);
        message.put("type", "text");
        message.put("timestamp", System.currentTimeMillis());
        roomRef.push().setValue(message);
    }
    public void leaveRoom() {
        if (roomRef != null && childListener != null) roomRef.removeEventListener(childListener);
        roomRef = null; childListener = null; currentRoom = null;
    }
    public boolean isInRoom() { return currentRoom != null; }
    public String getCurrentRoom() { return currentRoom; }
    public String getMyName() { return myName; }
}
