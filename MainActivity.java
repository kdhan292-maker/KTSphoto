package com.safetyphoto.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.*;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraImageUri;   // 카메라 촬영 결과 저장 URI

    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int PERMISSION_REQUEST   = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 전체화면
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webview);

        requestAppPermissions();
        setupWebView();

        webView.loadUrl("file:///android_asset/www/index.html");
    }

    private void setupWebView() {
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setSupportZoom(true);
        ws.setBuiltInZoomControls(false);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);

        // Android 브릿지 등록 — JS 에서 window.Android 로 접근
        webView.addJavascriptInterface(new AndroidBridge(this), "Android");

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params) {

                // 이전 콜백 취소
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;

                // capture="environment" → 카메라 직접 실행
                if (params.isCaptureEnabled()) {
                    launchCamera();
                } else {
                    launchGallery();
                }
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return false;
            }
        });
    }

    // ── 카메라 직접 실행 ──────────────────────────────────
    private void launchCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) == null) {
            // 카메라 앱 없으면 갤러리로 대체
            launchGallery();
            return;
        }

        File photoFile = createTempImageFile();
        if (photoFile == null) {
            launchGallery();
            return;
        }

        cameraImageUri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                photoFile);

        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
        cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(cameraIntent, FILE_CHOOSER_REQUEST);
    }

    // ── 갤러리 선택 실행 ──────────────────────────────────
    private void launchGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(
                    Intent.createChooser(intent, "사진 선택"),
                    FILE_CHOOSER_REQUEST);
        } catch (Exception e) {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(null);
                filePathCallback = null;
            }
        }
    }

    // ── 임시 파일 생성 ──────────────────────────────────
    private File createTempImageFile() {
        try {
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                    Locale.getDefault()).format(new Date());
            File dir = new File(getCacheDir(), "camera_temp");
            if (!dir.exists()) dir.mkdirs();
            return File.createTempFile("IMG_" + stamp, ".jpg", dir);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // ── 촬영/선택 결과 처리 ──────────────────────────────
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != FILE_CHOOSER_REQUEST) return;
        if (filePathCallback == null) return;

        Uri[] results = null;

        if (resultCode == Activity.RESULT_OK) {
            if (cameraImageUri != null) {
                // 카메라 촬영 결과 → 임시 파일 URI
                results = new Uri[]{ cameraImageUri };
            } else if (data != null) {
                // 갤러리 선택 결과
                results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            }
        }

        cameraImageUri = null;
        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
    }

    // ── 권한 요청 ──────────────────────────────────────
    private void requestAppPermissions() {
        List<String> needed = new ArrayList<>();

        String[] perms;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms = new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.READ_MEDIA_IMAGES
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            perms = new String[]{
                Manifest.permission.CAMERA
            };
        } else {
            perms = new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }

        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(p);
            }
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                needed.toArray(new String[0]), PERMISSION_REQUEST);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
