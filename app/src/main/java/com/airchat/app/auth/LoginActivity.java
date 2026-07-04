package com.airchat.app.auth;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.airchat.app.MainActivity;
import com.airchat.app.R;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;
public class LoginActivity extends AppCompatActivity {
    private static final int PICK_IMAGE = 100;
    private EditText etUsername, etName;
    private ImageView ivDp;
    private Button btnSave;
    private ProgressBar progressBar;
    private TextView tvUsernameStatus;
    private Uri selectedImageUri;
    private SharedPreferences prefs;
    private FirebaseFirestore db;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        prefs = getSharedPreferences("airchat", MODE_PRIVATE);
        db = FirebaseFirestore.getInstance();
        if (prefs.getString("uid", null) != null) { goToMain(); return; }
        etUsername = findViewById(R.id.etUsername);
        etName = findViewById(R.id.etName);
        ivDp = findViewById(R.id.ivDp);
        btnSave = findViewById(R.id.btnSave);
        progressBar = findViewById(R.id.progressBar);
        tvUsernameStatus = findViewById(R.id.tvUsernameStatus);
        ivDp.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, PICK_IMAGE);
        });
        etUsername.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { checkUsername(s.toString()); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        btnSave.setOnClickListener(v -> saveProfile());
    }
    private void checkUsername(String username) {
        if (username.length() < 3) { tvUsernameStatus.setText(""); return; }
        username = username.toLowerCase().replaceAll("[^a-z0-9._]", "");
        db.collection("users").whereEqualTo("username", username).get()
            .addOnSuccessListener(snap -> {
                if (snap.isEmpty()) {
                    tvUsernameStatus.setText("✓ @" + username + " available");
                    tvUsernameStatus.setTextColor(0xFF008B7A);
                } else {
                    tvUsernameStatus.setText("✗ @" + username + " already taken");
                    tvUsernameStatus.setTextColor(0xFFE53935);
                }
            });
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            selectedImageUri = data.getData();
            ivDp.setImageURI(selectedImageUri);
        }
    }
    private void saveProfile() {
        String username = etUsername.getText().toString().trim().toLowerCase().replaceAll("[^a-z0-9._]", "");
        String name = etName.getText().toString().trim();
        if (username.length() < 3) { etUsername.setError("Minimum 3 characters"); return; }
        if (name.isEmpty()) { etName.setError("Naam zaroori hai"); return; }
        progressBar.setVisibility(View.VISIBLE);
        btnSave.setEnabled(false);
        db.collection("users").whereEqualTo("username", username).get()
            .addOnSuccessListener(snap -> {
                if (!snap.isEmpty()) {
                    progressBar.setVisibility(View.GONE);
                    btnSave.setEnabled(true);
                    etUsername.setError("Username already taken");
                    return;
                }
                String uid = "user_" + username;
                if (selectedImageUri != null) {
                    new com.airchat.app.media.CloudinaryUploader(this).uploadMedia(
                        selectedImageUri, "image/jpeg",
                        new com.airchat.app.media.CloudinaryUploader.UploadCallback() {
                            @Override public void onSuccess(String url, String publicId) { saveToFirestore(uid, name, username, url); }
                            @Override public void onFailure(String error) { saveToFirestore(uid, name, username, ""); }
                            @Override public void onProgress(int percent) {}
                        });
                } else {
                    saveToFirestore(uid, name, username, "");
                }
            });
    }
    private void
