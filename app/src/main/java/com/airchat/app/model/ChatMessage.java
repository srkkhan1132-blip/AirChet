package com.airchat.app.model;

public class ChatMessage {

    public static final int TYPE_SENT = 0;
    public static final int TYPE_RECEIVED = 1;
    public static final int TYPE_SYSTEM = 2;       // e.g. "X joined", "Connected"
    public static final int CONTENT_TEXT = 0;
    public static final int CONTENT_IMAGE = 1;

    private String senderName;
    private String text;
    private String imagePath;       // local file path if CONTENT_IMAGE
    private int type;
    private int contentType;
    private long timestamp;

    public ChatMessage(String senderName, String text, int type) {
        this(senderName, text, null, type, CONTENT_TEXT);
    }

    public ChatMessage(String senderName, String text, String imagePath, int type, int contentType) {
        this.senderName = senderName;
        this.text = text;
        this.imagePath = imagePath;
        this.type = type;
        this.contentType = contentType;
        this.timestamp = System.currentTimeMillis();
    }

    public String getSenderName() { return senderName; }
    public String getText() { return text; }
    public String getImagePath() { return imagePath; }
    public int getType() { return type; }
    public int getContentType() { return contentType; }
    public long getTimestamp() { return timestamp; }
}
