package com.airchat.app.model;

public class NearbyDevice {

    public static final int STATUS_AVAILABLE = 0;
    public static final int STATUS_CONNECTING = 1;
    public static final int STATUS_CONNECTED = 2;
    public static final int STATUS_DISCONNECTED = 3;

    public static final int TYPE_BLUETOOTH = 0;
    public static final int TYPE_WIFI_DIRECT = 1;

    private String name;
    private String address;   // MAC address for BT, deviceAddress for WiFi Direct
    private int connectionType;
    private int status;

    public NearbyDevice(String name, String address, int connectionType, int status) {
        this.name = name;
        this.address = address;
        this.connectionType = connectionType;
        this.status = status;
    }

    public String getName() { return name; }
    public String getAddress() { return address; }
    public int getConnectionType() { return connectionType; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof NearbyDevice)) return false;
        NearbyDevice other = (NearbyDevice) obj;
        return address != null && address.equals(other.address);
    }

    @Override
    public int hashCode() {
        return address != null ? address.hashCode() : 0;
    }
}