echo '<?xml version="1.0" encoding="utf-8"?><LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical" android:gravity="center" android:padding="32dp" android:background="@color/bg_dark"><TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="AirChat" android:textColor="@color/accent_green" android:textSize="36sp" android:textStyle="bold" android:layout_marginBottom="4dp"/><TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Apna account banayein" android:textColor="@color/text_secondary" android:textSize="14sp" android:layout_marginBottom="24dp"/><ImageView android:id="@+id/ivDp" android:layout_width="100dp" android:layout_height="100dp" android:background="@drawable/circle_avatar" android:scaleType="centerCrop" android:layout_marginBottom="8dp"/><TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="DP lagayen (tap karein)" android:textColor="@color/accent_green" android:textSize="13sp" android:layout_marginBottom="24dp"/><EditText android:id="@+id/etUsername" android:layout_width="match_parent" android:layout_height="wrap_content" android:hint="@username (jaise @srkkhan)" android:textColor="@color/text_primary" android:textColorHint="@color/text_secondary" android:background="@drawable/bg_input_field" android:paddingStart="16dp" android:paddingEnd="16dp" android:paddingTop="14dp" android:paddingBottom="14dp" android:inputType="textNoSuggestions" android:layout_marginBottom="4dp"/><TextView android:id="@+id/tvUsernameStatus" android:layout_width="match_parent" android:layout_height="wrap_content" android:textSize="12sp" android:layout_marginBottom="12dp"/><EditText android:id="@+id/etName" android:layout_width="match_parent" android:layout_height="wrap_content" android:hint="Aapka poora naam" android:textColor="@color/text_primary" android:textColorHint="@color/text_secondary" android:background="@drawable/bg_input_field" android:paddingStart="16dp" android:paddingEnd="16dp" android:paddingTop="14dp" android:paddingBottom="14dp" android:inputType="textPersonName" android:layout_marginBottom="24dp"/><ProgressBar android:id="@+id/progressBar" android:layout_width="wrap_content" android:layout_height="wrap_content" android:visibility="gone" android:indeterminateTint="@color/accent_green" android:layout_marginBottom="16dp"/><Button android:id="@+id/btnSave" style="@style/Widget.MaterialComponents.Button" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="Account banayein" android:backgroundTint="@color/accent_green" android:textColor="@color/white" android:paddingTop="14dp" android:paddingBottom="14dp"/></LinearLayout>' > app/src/main/res/layout/activity_login.xml
cat > app/src/main/java/com/airchat/app/search/SearchActivity.java << 'EOF'
package com.airchat.app.search;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.airchat.app.R;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class SearchActivity extends AppCompatActivity {
    private EditText etSearch;
    private RecyclerView recycler;
    private TextView tvEmpty;
    private List<Map<String, Object>> results = new ArrayList<>();
    private FirebaseFirestore db;
    private String myUid, myUsername;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        db = FirebaseFirestore.getInstance();
        SharedPreferences prefs = getSharedPreferences("airchat", MODE_PRIVATE);
        myUid = prefs.getString("uid", "");
        myUsername = prefs.getString("username", "");
        etSearch = findViewById(R.id.etSearch);
        recycler = findViewById(R.id.recyclerResults);
        tvEmpty = findViewById(R.id.tvEmpty);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        recycler.setLayoutManager(new LinearLayoutManager(this));
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { searchUsers(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }
    private void searchUsers(String query) {
        if (query.length() < 2) { results.clear(); updateAdapter(); return; }
        query = query.toLowerCase().replace("@", "");
        String finalQuery = query;
        db.collection("users")
            .whereGreaterThanOrEqualTo("username", finalQuery)
            .whereLessThanOrEqualTo("username", finalQuery + "\uf8ff")
            .get()
            .addOnSuccessListener(snap -> {
                results.clear();
                for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snap) {
                    if (!doc.getId().equals(myUid)) {
                        results.add(doc.getData());
                    }
                }
                updateAdapter();
            });
    }
    private void updateAdapter() {
        tvEmpty.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
        recycler.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_user, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }
            @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                Map<String, Object> user = results.get(position);
                String uid = (String) user.get("uid");
                String name = (String) user.get("name");
                String username = (String) user.get("username");
                ((TextView) holder.itemView.findViewById(R.id.tvName)).setText(name);
                ((TextView) holder.itemView.findViewById(R.id.tvUsername)).setText("@" + username);
                holder.itemView.findViewById(R.id.btnFollow).setOnClickListener(v -> followUser(uid, name, username));
            }
            @Override public int getItemCount() { return results.size(); }
        });
    }
    private void followUser(String targetUid, String name, String username) {
        Map<String, Object> followData = new HashMap<>();
        followData.put("uid", targetUid);
        followData.put("name", name);
        followData.put("username", username);
        followData.put("timestamp", System.currentTimeMillis());
        db.collection("users").document(myUid).collection("following").document(targetUid).set(followData)
            .addOnSuccessListener(v -> {
                Map<String, Object> followerData = new HashMap<>();
                followerData.put("uid", myUid);
                followerData.put("username", myUsername);
                followerData.put("timestamp", System.currentTimeMillis());
                db.collection("users").document(targetUid).collection("followers").document(myUid).set(followerData);
                Toast.makeText(this, "@" + username + " ko follow kar liya!", Toast.LENGTH_SHORT).show();
            });
    }
}
