package com.airchat.app.auth;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.airchat.app.MainActivity;
import com.airchat.app.R;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
public class LoginActivity extends AppCompatActivity {
    private EditText etPhone, etOtp, etName;
    private Button btnSendOtp, btnVerifyOtp, btnSaveName;
    private LinearLayout panelPhone, panelOtp, panelName;
    private ProgressBar progressBar;
    private FirebaseAuth auth;
    private String verificationId;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) { goToMain(); return; }
        etPhone = findViewById(R.id.etPhone);
        etOtp = findViewById(R.id.etOtp);
        etName = findViewById(R.id.etName);
        btnSendOtp = findViewById(R.id.btnSendOtp);
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp);
        btnSaveName = findViewById(R.id.btnSaveName);
        panelPhone = findViewById(R.id.panelPhone);
        panelOtp = findViewById(R.id.panelOtp);
        panelName = findViewById(R.id.panelName);
        progressBar = findViewById(R.id.progressBar);
        btnSendOtp.setOnClickListener(v -> sendOtp());
        btnVerifyOtp.setOnClickListener(v -> verifyOtp());
        btnSaveName.setOnClickListener(v -> saveName());
    }
    private void sendOtp() {
        String phone = etPhone.getText().toString().trim();
        if (phone.isEmpty() || phone.length() < 10) { etPhone.setError("Sahi number daalein"); return; }
        if (!phone.startsWith("+")) phone = "+91" + phone;
        progressBar.setVisibility(View.VISIBLE);
        btnSendOtp.setEnabled(false);
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phone).setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(PhoneAuthCredential credential) {
                        progressBar.setVisibility(View.GONE);
                        auth.signInWithCredential(credential).addOnSuccessListener(r -> checkProfile());
                    }
                    @Override
                    public void onVerificationFailed(FirebaseException e) {
                        progressBar.setVisibility(View.GONE);
                        btnSendOtp.setEnabled(true);
                        Toast.makeText(LoginActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                    @Override
                    public void onCodeSent(String verId, PhoneAuthProvider.ForceResendingToken token) {
                        progressBar.setVisibility(View.GONE);
                        verificationId = verId;
                        panelPhone.setVisibility(View.GONE);
                        panelOtp.setVisibility(View.VISIBLE);
                        Toast.makeText(LoginActivity.this, "OTP bheja gaya!", Toast.LENGTH_SHORT).show();
                    }
                }).build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }
    private void verifyOtp() {
        String otp = etOtp.getText().toString().trim();
        if (otp.length() != 6) { etOtp.setError("6 digit OTP daalein"); return; }
        progressBar.setVisibility(View.VISIBLE);
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, otp);
        auth.signInWithCredential(credential).addOnCompleteListener(task -> {
            progressBar.setVisibility(View.GONE);
            if (task.isSuccessful()) checkProfile();
            else Toast.makeText(this, "Galat OTP", Toast.LENGTH_SHORT).show();
        });
    }
    private void checkProfile() {
        FirebaseFirestore.getInstance().collection("users")
                .document(auth.getCurrentUser().getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && doc.getString("name") != null) goToMain();
                    else { panelOtp.setVisibility(View.GONE); panelName.setVisibility(View.VISIBLE); }
                });
    }
    private void saveName() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) { etName.setError("Naam daalein"); return; }
        String uid = auth.getCurrentUser().getUid();
        Map<String, Object> user = new HashMap<>();
        user.put("name", name);
        user.put("phone", auth.getCurrentUser().getPhoneNumber());
        user.put("uid", uid);
        user.put("online", true);
        FirebaseFirestore.getInstance().collection("users").document(uid).set(user)
                .addOnSuccessListener(v -> goToMain());
    }
    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
