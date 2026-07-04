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
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { checkUsername(s.toString()); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        btnSave.setOnClickListener(v -> saveProfile());
    }
    private void checkUsername(String username) {
        if (username.length() < 3) { tvUsernameStatus.setText(""); return; }
        String clean = username.toLowerCase().replaceAll("[^a-z0-9._]", "");
        db.collection("users").whereEqualTo("username", clean).get()
            .addOnSuccessListener(snap -> {
                if (snap.isEmpty()) {
                    tvUsernameStatus.setText("@ " + clean + " available");
                    tvUsernameStatus.setTextColor(0xFF008B7A);
                } else {
                    tvUsernameStatus.setText("@ " + clean + " already taken");
                    tvUsernameStatus.setTextColor(0xFFE53935);
                }
            });
    }
    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_IMAGE && res == RESULT_OK && data != null) {
            selectedImageUri = data.getData();
            ivDp.setImageURI(selectedImageUri);
        }
    }
    private void saveProfile() {
        String username = etUsername.getText().toString().trim().toLowerCase().replaceAll("[^a-z0-9._]", "");
        String name = etName.getText().toString().trim();
        if (username.length() < 3) { etUsername.setError("Min 3 characters"); return; }
        if (name.isEmpty()) { etName.setError("Naam zaroori hai"); return; }
        progressBar.setVisibility(View.VISIBLE);
        btnSave.setEnabled(false);
        String uid = "user_" + username;
        db.collection("users").whereEqualTo("username", username).get()
            .addOnSuccessListener(snap -> {
                if (!snap.isEmpty()) {
                    progressBar.setVisibility(View.GONE);
                    btnSave.setEnabled(true);
                    etUsername.setError("Username already taken");
                    return;
                }
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
    private void saveToFirestore(String uid, String name, String username, String dpUrl) {
        Map<String, Object> user = new HashMap<>();
        user.put("uid", uid);
        user.put("name", name);
        user.put("username", username);
        user.put("dpUrl", dpUrl);
        user.put("bio", "");
        user.put("online", true);
        db.collection("users").document(uid).set(user)
            .addOnSuccessListener(v -> {
                prefs.edit().putString("uid", uid).putString("name", name)
                    .putString("username", username).putString("dpUrl", dpUrl).apply();
                goToMain();
            })
            .addOnFailureListener(e -> {
                progressBar.setVisibility(View.GONE);
                btnSave.setEnabled(true);
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
    }
    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
