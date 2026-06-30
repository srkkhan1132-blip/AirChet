package com.airchat.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.airchat.app.R;
import com.airchat.app.model.NearbyDevice;

import java.util.ArrayList;
import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.VH> {

    public interface OnDeviceClickListener {
        void onDeviceClick(NearbyDevice device);
    }

    private final List<NearbyDevice> devices = new ArrayList<>();
    private final OnDeviceClickListener clickListener;

    public DeviceAdapter(OnDeviceClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void addOrUpdate(NearbyDevice device) {
        int idx = devices.indexOf(device);
        if (idx >= 0) {
            devices.set(idx, device);
            notifyItemChanged(idx);
        } else {
            devices.add(device);
            notifyItemInserted(devices.size() - 1);
        }
    }

    public void updateStatus(String address, int status) {
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).getAddress().equals(address)) {
                devices.get(i).setStatus(status);
                notifyItemChanged(i);
                return;
            }
        }
    }

    public void clear() {
        devices.clear();
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return devices.isEmpty();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_device, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        NearbyDevice device = devices.get(position);
        holder.name.setText(device.getName());

        String typeLabel = device.getConnectionType() == NearbyDevice.TYPE_BLUETOOTH
                ? "Bluetooth" : "Wi-Fi Direct";

        switch (device.getStatus()) {
            case NearbyDevice.STATUS_CONNECTING:
                holder.status.setText("Connecting via " + typeLabel + "…");
                holder.progress.setVisibility(View.VISIBLE);
                break;
            case NearbyDevice.STATUS_CONNECTED:
                holder.status.setText("Connected · " + typeLabel);
                holder.progress.setVisibility(View.GONE);
                break;
            default:
                holder.status.setText("Available · " + typeLabel);
                holder.progress.setVisibility(View.GONE);
                break;
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onDeviceClick(device);
        });
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, status;
        ProgressBar progress;

        VH(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvDeviceName);
            status = itemView.findViewById(R.id.tvDeviceStatus);
            progress = itemView.findViewById(R.id.progressConnecting);
        }
    }
}
