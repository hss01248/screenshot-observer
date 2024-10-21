package com.hss01248.media.observer;

import android.content.ContentUris;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;



import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class ScreenShotListenManagerBelowAndroid14 {
    private static final String TAG = "ScreenShotListenManager";


    /**
     * 读取媒体数据库时需要读取的列, 其中 WIDTH 和 HEIGHT 字段在 API 16 以后才有
     */
    private static final String[] MEDIA_PROJECTIONS_API_16 = {
            MediaStore.Images.ImageColumns.DATA,
            MediaStore.Images.ImageColumns.DATE_TAKEN,
            MediaStore.Images.ImageColumns.WIDTH,
            MediaStore.Images.ImageColumns.HEIGHT,
            MediaStore.Images.ImageColumns._ID,
    };

    /**
     * 截屏依据中的路径判断关键字
     */
    private static final String[] KEYWORDS = {
            "screenshot", "screen_shot", "screen-shot", "screen shot",
            "screencapture", "screen_capture", "screen-capture", "screen capture",
            "screencap", "screen_cap", "screen-cap", "screen cap"
    };

    private static Point sScreenRealSize;
    public  boolean isAppDebuggable() {
        try {
            ApplicationInfo info = mContext.getApplicationInfo();
            return (info.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    /**
     * 已回调过的路径
     */
    private final static List<String> sHasCallbackPaths = new ArrayList<String>();

    private Context mContext;

    private OnScreenShotListener mListener;

    private long mStartListenTime;

    /**
     * 内部存储器内容观察者
     */
    private MediaContentObserver mInternalObserver;

    /**
     * 外部存储器内容观察者
     */
    private MediaContentObserver mExternalObserver;

    /**
     * 运行在 UI 线程的 Handler, 用于运行监听器回调
     */
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());

    private ScreenShotListenManagerBelowAndroid14(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("The context must not be null.");
        }
        mContext = context;

        // 获取屏幕真实的分辨率
        if (sScreenRealSize == null) {
            sScreenRealSize = getRealScreenSize();
            if (sScreenRealSize != null) {
                if(isAppDebuggable()){
                    Log.d(TAG, "Screen Real Size: " + sScreenRealSize.x + " * " + sScreenRealSize.y);
                }

            } else {
                if(isAppDebuggable()){
                    Log.w(TAG, "Get screen real size failed.");
                }
            }
        }
    }
   static ScreenShotListenManagerBelowAndroid14 screenShotListenManager;
    public static ScreenShotListenManagerBelowAndroid14 getInstance(Context context) {
        if(screenShotListenManager == null){
            //assertInMainThread();
            screenShotListenManager = new ScreenShotListenManagerBelowAndroid14(context.getApplicationContext());
        }

        return screenShotListenManager;
    }

    /**
     * 启动监听
     */
    public void startListen() {
        assertInMainThread();

//        sHasCallbackPaths.clear();

        // 记录开始监听的时间戳
        mStartListenTime = System.currentTimeMillis();

        // 创建内容观察者
        //mInternalObserver = new MediaContentObserver(MediaStore.Images.Media.INTERNAL_CONTENT_URI, mUiHandler);
        mExternalObserver = new MediaContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mUiHandler);

        //Android Q(10) ContentObserver 不回调 onChange这篇文章提供的解决方法：
        // 在Android Q版本上调用注册媒体数据库监听的方法registerContentObserver时传入 notifyForDescendants参数值改为 true，
        // Android Q之前的版本仍然传入 false。
        //
        //作者：yake
        //链接：https://juejin.cn/post/7189126960319037495
        //来源：稀土掘金
        //著作权归作者所有。商业转载请联系作者获得授权，非商业转载请注明出处。
        boolean notifyForDescendants = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;

        // 注册内容观察者
        /*mContext.getContentResolver().registerContentObserver(
                MediaStore.Images.Media.INTERNAL_CONTENT_URI,
                notifyForDescendants,
                mInternalObserver
        );*/
        mContext.getContentResolver().registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                notifyForDescendants,
                mExternalObserver
        );
    }

    /**
     * 停止监听
     */
    public void stopListen() {
        assertInMainThread();

        // 注销内容观察者
        if (mInternalObserver != null) {
            try {
                mContext.getContentResolver().unregisterContentObserver(mInternalObserver);
            } catch (Exception e) {
                e.printStackTrace();
            }
            mInternalObserver = null;
        }
        if (mExternalObserver != null) {
            try {
                mContext.getContentResolver().unregisterContentObserver(mExternalObserver);
            } catch (Exception e) {
                e.printStackTrace();
            }
            mExternalObserver = null;
        }

        // 清空数据
        mStartListenTime = 0;
//        sHasCallbackPaths.clear();

        //切记！！！:必须设置为空 可能mListener 会隐式持有Activity导致释放不掉
        mListener = null;
    }

    /**
     * 处理媒体数据库的内容改变
     */
    private void handleMediaContentChange(Uri contentUri) {
        Cursor cursor = null;
        try {
            // 数据改变时查询数据库中最后加入的一条数据
            //IllegalArgumentException: Invalid token limit
            //From Android 11, LIMIT and OFFSET should be retrieved using Bundle by
            //public Cursor query (Uri uri,
            //                String[] projection,
            //                Bundle queryArgs,
            //                CancellationSignal cancellationSignal)
 /*           if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
                Bundle queryArgs = new Bundle();
                queryArgs.putInt(ContentResolver.QUERY_ARG_OFFSET, 0);
                //queryArgs.putInt(ContentResolver.QUERY_ARG_LIMIT, 1);
                queryArgs.putString(ContentResolver.QUERY_ARG_SORT_COLUMNS, MediaStore.Images.Media._ID);
                queryArgs.putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING);
                cursor = mContext.getContentResolver().query(
                        contentUri,
                        MEDIA_PROJECTIONS_API_16,
                        queryArgs,
                        null
                );
            }else {
                cursor = mContext.getContentResolver().query(
                        contentUri,
                         MEDIA_PROJECTIONS_API_16,
                        null,
                        null,
                        MediaStore.Images.ImageColumns._ID + " desc"
                );
            }*/

            cursor = mContext.getContentResolver().query(
                    contentUri,
                    MEDIA_PROJECTIONS_API_16,
                    null,
                    null,
                    MediaStore.Images.ImageColumns._ID + " desc"
                    //IllegalArgumentException: Invalid token limit
            );


            if (cursor == null) {
                if(isAppDebuggable()){
                    Log.e(TAG, "Deviant logic. cursor == null");
                }
                return;
            }
            if (!cursor.moveToFirst()) {
                if(isAppDebuggable()){
                    Log.d(TAG, "Cursor no data. 大概率是因为没有权限");
                }

                if (mListener != null && !checkCallback("")) {
                    mListener.onShot(null,false);
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            sHasCallbackPaths.remove("");
                        }
                    },2000);

                }
                return;
            }

            // 获取各列的索引
            int dataIndex = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            int dateTakenIndex = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_TAKEN);
            int widthIndex = -1;
            int heightIndex = -1;
            if (Build.VERSION.SDK_INT >= 16) {
                widthIndex = cursor.getColumnIndex(MediaStore.Images.ImageColumns.WIDTH);
                heightIndex = cursor.getColumnIndex(MediaStore.Images.ImageColumns.HEIGHT);
            }
            long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns._ID));
            Uri contentUri0 = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);

            // 获取行数据
            String data = cursor.getString(dataIndex);
            long dateTaken = cursor.getLong(dateTakenIndex);
            int width = 0;
            int height = 0;
            if (widthIndex >= 0 && heightIndex >= 0) {
                width = cursor.getInt(widthIndex);
                height = cursor.getInt(heightIndex);
            } else {
                // API 16 之前, 宽高要手动获取
                Point size = getImageSize(data);
                width = size.x;
                height = size.y;
            }

            // 处理获取到的第一行数据
            handleMediaRowData(data, dateTaken, width, height,contentUri0);

        } catch (Exception e) {
            e.printStackTrace();

        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
    }

    private Point getImageSize(String imagePath) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imagePath, options);
        return new Point(options.outWidth, options.outHeight);
    }

    /**
     * 处理获取到的一行数据
     */
    private void handleMediaRowData(String data, long dateTaken, int width, int height,Uri contentUri0) {
        if (checkScreenShot(data, dateTaken, width, height,contentUri0)) {
            if(isAppDebuggable()){
                Log.d(TAG, "ScreenShot: path = " + data + "; size = " + width + " * " + height
                        + "; date = " + dateTaken);
            }

            if (mListener != null && !checkCallback(data)) {
                mListener.onShot(data,true);
            }
        } else {
            // 如果在观察区间媒体数据库有数据改变，又不符合截屏规则，则输出到 log 待分析
            if(isAppDebuggable()){
                Log.w(TAG, "Media content changed, but not screenshot: path = " + data
                        + "; size = " + width + " * " + height + "; date = " + dateTaken);
            }
        }
    }

    /**
     * 判断指定的数据行是否符合截屏条件
     */
    private boolean checkScreenShot(String data, long dateTaken, int width, int height,Uri contentUri0) {
        /*
         * 判断依据一: 时间判断
         */
        // 如果加入数据库的时间在开始监听之前, 或者与当前时间相差大于10秒, 则认为当前没有截屏
        if (dateTaken < mStartListenTime || (System.currentTimeMillis() - dateTaken) > 10 * 1000) {
            if(isAppDebuggable()){
                Log.i(TAG, "加入数据库的时间在开始监听之前, 或者与当前时间相差大于10秒, 则认为当前没有截屏: path = " + data
                        + "; size = " + width + " * " + height + "; date = " + dateTaken);
            }
            return false;
        }

        /*
         * 判断依据二: 尺寸判断
         */
        if (sScreenRealSize != null) {
            if(width == sScreenRealSize.x && height >= sScreenRealSize.y){
                //有滚动截屏的情况
                if(isAppDebuggable()){
                    if(height > sScreenRealSize.y){
                        Log.i(TAG, "检测到有滚动截屏的情况: path = " + data
                                + "; size = " + width + " * " + height + "; sScreenRealSize = " + sScreenRealSize);
                    }
                }
            }else {
                // 如果图片尺寸超出屏幕, 则认为当前没有截屏
                if (!((width <= sScreenRealSize.x && height <= sScreenRealSize.y)
                        || (height <= sScreenRealSize.x && width <= sScreenRealSize.y))) {

                    if(isAppDebuggable()){
                        Log.i(TAG, "图片尺寸超出屏幕, 则认为当前没有截屏: path = " + data
                                + "; size = " + width + " * " + height + "; sScreenRealSize = " + sScreenRealSize);
                    }
                    return false;
                }
            }

        }

        /*
         * 判断依据三: 路径判断
         */
        if (TextUtils.isEmpty(data)) {
            if(isAppDebuggable()){
                Log.i(TAG, "路径为空, 则认为当前没有截屏: path = " + data
                        + "; size = " + width + " * " + height + "; sScreenRealSize = " + sScreenRealSize);
            }
            return false;
        }
        data = data.toLowerCase();
        // 判断图片路径是否含有指定的关键字之一, 如果有, 则认为当前截屏了
        for (String keyWork : KEYWORDS) {
            if (data.contains(keyWork)) {
                if(isAppDebuggable()){
                    Log.i(TAG, "截屏判断命中: path = " + data
                            + "; size = " + width + " * " + height + "; sScreenRealSize = " + sScreenRealSize+",date:"+dateTaken);
                }
                return true;
            }
        }
        if(isAppDebuggable()){
            Log.i(TAG, "没有包含screen shoot,capture等字样,不是截屏: path = " + data
                    + "; size = " + width + " * " + height + "; sScreenRealSize = " + sScreenRealSize);
        }

        return false;
    }

    /**
     * 判断是否已回调过, 某些手机ROM截屏一次会发出多次内容改变的通知; <br/>
     * 删除一个图片也会发通知, 同时防止删除图片时误将上一张符合截屏规则的图片当做是当前截屏.
     */
    private boolean checkCallback(String imagePath) {
        if (sHasCallbackPaths.contains(imagePath)) {
            if(isAppDebuggable()){
                Log.d(TAG, "ScreenShot: imgPath has done"
                        + "; imagePath = " + imagePath);
            }
            return true;
        }
        // 大概缓存15~20条记录便可
        if (sHasCallbackPaths.size() >= 20) {
            for (int i = 0; i < 5; i++) {
                sHasCallbackPaths.remove(0);
            }
        }
        sHasCallbackPaths.add(imagePath);
        return false;
    }

    /**
     * 获取屏幕分辨率
     */
    private Point getRealScreenSize() {
        Point screenSize = null;
        try {
            screenSize = new Point();
            WindowManager windowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
            Display defaultDisplay = windowManager.getDefaultDisplay();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                defaultDisplay.getRealSize(screenSize);
            } else {
                try {
                    Method mGetRawW = Display.class.getMethod("getRawWidth");
                    Method mGetRawH = Display.class.getMethod("getRawHeight");
                    screenSize.set(
                            (Integer) mGetRawW.invoke(defaultDisplay),
                            (Integer) mGetRawH.invoke(defaultDisplay)
                    );
                } catch (Exception e) {
                    screenSize.set(defaultDisplay.getWidth(), defaultDisplay.getHeight());
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return screenSize;
    }



    private int dp2px(Context ctx, float dp) {
        float scale = ctx.getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }

    /**
     * 设置截屏监听器
     */
    public void setListener(OnScreenShotListener listener) {
        mListener = listener;
    }

    public interface OnScreenShotListener {
        void onShot(String imagePath,boolean sure);
    }

    private static void assertInMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            StackTraceElement[] elements = Thread.currentThread().getStackTrace();
            String methodMsg = null;
            if (elements != null && elements.length >= 4) {
                methodMsg = elements[3].toString();
            }
            throw new IllegalStateException("Call the method must be in main thread: " + methodMsg);
        }
    }

    /**
     * 媒体内容观察者(观察媒体数据库的改变)
     */
    private class MediaContentObserver extends ContentObserver {

        private Uri mContentUri;

        public MediaContentObserver(Uri contentUri, Handler handler) {
            super(handler);
            mContentUri = contentUri;
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            if(isAppDebuggable()){
                Log.d("MediaContent","监测到MediaContent change: selfChange:"+selfChange+", " +mContentUri+", " +this.toString());
            }

            handleMediaContentChange(mContentUri);
        }
    }


}

