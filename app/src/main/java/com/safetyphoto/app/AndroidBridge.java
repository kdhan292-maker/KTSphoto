package com.safetyphoto.app;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class AndroidBridge {

    private final Context context;

    public AndroidBridge(Context context) {
        this.context = context;
    }

    /**
     * JS에서 window.Android.savePhoto(base64Data, filename) 로 호출
     * base64Data : "data:image/jpeg;base64,/9j/..." 형식 또는 순수 base64
     * filename   : 저장할 파일명 (예: "260330_서구1_작업전확대.jpg")
     * 반환값     : true = 성공, false = 실패
     */
    @JavascriptInterface
    public boolean savePhoto(String base64Data, String filename) {
        try {
            // data URL 헤더 제거
            String pure = base64Data;
            int commaIdx = base64Data.indexOf(',');
            if (commaIdx >= 0) {
                pure = base64Data.substring(commaIdx + 1);
            }

            byte[] bytes = Base64.decode(pure, Base64.DEFAULT);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10 (Q) 이상 : MediaStore API 사용
                return saveViaMediaStore(bytes, filename);
            } else {
                // Android 9 이하 : 직접 파일 저장
                return saveToLegacyStorage(bytes, filename);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Android 10+ : MediaStore를 통해 Pictures/안전사진 에 저장 */
    private boolean saveViaMediaStore(byte[] bytes, String filename) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/안전사진");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);

            Uri uri = context.getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            if (uri == null) return false;

            try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                if (out == null) return false;
                out.write(bytes);
                out.flush();
            }

            // IS_PENDING 해제 → 갤러리에 표시
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            context.getContentResolver().update(uri, values, null, null);

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Android 9 이하 : DCIM/안전사진 에 직접 저장 */
    private boolean saveToLegacyStorage(byte[] bytes, String filename) {
        try {
            File dir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "안전사진");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(dir, filename);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(bytes);
                fos.flush();
            }

            // 미디어 스캔 (갤러리에 바로 표시)
            android.media.MediaScannerConnection.scanFile(
                    context,
                    new String[]{file.getAbsolutePath()},
                    new String[]{"image/jpeg"},
                    null);

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * JS에서 현재 안드로이드 버전 확인용 (선택적)
     * window.Android.getAndroidVersion() → 정수 반환
     */
    @JavascriptInterface
    public int getAndroidVersion() {
        return Build.VERSION.SDK_INT;
    }
}
