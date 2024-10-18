package com.hss01248.media.observer;

import android.app.Activity;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;



/**
 * @Despciption https://github.com/DoubleD0721/Screenshot
 * @Author hss
 * @Date 10/18/24 10:40 AM
 * @Version 1.0
 */
public class ScreenshotObserverUtil {

    static Map<Activity,Object> map  = new HashMap<>();

    public static void registerObserver(Activity activity, ScreenshotCallback callback){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE){
            Activity.ScreenCaptureCallback captureCallback = new Activity.ScreenCaptureCallback() {
                @Override
                public void onScreenCaptured() {
                    callback.onCapture(null,true);
                }
            };
            map.put(activity,captureCallback);
            activity.registerScreenCaptureCallback(activity.getMainExecutor(), captureCallback);
            return;
        }
        ScreenShotListenManagerBelowAndroid14 manager = ScreenShotListenManagerBelowAndroid14.getInstance(activity.getApplicationContext());

        ScreenShotListenManagerBelowAndroid14.OnScreenShotListener listener = new ScreenShotListenManagerBelowAndroid14.OnScreenShotListener() {
            @Override
            public void onShot(String imagePath,boolean sure) {
                if(TextUtils.isEmpty(imagePath)){
                    callback.onCapture(null,sure);
                }else {
                    callback.onCapture(Uri.fromFile(new File(imagePath)),sure);
                }

            }
        };
        map.put(activity,listener);
        manager.setListener(listener);
        manager.startListen();
    }

    public static void unRegisterObserver(Activity activity){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE){
            Activity.ScreenCaptureCallback captureCallback = (Activity.ScreenCaptureCallback) map.remove(activity);
            if(captureCallback !=null){
                activity.unregisterScreenCaptureCallback(captureCallback);
            }
            return;
        }
        ScreenShotListenManagerBelowAndroid14.OnScreenShotListener captureCallback = (ScreenShotListenManagerBelowAndroid14.OnScreenShotListener) map.remove(activity);
        if(captureCallback !=null){
            ScreenShotListenManagerBelowAndroid14.getInstance(activity.getApplicationContext()).stopListen();
        }
    }
}
