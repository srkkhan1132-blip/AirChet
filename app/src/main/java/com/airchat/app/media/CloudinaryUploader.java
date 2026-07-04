package com.airchat.app.media;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;
public class CloudinaryUploader {
    private static final String CLOUD_NAME = "vaiaxmcb";
    private static final String UPLOAD_URL = "https://api.cloudinary.com/v1_1/" + CLOUD_NAME + "/auto/upload";
    public interface UploadCallback {
        void onSuccess(String url, String publicId);
        void onFailure(String error);
        void onProgress(int percent);
    }
    private final Context context;
    private final OkHttpClient client = new OkHttpClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    public CloudinaryUploader(Context context) {
        this.context = context.getApplicationContext();
    }
    public void uploadMedia(Uri uri, String mimeType, UploadCallback callback) {
        executor.execute(() -> {
            try {
                InputStream inputStream = context.getContentResolver().openInputStream(uri);
                if (inputStream == null) { mainHandler.post(() -> callback.onFailure("Could not read file")); return; }
                byte[] bytes = inputStream.readAllBytes();
                inputStream.close();
                MediaType mediaType = MediaType.parse(mimeType != null ? mimeType : "application/octet-stream");
                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", "upload", RequestBody.create(bytes, mediaType))
                        .addFormDataPart("upload_preset", "airchat_unsigned")
                        .build();
                Request request = new Request.Builder().url(UPLOAD_URL).post(requestBody).build();
                Response response = client.newCall(request).execute();
                String body = response.body().string();
                if (response.isSuccessful()) {
                    JSONObject json = new JSONObject(body);
                    String url = json.getString("secure_url");
                    String publicId = json.getString("public_id");
                    mainHandler.post(() -> callback.onSuccess(url, publicId));
                } else {
                    mainHandler.post(() -> callback.onFailure("Upload failed: " + response.code()));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onFailure(e.getMessage()));
            }
        });
    }
}
