package com.hss01248.media.observer;

import android.app.Activity;
import android.app.Application;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 按 Activity 注册截屏回调。
 * Android 14 以下：Manager 只挂一个分发 listener，再扇出到所有已注册且未销毁的 Activity。
 * 使用 WeakHashMap，并在 onDestroy 时自动 unregister，降低泄漏风险。
 *
 * @see <a href="https://github.com/DoubleD0721/Screenshot">Screenshot</a>
 */
public class ScreenshotObserverUtil {

    private static final Object LOCK = new Object();
    /** key 弱引用；value 仍请避免强持 Activity（非静态内部类） */
    private static final Map<Activity, ScreenshotCallback> sCallbacks =
            new WeakHashMap<>();
    /** API 34+ 系统 ScreenCaptureCallback，与 sCallbacks 成对 */
    private static final Map<Activity, Activity.ScreenCaptureCallback> sApi34CaptureCallbacks =
            new WeakHashMap<>();

    private static boolean sLifecycleRegistered;
    private static boolean sBelow14Listening;

    private static final ScreenShotListenManagerBelowAndroid14.OnScreenShotListener sDispatcher =
            new ScreenShotListenManagerBelowAndroid14.OnScreenShotListener() {
                @Override
                public void onShot(String imagePath, boolean sure) {
                    dispatchToActivities(imagePath, sure);
                }
            };

    public static void registerObserver(Activity activity, ScreenshotCallback callback) {
        if (activity == null || callback == null) {
            return;
        }
        ensureLifecycleCallbacks(activity.getApplication());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            synchronized (LOCK) {
                // 重复注册先卸旧的
                Activity.ScreenCaptureCallback old = sApi34CaptureCallbacks.remove(activity);
                if (old != null) {
                    try {
                        activity.unregisterScreenCaptureCallback(old);
                    } catch (Exception ignored) {
                    }
                }
                Activity.ScreenCaptureCallback captureCallback =
                        new Activity.ScreenCaptureCallback() {
                            @Override
                            public void onScreenCaptured() {
                                callback.onCapture(null, true);
                            }
                        };
                sCallbacks.put(activity, callback);
                sApi34CaptureCallbacks.put(activity, captureCallback);
                activity.registerScreenCaptureCallback(activity.getMainExecutor(), captureCallback);
            }
            return;
        }

        synchronized (LOCK) {
            boolean wasEmpty = sCallbacks.isEmpty();
            sCallbacks.put(activity, callback);
            ScreenShotListenManagerBelowAndroid14 manager =
                    ScreenShotListenManagerBelowAndroid14.getInstance(activity.getApplicationContext());
            manager.setListener(sDispatcher);
            if (wasEmpty || !sBelow14Listening) {
                manager.startListen();
                sBelow14Listening = true;
            }
        }
    }

    public static void unRegisterObserver(Activity activity) {
        if (activity == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            synchronized (LOCK) {
                sCallbacks.remove(activity);
                Activity.ScreenCaptureCallback captureCallback = sApi34CaptureCallbacks.remove(activity);
                if (captureCallback != null) {
                    try {
                        activity.unregisterScreenCaptureCallback(captureCallback);
                    } catch (Exception ignored) {
                    }
                }
            }
            return;
        }

        synchronized (LOCK) {
            if (sCallbacks.remove(activity) == null) {
                return;
            }
            if (sCallbacks.isEmpty() && sBelow14Listening) {
                ScreenShotListenManagerBelowAndroid14 manager =
                        ScreenShotListenManagerBelowAndroid14.getInstance(activity.getApplicationContext());
                manager.stopListen();
                manager.setListener(null);
                sBelow14Listening = false;
            }
        }
    }

    /** 向所有仍有效的已注册 Activity 分发（主线程，由 Manager.notifyShot 保证） */
    private static void dispatchToActivities(String imagePath, boolean sure) {
        final Uri uri = TextUtils.isEmpty(imagePath) ? null : Uri.fromFile(new File(imagePath));
        final List<ScreenshotCallback> targets = new ArrayList<>();
        synchronized (LOCK) {
            for (Map.Entry<Activity, ScreenshotCallback> entry :
                    new ArrayList<>(sCallbacks.entrySet())) {
                Activity activity = entry.getKey();
                ScreenshotCallback cb = entry.getValue();
                if (cb == null || activity == null) {
                    continue;
                }
                if (activity.isFinishing()) {
                    continue;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) {
                    continue;
                }
                targets.add(cb);
            }
        }
        for (ScreenshotCallback cb : targets) {
            cb.onCapture(uri, sure);
        }
    }

    private static void ensureLifecycleCallbacks(Application application) {
        if (application == null) {
            return;
        }
        synchronized (LOCK) {
            if (sLifecycleRegistered) {
                return;
            }
            sLifecycleRegistered = true;
            application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                }

                @Override
                public void onActivityStarted(Activity activity) {
                }

                @Override
                public void onActivityResumed(Activity activity) {
                }

                @Override
                public void onActivityPaused(Activity activity) {
                }

                @Override
                public void onActivityStopped(Activity activity) {
                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                    // 漏调 unRegister 时自动摘掉，避免静态 Map 拖死 Activity
                    unRegisterObserver(activity);
                }
            });
        }
    }
}
