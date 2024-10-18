package com.hss01248.mediax.demo;


import android.Manifest;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;


import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.PermissionUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.hss01248.media.observer.ScreenshotCallback;
import com.hss01248.media.observer.ScreenshotObserverUtil;


public class MainActivity extends AppCompatActivity {
    TextView textView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView textView1 = new TextView(this);
        textView1.setText("center");
        setContentView(textView1);

    }

    @Override
    protected void onResume() {
        super.onResume();
        ScreenshotObserverUtil.registerObserver(this, new ScreenshotCallback() {
            @Override
            public void onCapture(@Nullable Uri uri,boolean sure) {
                LogUtils.i("检测到截屏:"+uri,"是否100%肯定:"+sure);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        ScreenshotObserverUtil.unRegisterObserver(this);
    }
}