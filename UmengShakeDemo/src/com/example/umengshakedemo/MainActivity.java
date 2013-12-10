package com.example.umengshakedemo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.SensorEvent;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import com.umeng.scrshot.adapter.UMBaseAdapter;
import com.umeng.socialize.bean.SHARE_MEDIA;
import com.umeng.socialize.bean.SocializeEntity;
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

import java.util.ArrayList;
import java.util.List;

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
	/**
	 * 视频的路径或者url
	 */
	private String mVideoPath = "";
	private boolean mIsVideoSizeKnown = false;
	private boolean mIsVideoReadyToBePlayed = false;

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
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		if (!LibsChecker.checkVitamioLibs(this)) {
			Log.d(TAG, "#### lib载入失败.");
			return;
		}
		setContentView(R.layout.activity_main);

		mSurfaceView = (SurfaceView) findViewById(R.id.surface);
		mSurfaceHolder = mSurfaceView.getHolder();
		mSurfaceHolder.addCallback(this);
		mSurfaceHolder.setFormat(PixelFormat.RGBA_8888);

		// 配置SSO, 并且要覆写onActivityResult方法进行回调，否则无法授权成功
		configSocialSso();
	}

	/**
	 * @Title: configSocialSso
	 * @Description: 配置SSO授权, 并且要覆写onActivityResult方法进行回调，否则无法授权成功
	 * @throws
	 */
	private void configSocialSso() {
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
		platforms.add(SHARE_MEDIA.QQ);
		platforms.add(SHARE_MEDIA.TENCENT);
		platforms.add(SHARE_MEDIA.RENREN);
		// 设置摇一摇分享的文字内容
		mShakeController.setShareContent("精彩瞬间，摇摇分享  -- 来自友盟社会化组件.");
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
				return mMediaPlayer.getCurrentFrame();
			}
			return null;
		}
	}

	/**
	 * @ClassName: VitamioListener
	 * @Description: 摇一摇监听器，包含用户摇一摇完成、分享开始、分享完成、按钮点击的监听
	 * @author Honghui He
	 */
	private class VitamioListener implements OnSensorListener {

		@Override
		public void onComplete(SHARE_MEDIA arg0, int arg1, SocializeEntity arg2) {
			Toast.makeText(MainActivity.this, "分享成功", Toast.LENGTH_SHORT)
					.show();
		}

		@Override
		public void onStart() {
		}

		@Override
		public void onActionComplete(SensorEvent arg0) {
			Toast.makeText(MainActivity.this, "用户摇一摇", Toast.LENGTH_SHORT)
					.show();
			// 暂停视频
			mMediaPlayer.pause();
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
			mMediaPlayer.start();
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
			setVolumeControlStream(AudioManager.STREAM_MUSIC);

		} catch (Exception e) {
			Log.e(TAG, "error: " + e.getMessage(), e);
		}
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
		mMediaPlayer.start();
		Toast.makeText(MainActivity.this, "摇一摇手机，分享精彩瞬间吧!!!", Toast.LENGTH_LONG)
				.show();
	}

	/**
	 * @Title: releaseMediaPlayer
	 * @Description: 释放mMediaPlayer对象
	 * @throws
	 */
	private void releaseMediaPlayer() {
		if (mMediaPlayer != null) {
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
		Log.d(TAG, "onBufferingUpdate percent:" + percent);

	}

	public void onCompletion(MediaPlayer arg0) {
		Log.d(TAG, "onCompletion called");
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
		Log.d(TAG, "surfaceChanged called");

	}

	public void surfaceDestroyed(SurfaceHolder surfaceholder) {
		Log.d(TAG, "surfaceDestroyed called");
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
		Log.d(TAG, "surfaceCreated called");
		playVideo();
	}

	@Override
	protected void onPause() {
		super.onPause();
		releaseMediaPlayer();
		cleanUp();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		releaseMediaPlayer();
		cleanUp();
	}

}
