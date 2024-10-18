package com.hss01248.media.observer;

import android.net.Uri;

import androidx.annotation.Nullable;

/**
 * @Despciption todo
 * @Author hss
 * @Date 10/18/24 10:46 AM
 * @Version 1.0
 */
public interface ScreenshotCallback {

    void onCapture(@Nullable Uri uri, boolean sure);
}
