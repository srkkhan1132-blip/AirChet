package com.airchat.app;
import android.content.Intent;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.airchat.app.R;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
public class SearchUserActivity extends AppCompatActivity {
    private EditText etSearch;
    private RecyclerView recycler;
    private TextView tvEmpty;
    private List<Map<String,Object>> results = new ArrayList<>();
    private FirebaseFirestore db;
    private String myUid, myName;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_user);
        db = FirebaseFirestore.getInstance();
        SharedPreferences prefs = getSharedPreferences("airchat", MODE_PRIVATE);
        myUid = prefs.getString("uid","");
        myName = prefs.getString("name","");
        etSearch = findViewById(R.id.etSearch);
        recycler = findViewById(R.id.recyclerResults);
        tvEmpty = findViewById(R.id.tvEmpty);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        recycler.setLayoutManager(new LinearLayoutManager(this));
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                String q = s.toString().toLowerCase().replace("@","").trim();
                if (q.length() < 2) { results.clear(); updateAdapter(); return; }
                db.collection("users")
                    .whereGreaterThanOrEqualTo("username", q)
                    .whereLessThanOrEqualTo("username", q+"\uf8ff")
                    .get()
                    .addOnSuccessListener(snap -> {
                        results.clear();
                        for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snap) {
                            if (!doc.getId().equals(myUid)) results.add(doc.getData());
                        }
                        updateAdapter();
                    });
            }
        });
    }
    private void updateAdapter() {
        tvEmpty.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
        recycler.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p, int v) {
                View view = LayoutInflater.from(p.getContext()).inflate(R.layout.item_search_user, p, false);
                return new RecyclerView.ViewHolder(view){};
            }
            @Override public void onBindViewHolder(RecyclerView.ViewHolder h, int pos) {
                Map<String,Object> user = results.get(pos);
                String uid = (String) user.get("uid");
                String name = (String) user.get("name");
                String username = (String) user.get("username");
                ((TextView) h.itemView.findViewById(R.id.tvName)).setText(name);
                ((TextView) h.itemView.findViewById(R.id.tvUsername)).setText("@"+username);
                h.itemView.setOnClickListener(v -> {
                    Intent intent = new Intent(SearchUserActivity.this, OnlineChatActivity.class);
                    intent.putExtra("receiverUid", uid);
                    intent.putExtra("receiverName", name);
                    intent.putExtra("receiverUsername", username);
                    intent.putExtra("myUid", myUid);
                    intent.putExtra("myName", myName);
                    startActivity(intent);
                });
            }
            @Override public int getItemCount() { return results.size(); }
        });
    }
}
