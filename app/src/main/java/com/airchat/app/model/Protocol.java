package com.airchat.app.model;

import android.util.Base64;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Simple wire protocol used over both Bluetooth RFCOMM sockets and
 * Wi-Fi Direct TCP sockets, so the chat layer doesn't care which
 * transport delivered the bytes.
 *
 * Each message is one line of JSON, terminated by '\n'.
 */
public class Protocol {

    public static String encodeText(String senderName, String text) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("type", "text");
            obj.put("sender", senderName);
            obj.put("text", text);
        } catch (JSONException e) { }
        return obj.toString();
    }

    public static String encodeImage(String senderName, byte[] imageBytes) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("type", "image");
            obj.put("sender", senderName);
            obj.put("data", Base64.encodeToString(imageBytes, Base64.NO_WRAP));
        } catch (JSONException e) { }
        return obj.toString();
    }

    public static String encodeSystem(String senderName, String info) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("type", "system");
            obj.put("sender", senderName);
            obj.put("text", info);
        } catch (JSONException e) { }
        return obj.toString();
    }

    public static class Decoded {
        public String type;
        public String sender;
        public String text;
        public byte[] imageBytes;
    }

    public static Decoded decode(String line) {
        Decoded d = new Decoded();
        try {
            JSONObject obj = new JSONObject(line);
            d.type = obj.optString("type", "text");
            d.sender = obj.optString("sender", "Unknown");
            if (d.type.equals("image")) {
                String b64 = obj.optString("data", "");
                d.imageBytes = Base64.decode(b64, Base64.NO_WRAP);
            } else {
                d.text = obj.optString("text", "");
            }
        } catch (JSONException e) {
            d.type = "text";
            d.sender = "Unknown";
            d.text = line;
        }
        return d;
    }
}
