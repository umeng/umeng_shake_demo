
package com.example.umengshakedemo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.hardware.SensorEvent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Toast;

import com.umeng.scrshot.adapter.UMBaseAdapter;
import com.umeng.socialize.bean.SHARE_MEDIA;
import com.umeng.socialize.bean.SocializeEntity;
import com.umeng.socialize.bean.StatusCode;
import com.umeng.socialize.controller.RequestType;
import com.umeng.socialize.controller.UMServiceFactory;
import com.umeng.socialize.controller.UMSocialService;
import com.umeng.socialize.controller.UMSsoHandler;
import com.umeng.socialize.sensor.UMSensor.OnSensorListener;
import com.umeng.socialize.sensor.UMSensor.WhitchButton;
import com.umeng.socialize.sensor.controller.UMShakeService;
import com.umeng.socialize.sensor.controller.impl.UMShakeServiceFactory;
import com.umeng.socialize.sso.QZoneSsoHandler;
import com.umeng.socialize.sso.SinaSsoHandler;
import com.umeng.socialize.sso.TencentWBSsoHandler;

import io.vov.vitamio.LibsChecker;
import io.vov.vitamio.MediaPlayer;
import io.vov.vitamio.MediaPlayer.OnBufferingUpdateListener;
import io.vov.vitamio.MediaPlayer.OnCompletionListener;
import io.vov.vitamio.MediaPlayer.OnPreparedListener;
import io.vov.vitamio.MediaPlayer.OnVideoSizeChangedListener;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @ClassName: MainActivity
 * @Description: 该应用演示了使用Vitamio库来播放在线视频， 并且集成友盟社会化组件当中的摇一摇截屏分享功能.由于默认是播放
 *               流媒体，为了播放流畅，大家在测试时可连接WIFI，也避免消耗过多的流量. 说明 :
 *               该demo并不是要演示如何做一个播放器，只是为了演示视频播放器如何集成摇一摇功能，
 *               为了使整个demo更简单、清晰，忽略了很多细节处理,因此该demo有很多不足之处.
 * @author Honghui He
 */
