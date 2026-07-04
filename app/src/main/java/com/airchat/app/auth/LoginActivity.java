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
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.airchat.app.MainActivity;
import com.airchat.app.R;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;
public class LoginActivity extends AppCompatActivity {
    private static final int PICK_IMAGE = 100;
    private EditText etName, etPhone;
    private ImageView ivDp;
    private Button btnSave;
    private ProgressBar progressBar;
    private Uri selectedImageUri;
    private SharedPreferences prefs;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        prefs = getSharedPreferences("airchat", MODE_PRIVATE);
        if (prefs.getString("uid", null) != null) {
            goToMain(); return;
        }
        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        ivDp = findViewById(R.id.ivDp);
        btnSave = findViewById(R.id.btnSave);
        progressBar = findViewById(R.id.progressBar);
        ivDp.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, PICK_IMAGE);
        });
        btnSave.setOnClickListener(v -> saveProfile());
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
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        if (name.isEmpty()) { etName.setError("Naam zaroori hai"); return; }
        if (phone.isEmpty() || phone.length() < 10) { etPhone.setError("Phone number zaroori hai"); return; }
        progressBar.setVisibility(View.VISIBLE);
        btnSave.setEnabled(false);
        String uid = "user_" + phone.replaceAll("[^0-9]", "");
        if (selectedImageUri != null) {
            uploadDpAndSave(uid, name, phone);
        } else {
            saveToFirestore(uid, name, phone, "");
        }
    }
    private void uploadDpAndSave(String uid, String name, String phone) {
        new com.airchat.app.media.CloudinaryUploader(this).uploadMedia(
            selectedImageUri, "image/jpeg",
            new com.airchat.app.media.CloudinaryUploader.UploadCallback() {
                @Override public void onSuccess(String url, String publicId) {
                    saveToFirestore(uid, name, phone, url);
                }
                @Override public void onFailure(String error) {
                    saveToFirestore(uid, name, phone, "");
                }
                @Override public void onProgress(int percent) {}
            });
    }
    private void saveToFirestore(String uid, String name, String phone, String dpUrl) {
        Map<String, Object> user = new HashMap<>();
        user.put("uid", uid);
        user.put("name", name);
        user.put("phone", phone);
        user.put("dpUrl", dpUrl);
        user.put("online", true);
        FirebaseFirestore.getInstance().collection("users").document(uid).set(user)
            .addOnSuccessListener(v -> {
                prefs.edit().putString("uid", uid).putString("name", name)
                    .putString("phone", phone).putString("dpUrl", dpUrl).apply();
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
