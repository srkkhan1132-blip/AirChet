package com.airchat.app.auth;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.airchat.app.MainActivity;
import com.airchat.app.R;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;
public class SignupActivity extends AppCompatActivity {
    private EditText etUsername, etName;
    private Button btnSave;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private SharedPreferences prefs;
    private FirebaseFirestore db;
    private boolean usernameOk = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);
        prefs = getSharedPreferences("airchat", MODE_PRIVATE);
        db = FirebaseFirestore.getInstance();
        if (prefs.getString("uid", null) != null) { startActivity(new Intent(this, MainActivity.class)); finish(); return; }
        etUsername = findViewById(R.id.etUsername);
        etName = findViewById(R.id.etName);
        btnSave = findViewById(R.id.btnSave);
        progressBar = findViewById(R.id.progressBar);
        tvStatus = findViewById(R.id.tvStatus);
        etUsername.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                String u = s.toString().toLowerCase().replaceAll("[^a-z0-9._]","");
                if (u.length() < 3) { tvStatus.setText("Min 3 characters"); usernameOk = false; return; }
                db.collection("users").document("user_"+u).get().addOnSuccessListener(doc -> {
                    if (!doc.exists()) { tvStatus.setText("Available: @"+u); tvStatus.setTextColor(0xFF00897B); usernameOk = true; }
                    else { tvStatus.setText("Taken: @"+u); tvStatus.setTextColor(0xFFE53935); usernameOk = false; }
                });
            }
        });
        btnSave.setOnClickListener(v -> save());
    }
    private void save() {
        String username = etUsername.getText().toString().trim().toLowerCase().replaceAll("[^a-z0-9._]","");
        String name = etName.getText().toString().trim();
        if (!usernameOk) { Toast.makeText(this,"Valid username chunein",Toast.LENGTH_SHORT).show(); return; }
        if (name.isEmpty()) { etName.setError("Naam zaroori hai"); return; }
        progressBar.setVisibility(View.VISIBLE);
        btnSave.setEnabled(false);
        String uid = "user_"+username;
        Map<String,Object> user = new HashMap<>();
        user.put("uid",uid); user.put("name",name); user.put("username",username); user.put("dpUrl",""); user.put("bio",""); user.put("online",true);
        db.collection("users").document(uid).set(user).addOnSuccessListener(v -> {
            prefs.edit().putString("uid",uid).putString("name",name).putString("username",username).putString("dpUrl","").apply();
            startActivity(new Intent(this, MainActivity.class)); finish();
        }).addOnFailureListener(e -> {
            progressBar.setVisibility(View.GONE); btnSave.setEnabled(true);
            Toast.makeText(this,"Error: "+e.getMessage(),Toast.LENGTH_LONG).show();
        });
    }
}