public class MainActivity extends Activity implements
        OnBufferingUpdateListener, OnCompletionListener, OnPreparedListener,
        OnVideoSizeChangedListener, SurfaceHolder.Callback {

    private static final String TAG = "MainActivity";
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;
    /**
     * Vitamio的MediaPlayer
     */
    private MediaPlayer mMediaPlayer = null;
    /**
     * 显示视频的surfaceview
     */
    private SurfaceView mSurfaceView = null;
    private SurfaceHolder mSurfaceHolder = null;

    private ImageButton mPlayButton = null;

    /**
     * 
     */
    private LinearLayout mSeekbarLayout = null;
    /**
     * 进度条
     */
    private SeekBar mVideoSeekBar = null;
    /**
     * 视频总时间
     */
    private long mVideoDuration = -1;

    private static long mPosition = 0;
    /**
     * 视频的路径或者url
     */
    private String mVideoPath = "";
    private boolean mIsVideoSizeKnown = false;
    private boolean mIsVideoReadyToBePlayed = false;

    /**
     * 定时器， 用于更新进度条
     */
    private Timer mTimer = null;
    /**
     * 更新进度条的消息
     */
    private final int UPDATE_SEEKBAR_MSG = 123;
    /**
     * 每个一秒更新
     */
    private final int UPDATE_INTERVAL = 1000;

    /**
     * 
     */
    private final int HIDE_SEEKBAR_MSG = 456;

    private final int HIDE_MSG_DELAY = 3000;
    /**
     * 控制器描述符
     */
    private final String UMENG_DESCRIPTION = "com.umeng.share";
    /**
     * 友盟分享SDK控制器
     */
    private UMSocialService mSocialController = UMServiceFactory
            .getUMSocialService(UMENG_DESCRIPTION, RequestType.SOCIAL);
    /**
     * 摇一摇控制器, 如果您在使用摇一摇功能时已经声明了UMSocialService对象，
     * 则摇一摇的描述符应与UMSocialService对象的一致,即UMENG_DESCRIPTION.
     */
    private UMShakeService mShakeController = UMShakeServiceFactory
            .getShakeService(UMENG_DESCRIPTION);

    /**
     * (非 Javadoc)
     * 
     * @Title: onCreate
     * @Description: onCreate
     * @param icicle
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (!LibsChecker.checkVitamioLibs(this)) {
            Log.d(TAG, "#### lib载入失败.");
            return;
        }
        this.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        // 初始化SurfaceView
        initSurfaceView();
        // 配置SSO, 并且要覆写onActivityResult方法进行回调，否则无法授权成功
        configSocialSso();
    }

    /**
     * @Title: initSurfaceView
     * @Description: 初始化SurfaceView
     * @throws
     */
    private void initSurfaceView() {
        mSurfaceView = (SurfaceView) findViewById(R.id.surface);
        mSurfaceView.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                showSeekbar();
            }
        });
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setFormat(PixelFormat.RGBA_8888);

        mPlayButton = (ImageButton) findViewById(R.id.play_btn);
        mPlayButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mMediaPlayer != null) {
                    if (mMediaPlayer.isPlaying()) {
                        pause();
                    } else {
                        play();
                    }
                }
            }
        });

        mSeekbarLayout = (LinearLayout) findViewById(R.id.seekbar_layout);
        // 初始化进度条
        initSeekBar();
    }

    /**
     * @Title: configSocialSso
     * @Description: 配置SSO授权, 并且要覆写onActivityResult方法进行回调，否则无法授权成功
     * @throws
     */
    private void configSocialSso() {

        // 添加微信支持
        mSocialController.getConfig().supportWXPlatform(MainActivity.this,
                "wx9f162ffbf5731350", "http://www.umeng.com/social");
        // 添加QQ平台， 并且设置SSO授权
        mSocialController.getConfig().supportQQPlatform(MainActivity.this,
                "http://www.umeng.com/social");
        // 添加QQ空间的sso授权
        mSocialController.getConfig().setSsoHandler(
                new QZoneSsoHandler(MainActivity.this));
        // 添加腾讯微博的sso授权
        mSocialController.getConfig().setSsoHandler(new TencentWBSsoHandler());
        // 添加新浪微博的sso授权
        mSocialController.getConfig().setSsoHandler(new SinaSsoHandler());
    }

    /**
     * @Title: initSeekBar
     * @Description: 初始化进度条
     * @throws
     */
    private void initSeekBar() {
        mVideoSeekBar = (SeekBar) findViewById(R.id.video_seekbar);
        mVideoSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

            /**
             * @Description: 用户拖动进度条
             * @param seekBar
             */
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                int duration = (int) mVideoDuration;
                int position = progress * duration / 100;
                if (mMediaPlayer != null) {
                    mMediaPlayer.seekTo(position);
                }
                updateUI(position);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
            }
        });
        showSeekbar();
    }

    /**
     * 通过定时器和Handler来更新进度条
     */
    private class VideoTimerTask extends TimerTask {

        @Override
        public void run() {
            if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
                Message msg = Message.obtain(mHandler);
                msg.what = UPDATE_SEEKBAR_MSG;
                msg.obj = mMediaPlayer.getCurrentPosition();
                mHandler.sendMessage(msg);
            }
        }

    };

    /**
     * 处理各种消息
     */
    private final Handler mHandler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            if (msg.what == UPDATE_SEEKBAR_MSG) {
                long millseconds = (Long) msg.obj;
                // 更新UI
                updateUI((int) millseconds);
            } else if (msg.what == HIDE_SEEKBAR_MSG) {
                mSeekbarLayout.setVisibility(View.GONE);
            }
        };
    };

    /**
     * @Title: updateUI
     * @Description: 更新播放进度条和时间
     * @param millseconds
     * @throws
     */
    private void updateUI(int millseconds) {
        float total = (float) mVideoDuration;
        int progress = (int) ((millseconds / total) * 100);
        mVideoSeekBar.setProgress(progress);
    }

    /**
     * (非 Javadoc)
     * 
     * @Title: onResume
     * @Description: 注册摇一摇功能, 注册操作最好放在onResume方法
     * @see android.app.Activity#onResume()
     */
    @Override
    protected void onResume() {
        super.onResume();
        // 注册摇一摇截屏分享
        registerShake();
        if (mTimer == null) {
            mTimer = new Timer();
            mTimer.schedule(new VideoTimerTask(), 0, UPDATE_INTERVAL);
        }
    }

    /**
     * (非 Javadoc)
     * 
     * @Title: onStop
     * @Description: 注销摇一摇功能
     * @see android.app.Activity#onStop()
     */
    @Override
    protected void onStop() {
        super.onStop();
        if (mTimer != null) {
            mTimer.cancel();
            mTimer.purge();
            mTimer = null;
        }
        mHandler.removeMessages(UPDATE_SEEKBAR_MSG);
        // 注销摇一摇传感器
        mShakeController.unregisterShakeListener(MainActivity.this);
    }

    /**
     * (非 Javadoc)
     * 
     * @Title: onActivityResult
     * @Description: SSO授权必须覆写该方法进行回调
     * @param requestCode
     * @param resultCode
     * @param data
     * @see android.app.Activity#onActivityResult(int, int,
     *      android.content.Intent)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        // 通过requestCode获取ssoHandler
        UMSsoHandler ssoHandler = mSocialController.getConfig().getSsoHandler(
                requestCode);
        if (ssoHandler != null) {
            // 回调给umeng sdk
            ssoHandler.authorizeCallBack(requestCode, resultCode, data);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * @Title: registerShake
     * @Description: 注册摇一摇截屏分享
     * @throws
     */
    private void registerShake() {

        // 最多支持5个平台， 如果多于5个，则取前5个
        List<SHARE_MEDIA> platforms = new ArrayList<SHARE_MEDIA>();
        platforms.add(SHARE_MEDIA.SINA);
        platforms.add(SHARE_MEDIA.QZONE);
        platforms.add(SHARE_MEDIA.WEIXIN);
        platforms.add(SHARE_MEDIA.WEIXIN_CIRCLE);
        platforms.add(SHARE_MEDIA.QQ);
        // 设置摇一摇分享的文字内容
        mShakeController.setShareContent("精彩瞬间，摇摇分享 -- 来自友盟社会化组件." + new Date().toString());
        // 注册摇一摇截屏分享， 自定义的VitamioAdapter,
        mShakeController.registerShakeListender(MainActivity.this,
                new VitamioAdapter(), platforms, new VitamioListener());
    }

    /**
     * @ClassName: VitamioAdapter
     * @Description: 自定义的截屏适配器，返回当前视频的图像，不包含界面上的其他view截图.
     * @author Honghui He
     */
    private class VitamioAdapter extends UMBaseAdapter {

        /**
         * (非 Javadoc)
         * 
         * @Title: getBitmap
         * @Description: 覆写该方法， 并且返回视频图像
         * @return
         * @see com.umeng.scrshot.adapter.UMBaseAdapter#getBitmap()
         */
        @Override
        public Bitmap getBitmap() {
            if (mMediaPlayer != null) {
                Bitmap bmp = mMediaPlayer.getCurrentFrame();
                if (bmp == null) {
                    return null;
                }

                // 使用Vitamio获取高清视频截图，分享到社交平台时会出现条纹.
                // 如果您的截图分享没有任何问题， 则不需要做这一步. ( 添加这一步会造成摇一摇动画不流畅 )
                Bitmap scrshot = compressBitmap(bmp);
                return scrshot;

            }
            return null;
        }
    }

    /**
     * 使用Vitamio获取截图，分享到社交平台时会出现条纹，目前不知道是什么原因. 如果您的截图分享没有任何问题， 则不需要做这一步.
     * 
     * @param bmp
     * @return
     */
    @SuppressLint("NewApi")
    private Bitmap compressBitmap(Bitmap bmp) {

        // 将Vitamio获取截图压缩到outStream
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        // 考虑到用户的网络速度,建议将图片压缩至70kb以下，保证图片上传的成功率.
        bmp.compress(CompressFormat.JPEG, 30, outStream);
        byte[] data = outStream.toByteArray();
        if (data != null && data.length > 0) {
            Log.d(TAG, "### 图片大小 : " + data.length / 1024 + " KB");
        }
        // 再从outStream解析一张图片
        Bitmap scrshot = BitmapFactory.decodeByteArray(data, 0, data.length);
        return scrshot;
    }

    /**
     * @ClassName: VitamioListener
     * @Description: 摇一摇监听器，包含用户摇一摇完成、分享开始、分享完成、按钮点击的监听
     * @author Honghui He
     */
    private class VitamioListener implements OnSensorListener {

        @Override
        public void onComplete(SHARE_MEDIA arg0, int code, SocializeEntity arg2) {
            if (code == StatusCode.ST_CODE_SUCCESSED) {
                Toast.makeText(MainActivity.this, "分享成功", Toast.LENGTH_SHORT)
                        .show();
            } else {
                Toast.makeText(MainActivity.this, " 抱歉,您的网络不给力,请重试...", Toast.LENGTH_SHORT)
                        .show();
            }
        }

        @Override
        public void onStart() {
        }

        @Override
        public void onActionComplete(SensorEvent arg0) {
            Toast.makeText(MainActivity.this, "用户摇一摇", Toast.LENGTH_SHORT)
                    .show();
            // 暂停视频
            pause();
        }

        @Override
        public void onButtonClick(WhitchButton button) {
            if (button == WhitchButton.BUTTON_SHARE) {
                Toast.makeText(MainActivity.this, "用户点击分享按钮",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "用户点击取消按钮",
                        Toast.LENGTH_SHORT).show();
            }
            // 重新开始
            play();
        }
    }

    /**
     * @Title: playVideo
     * @Description: 播放视频， 初始化视频播放器
     * @throws
     */
    private void playVideo() {
        cleanUp();
        try {
            // 视频的url地址, 也可以是本地的视频路径
            mVideoPath = "http://blog.umeng.com/images/video.mp4";
            // Create a new media player and set the listeners
            mMediaPlayer = new MediaPlayer(this);
            mMediaPlayer.setDataSource(mVideoPath);
            mMediaPlayer.setDisplay(mSurfaceHolder);
            mMediaPlayer.prepare();
            mMediaPlayer.setOnBufferingUpdateListener(this);
            mMediaPlayer.setOnCompletionListener(this);
            mMediaPlayer.setOnPreparedListener(this);
            mMediaPlayer.setOnVideoSizeChangedListener(this);
            mMediaPlayer.getMetadata();
            mMediaPlayer.seekTo(mPosition);
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
        } catch (Exception e) {
            Log.e(TAG, "error: " + e.getMessage(), e);
        }
    }

    /**
     * @Title: play
     * @Description: 播放视频
     * @throws
     */
    private void play() {
        Log.d(TAG, "### Click play button");
        mMediaPlayer.start();
        mPlayButton.setBackgroundResource(R.drawable.pause);
    }

    /**
     * 
     */
    /**
     * @Title: pause
     * @Description: 暂停视频
     * @throws
     */
    private void pause() {
        Log.d(TAG, "### Click pause button");
        mMediaPlayer.pause();
        mPlayButton.setBackgroundResource(R.drawable.play);
    }

    /**
     * @Title: startVideoPlayback
     * @Description: 播放视频
     * @throws
     */
    private void startVideoPlayback() {
        Log.v(TAG, "startVideoPlayback");
        mSurfaceHolder.setFixedSize(mVideoWidth, mVideoHeight);
        mSurfaceHolder.setKeepScreenOn(true);
        play();
        if (mTimer == null) {
            mTimer = new Timer();
            // 设置定时器
            mTimer.schedule(new VideoTimerTask(), 0, UPDATE_INTERVAL);
        }
        mVideoSeekBar.setVisibility(View.VISIBLE);
        mVideoDuration = mMediaPlayer.getDuration();
    }

    /**
     * @Title: releaseMediaPlayer
     * @Description: 释放mMediaPlayer对象
     * @throws
     */
    private void releaseMediaPlayer() {
        if (mMediaPlayer != null) {
            mPosition = mMediaPlayer.getCurrentPosition();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    /**
     * @Title: doCleanUp
     * @Description: 清空状态
     * @throws
     */
    private void cleanUp() {
        mVideoWidth = 0;
        mVideoHeight = 0;
        mIsVideoReadyToBePlayed = false;
        mIsVideoSizeKnown = false;
    }

    /**
     * (非 Javadoc)
     * 
     * @Title: onBufferingUpdate
     * @Description:
     * @param arg0
     * @param percent
     * @see io.vov.vitamio.MediaPlayer.OnBufferingUpdateListener#onBufferingUpdate(io.vov.vitamio.MediaPlayer,
     *      int)
     */
    public void onBufferingUpdate(MediaPlayer arg0, int percent) {
        // Log.d(TAG, "onBufferingUpdate percent:" + percent);

    }

    public void onCompletion(MediaPlayer arg0) {
        Log.d(TAG, "视频播放结束");
        pause();
    }

    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        Log.v(TAG, "onVideoSizeChanged called");
        if (width == 0 || height == 0) {
            Log.e(TAG, "invalid video width(" + width + ") or height(" + height
                    + ")");
            return;
        }
        mIsVideoSizeKnown = true;
        mVideoWidth = width;
        mVideoHeight = height;
        if (mIsVideoReadyToBePlayed && mIsVideoSizeKnown) {
            startVideoPlayback();
        }
    }

    public void onPrepared(MediaPlayer mediaplayer) {
        Log.d(TAG, "onPrepared called");
        mIsVideoReadyToBePlayed = true;
        if (mIsVideoReadyToBePlayed && mIsVideoSizeKnown) {
            startVideoPlayback();
        }
    }

    public void surfaceChanged(SurfaceHolder surfaceholder, int i, int j, int k) {
        Log.d(TAG, "### surfaceChanged called");

    }

    public void surfaceDestroyed(SurfaceHolder surfaceholder) {
        Log.d(TAG, "### surfaceDestroyed called");
    }

    /**
     * (非 Javadoc)
     * 
     * @Title: surfaceCreated
     * @Description: SurfaceView创建以后开始播放视频
     * @param holder
     * @see android.view.SurfaceHolder.Callback#surfaceCreated(android.view.SurfaceHolder)
     */
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "#### surfaceCreated called");
        playVideo();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // releaseMediaPlayer();
        // cleanUp();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseMediaPlayer();
        cleanUp();
    }

    /**
     * @Title: hideSeekbar
     * @Description: 用户点击视频时，显示进度条， 三秒后隐藏
     * @throws
     */
    private void showSeekbar() {
        mSeekbarLayout.setVisibility(View.VISIBLE);
        mHandler.removeMessages(HIDE_SEEKBAR_MSG);
        // 用户点击视频， 3秒后隐藏进度条
        Message msg = mHandler.obtainMessage(HIDE_SEEKBAR_MSG);
        msg.what = HIDE_SEEKBAR_MSG;
        mHandler.sendMessageDelayed(msg, HIDE_MSG_DELAY);
    }
}
