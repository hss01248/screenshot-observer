# Screenshot-observer

# usage



# 兼容性

参考这个:

https://github.com/DoubleD0721/Screenshot

目前Android针对截屏的监控主要有三种方式：

1. 利用FileObserver监听某个目录中资源的变化
2. 利用ContentObserver监听全部资源的变化
3. 直接监听截屏快捷键(由于不同的厂商自定义的原因，使用这种方法进行监听比较困难)

本文主要使用ContentObserver的方式来实现对截屏的监控。

## Android 各版本适配



主要针对Android 13及Android 14更新的存储权限进行适配。

在Android 13中，存储权限从原来的`READ_EXTERNAL_STORAGE`细化成为`READ_MEDIA_IMAGES`/`READ_MEDIA_VIDEO`/`READ_MEDIA_AUDIO`三种权限，在进行权限判断的时候需要进行版本区分。

在Android 14中，存储权限从Android 13的细化权限中更新成为允许用户选择部分图片资源给应用访问。但是针对截屏增加了一个新的截屏监控权限`DETECT_SCREEN_CAPTURE`，该权限默认为开且用户无感知，针对用户只给部分权限的情况，我们可以通过该权限来获取用户的截屏动作，尝试一些不依赖截屏文件的操作。

| 权限状态       | Android 13及以下机型   | Android 14及以上机型                    |
| -------------- | ---------------------- | --------------------------------------- |
| 有全部相册权限 | 使用媒体库监控实现监控 | 使用媒体库监控实现监控                  |
| 有部分相册权限 | 无法进行监控           | 使用系统API进行监控(但无法拿到截屏文件) |
| 没有相册权限   | 无法进行监控           | 使用系统API进行监控(但无法拿到截屏文件) |

在上述兼容性判定的基础上,测试可发现:

在Android13及以下机型上,**没有相册/存储权限时**,使用mContext.getContentResolver().registerContentObserver(), 在media store数据有新增,修改,删除时,**能够接收到回调**,

但是因为没有权限,无法查询到具体哪条数据变更,以及那张图片的具体数据,也就无法使用修改时间,图片尺寸等来判断是否为截图.

但是,如果限定此监测行为仅在当前app在前台时监听,退到后台时取消监听,那么所有监听到的都是本app在前台时操作中发生的行为,

此时对media store的修改行为,**大概率是截屏行为**,小概率为其他app在后台操作media store,比如一些图片同步,批量图片下载后更新media store. 



# 使用

### 在activity的onResume里注册监听,在onPause()里取消监听:

```java
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
```

