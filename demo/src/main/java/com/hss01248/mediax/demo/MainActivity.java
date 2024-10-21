package com.hss01248.mediax.demo;


import android.Manifest;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;


import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.PermissionUtils;
import com.blankj.utilcode.util.ScreenUtils;
import com.blankj.utilcode.util.SizeUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.hss01248.media.observer.ScreenshotCallback;
import com.hss01248.media.observer.ScreenshotObserverUtil;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        setContentView(R.layout.activity_main);
        RelativeLayout relativeLayout = findViewById(R.id.root_rl);
        ViewGroup.LayoutParams layoutParams1 = relativeLayout.getLayoutParams();
        layoutParams1.height = ScreenUtils.getScreenHeight()+ SizeUtils.dp2px(300);
        relativeLayout.setLayoutParams(layoutParams1);


    }

    @Override
    protected void onResume() {
        super.onResume();
        LogUtils.d("onResume",this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LogUtils.d("onPause",this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        LogUtils.d("onStart",this);
        ScreenshotObserverUtil.registerObserver(this, new ScreenshotCallback() {
            @Override
            public void onCapture(@Nullable Uri uri,boolean sure) {
                LogUtils.i("检测到截屏:"+uri,"是否100%肯定:"+sure);
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        LogUtils.d("onStop",this);
        ScreenshotObserverUtil.unRegisterObserver(this);
    }


}