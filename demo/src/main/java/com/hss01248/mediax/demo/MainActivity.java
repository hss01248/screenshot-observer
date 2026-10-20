package com.hss01248.mediax.demo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.hss01248.media.observer.ScreenshotCallback;
import com.hss01248.media.observer.ScreenshotObserverUtil;

import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_READ_MEDIA = 1001;

    private TextView tvStatus;
    private TextView tvLast;
    private Button btnStartListen;
    private Button btnStopListen;
    private AlertDialog previewDialog;
    private boolean listening;

    private final ScreenshotCallback screenshotCallback = new ScreenshotCallback() {
        @Override
        public void onCapture(@Nullable Uri uri, boolean sure) {
            LogUtils.i("检测到截屏: " + uri, "sure=" + sure);
            runOnUiThread(() -> showScreenshotDialog(uri, sure));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        tvLast = findViewById(R.id.tv_last);
        btnStartListen = findViewById(R.id.btn_start_listen);
        btnStopListen = findViewById(R.id.btn_stop_listen);

        findViewById(R.id.btn_permission).setOnClickListener(v -> requestReadMediaPermission());
        btnStartListen.setOnClickListener(v -> startListen());
        btnStopListen.setOnClickListener(v -> stopListen());

        updatePermissionHint();
        updateListenUi();
    }

    @Override
    protected void onDestroy() {
        if (listening) {
            ScreenshotObserverUtil.unRegisterObserver(this);
            listening = false;
        }
        dismissPreviewDialog();
        super.onDestroy();
    }

    private void startListen() {
        if (listening) {
            ToastUtils.showShort("已在监听中");
            return;
        }
        ScreenshotObserverUtil.registerObserver(this, screenshotCallback);
        listening = true;
        updateListenUi();
        ToastUtils.showShort("已开启监听");
    }

    private void stopListen() {
        if (!listening) {
            ToastUtils.showShort("当前未监听");
            return;
        }
        ScreenshotObserverUtil.unRegisterObserver(this);
        listening = false;
        dismissPreviewDialog();
        updateListenUi();
        ToastUtils.showShort("已关闭监听");
    }

    private void updateListenUi() {
        tvStatus.setText(listening ? "监听状态：已开启" : "监听状态：已关闭");
        btnStartListen.setEnabled(!listening);
        btnStopListen.setEnabled(listening);
    }

    private void requestReadMediaPermission() {
        String permission = readMediaPermission();
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            ToastUtils.showShort("已有读图权限");
            updatePermissionHint();
            return;
        }
        ActivityCompat.requestPermissions(this, new String[]{permission}, REQ_READ_MEDIA);
    }

    private String readMediaPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Manifest.permission.READ_MEDIA_IMAGES;
        }
        return Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private void updatePermissionHint() {
        boolean granted = ContextCompat.checkSelfPermission(this, readMediaPermission())
                == PackageManager.PERMISSION_GRANTED;
        String tip = granted ? "读图权限：已授予" : "读图权限：未授予（Android 14 以下查 MediaStore 需要）";
        tvLast.setText(tip + "\n最近一次：无");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_READ_MEDIA) {
            return;
        }
        boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        ToastUtils.showShort(ok ? "权限已授予" : "权限被拒绝");
        updatePermissionHint();
    }

    private void showScreenshotDialog(@Nullable Uri uri, boolean sure) {
        if (isFinishing() || !listening) {
            return;
        }
        dismissPreviewDialog();

        String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        StringBuilder info = new StringBuilder();
        info.append("时间: ").append(time).append('\n');
        info.append("sure: ").append(sure).append("（是否判定为截屏）\n");
        info.append("sdk: ").append(Build.VERSION.SDK_INT).append('\n');
        info.append("uri: ").append(uri == null ? "null" : uri).append('\n');

        View content = LayoutInflater.from(this).inflate(R.layout.dialog_screenshot_preview, null, false);
        ImageView iv = content.findViewById(R.id.iv_screenshot);
        TextView tvInfo = content.findViewById(R.id.tv_info);

        if (uri == null) {
            info.append("说明: uri 为空。Android 14+ 系统回调不带路径；")
                    .append("或 MediaStore 无读到数据/无权限。");
            iv.setImageDrawable(null);
        } else {
            appendFileMeta(info, uri);
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                if (is == null && "file".equalsIgnoreCase(uri.getScheme())) {
                    String path = uri.getPath();
                    if (!TextUtils.isEmpty(path)) {
                        iv.setImageBitmap(BitmapFactory.decodeFile(path));
                    }
                } else if (is != null) {
                    iv.setImageBitmap(BitmapFactory.decodeStream(is));
                    is.close();
                }
            } catch (Exception e) {
                info.append("加载图片失败: ").append(e.getMessage()).append('\n');
                LogUtils.e(e);
            }
        }

        tvInfo.setText(info.toString());
        tvLast.setText("最近一次: " + time + "\nsure=" + sure + "\nuri=" + uri);

        previewDialog = new AlertDialog.Builder(this)
                .setTitle("检测到截屏")
                .setView(content)
                .setPositiveButton("关闭", null)
                .create();
        previewDialog.show();
    }

    private void appendFileMeta(StringBuilder info, Uri uri) {
        info.append("scheme: ").append(uri.getScheme()).append('\n');
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            String path = uri.getPath();
            info.append("path: ").append(path).append('\n');
            if (!TextUtils.isEmpty(path)) {
                File file = new File(path);
                info.append("exists: ").append(file.exists()).append('\n');
                info.append("length: ").append(file.length()).append(" bytes\n");
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, opts);
                if (opts.outWidth > 0) {
                    info.append("size: ").append(opts.outWidth).append('x').append(opts.outHeight).append('\n');
                }
            }
        }
    }

    private void dismissPreviewDialog() {
        if (previewDialog != null && previewDialog.isShowing()) {
            previewDialog.dismiss();
        }
        previewDialog = null;
    }
}
