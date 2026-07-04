package com.airchat.app.contacts;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.airchat.app.R;
import com.airchat.app.chat.PrivateChatActivity;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;
public class ContactsActivity extends AppCompatActivity {
    private RecyclerView recycler;
    private TextView tvEmpty;
    private List<String[]> contactList = new ArrayList<>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts);
        recycler = findViewById(R.id.recyclerContacts);
        tvEmpty = findViewById(R.id.tvEmpty);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        recycler.setLayoutManager(new LinearLayoutManager(this));
        loadContacts();
    }
    private void loadContacts() {
        List<String> phoneNumbers = new ArrayList<>();
        Cursor cursor = getContentResolver().query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String number = cursor.getString(cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.NUMBER));
                if (number != null) {
                    number = number.replaceAll("[^0-9]", "");
                    if (number.length() >= 10) {
                        number = number.substring(number.length() - 10);
                        if (!phoneNumbers.contains(number)) phoneNumbers.add(number);
                    }
                }
            }
            cursor.close();
        }
        for (String phone : phoneNumbers) {
            String uid = "user_" + phone;
            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("name");
                        String dpUrl = doc.getString("dpUrl");
                        contactList.add(new String[]{uid, name, dpUrl, phone});
                        updateAdapter();
                    }
                });
        }
    }
    private void updateAdapter() {
        tvEmpty.setVisibility(contactList.isEmpty() ? View.VISIBLE : View.GONE);
        recycler.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contact, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }
            @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                String[] contact = contactList.get(position);
                ((TextView) holder.itemView.findViewById(R.id.tvName)).setText(contact[1]);
                ((TextView) holder.itemView.findViewById(R.id.tvPhone)).setText(contact[3]);
                holder.itemView.setOnClickListener(v -> {
                    SharedPreferences prefs = getSharedPreferences("airchat", MODE_PRIVATE);
                    Intent intent = new Intent(ContactsActivity.this, PrivateChatActivity.class);
                    intent.putExtra("receiverUid", contact[0]);
                    intent.putExtra("receiverName", contact[1]);
                    intent.putExtra("receiverDp", contact[2]);
                    intent.putExtra("myUid", prefs.getString("uid", ""));
                    intent.putExtra("myName", prefs.getString("name", ""));
                    startActivity(intent);
                });
            }
            @Override public int getItemCount() { return contactList.size(); }
        });
    }
}
