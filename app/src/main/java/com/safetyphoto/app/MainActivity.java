package com.safetyphoto.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.*;
import android.view.View;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int PERMISSION_REQUEST   = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 전체화면 (상태바 유지, 풀 스크린)
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webview);

        setupWebView();
        requestAppPermissions();

        // HTML 앱 로드
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
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);

        // 개발 디버깅용 (배포 시 제거 가능)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(false);
        }

        // Android 브릿지 등록 — JS에서 window.Android 로 접근
        webView.addJavascriptInterface(new AndroidBridge(this), "Android");

        // 카메라/파일 선택 처리
        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                Intent intent = params.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                // WebView 카메라 권한 자동 허용
                request.grant(request.getResources());
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return false; // 내부 링크는 WebView 내에서 처리
            }
        });
    }

    // 앱 권한 요청
    private void requestAppPermissions() {
        List<String> needed = new ArrayList<>();

        String[] perms;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            perms = new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.READ_MEDIA_IMAGES
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-12
            perms = new String[]{
                Manifest.permission.CAMERA
            };
        } else {
            // Android 8-9
            perms = new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }

        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needed.add(p);
            }
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                needed.toArray(new String[0]), PERMISSION_REQUEST);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null) {
                results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
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
