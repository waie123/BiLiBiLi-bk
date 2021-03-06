package com.vanco.abplayer;
/*
import com.umeng.content.UmengAction;
 * Copyright (C) 2012 YIXIA.COM
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.vov.vitamio.widget.OutlineTextView;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.DataFormatException;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.helper.HttpConnection.Response;

import master.flame.danmaku.controller.IDanmakuView;
import master.flame.danmaku.danmaku.loader.ILoader;
import master.flame.danmaku.danmaku.loader.IllegalDataException;
import master.flame.danmaku.danmaku.loader.android.DanmakuLoaderFactory;
import master.flame.danmaku.danmaku.model.android.DanmakuGlobalConfig;
import master.flame.danmaku.danmaku.model.android.Danmakus;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import master.flame.danmaku.danmaku.parser.IDataSource;
import master.flame.danmaku.danmaku.parser.android.BiliDanmukuParser;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.AnimationDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.minisea.bilibli.R;
import com.umeng.content.UmengAction;
import com.vanco.abplayer.util.ArrayUtils;
import com.vanco.abplayer.util.CompressionTools;
import com.vanco.abplayer.util.DeviceUtils;
import com.vanco.abplayer.util.HttpUtil;
import com.vanco.abplayer.util.ImageUtils;
import com.vanco.abplayer.util.Logger;
import com.vanco.abplayer.util.MediaUtils;
import com.vanco.abplayer.util.PreferenceUtils;
import com.vanco.abplayer.util.StringUtils;
import com.vanco.abplayer.util.ToastUtils;
import com.vanco.abplayer.view.ApplicationUtils;
import com.vanco.abplayer.view.MediaController;
import com.vanco.abplayer.view.PlayerService;
import com.vanco.abplayer.view.VP;
import com.vanco.abplayer.view.VideoView;
import com.yixia.zi.utils.FileUtils;
import com.yixia.zi.utils.Log;

@SuppressLint("HandlerLeak")
public class BiliVideoViewActivity extends Activity implements
		MediaController.MediaPlayerControl, VideoView.SurfaceCallback {
	private Handler handler = new Handler() {
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			if (msg.what == 1) {
				// todo something....
//				AdPublic.addAd(BiliVideoViewActivity.this);
			}
		}
	};
	private Timer timer = new Timer(true);
	private TimerTask task = new TimerTask() {
		public void run() {
			Message msg = new Message();
			msg.what = 1;
			handler.sendMessage(msg);
		}
	};
	public static final int RESULT_FAILED = -7;

	private static final IntentFilter USER_PRESENT_FILTER = new IntentFilter(
			Intent.ACTION_USER_PRESENT);
	private static final IntentFilter SCREEN_FILTER = new IntentFilter(
			Intent.ACTION_SCREEN_ON);
	private static final IntentFilter HEADSET_FILTER = new IntentFilter(
			Intent.ACTION_HEADSET_PLUG);
	private static final IntentFilter BATTERY_FILTER = new IntentFilter(
			Intent.ACTION_BATTERY_CHANGED);

	private IDanmakuView mDanmakuView;//????????????
	private BaseDanmakuParser mParser;
	private String danmakuPath;
	private String av;
	private String page;
	private boolean isload = false;
	private boolean isfirst = true;
	private View startVideo;
	private TextView startVideoInfo;
	private String startText = "??????????????????...";
	private ImageView biliAnim;
	private AnimationDrawable anim;
	
	
	private boolean mCreated = false;
	private boolean mNeedLock;
	private String mDisplayName;
	private String mBatteryLevel;
	private boolean mFromStart;
	private int mLoopCount;
	private boolean mSaveUri;
	private int mParentId;
	private float mStartPos;
	private boolean mEnd = false;
	private String mSubPath;
	private boolean mSubShown;
	private View mViewRoot;
	private VideoView mVideoView;
	private View mVideoLoadingLayout;
	private TextView mVideoLoadingText;
	private View mSubtitleContainer;
	private OutlineTextView mSubtitleText;
	private ImageView mSubtitleImage;
	private Uri mUri;
	private ScreenReceiver mScreenReceiver;
	private HeadsetPlugReceiver mHeadsetPlugReceiver;
	private UserPresentReceiver mUserPresentReceiver;
	private BatteryReceiver mBatteryReceiver;
	private boolean mReceiverRegistered = false;
	private boolean mHeadsetPlaying = false;
	private boolean mCloseComplete = false;
	private boolean mIsHWCodec = false;

	private MediaController mMediaController;
	private PlayerService vPlayer;
	private ServiceConnection vPlayerServiceConnection;
	// private Animation mLoadingAnimation;
	// private View mLoadingProgressView;

	static {
		SCREEN_FILTER.addAction(Intent.ACTION_SCREEN_OFF);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		if (!io.vov.vitamio.LibsChecker.checkVitamioLibs(this))
			return;

		vPlayerServiceConnection = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				vPlayer = ((PlayerService.LocalBinder) service).getService();
				mServiceConnected = true;
				if (mSurfaceCreated)
					vPlayerHandler.sendEmptyMessage(OPEN_FILE);
			}

			@Override
			public void onServiceDisconnected(ComponentName name) {
				vPlayer = null;
				mServiceConnected = false;
			}
		};

		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		parseIntent(getIntent());
		loadView(R.layout.activity_video);
		manageReceivers();
		
	    findViews();

		mCreated = true;
		startText = startText + "????????????\n??????????????????...????????????\n??????????????????...";
		startVideoInfo.setText(startText);
		new VideoViewInitTask().execute();
//		AdPublic.addAd(BiliVideoViewActivity.this);
		// ???????????????
// 	timer.schedule(task, 0, AdPublic.time);
	}
	
    private void findViews(){
    	 // DanmakuView
        mDanmakuView = (IDanmakuView) findViewById(R.id.sv_danmaku);
        startVideo = (View) findViewById(R.id.video_start);
        startVideo.setVisibility(View.VISIBLE);
        startVideoInfo = (TextView) findViewById(R.id.video_start_info);
        biliAnim = (ImageView) findViewById(R.id.bili_anim);
        anim = (AnimationDrawable) biliAnim.getBackground();
        anim.start();
        DanmakuGlobalConfig.DEFAULT.setDanmakuStyle(DanmakuGlobalConfig.DANMAKU_STYLE_STROKEN, 3).setDuplicateMergingEnabled(false);
//        if (mDanmakuView != null) {
//            //mParser = createParser(this.getResources().openRawResource(R.raw.comments));
//            mParser = createParser(openFileInput(danmakuPath));
//            //mParser = createParser(danmakuPath);
//            mDanmakuView.setCallback(new Callback() {
//
//                @Override
//                public void updateTimer(DanmakuTimer timer) {
//
//                }
//
//                @Override
//                public void prepared() {
//                	isload = true;
//                	startPlayer();
//                    //mDanmakuView.start();
//                }
//            });
//            mDanmakuView.prepare(mParser);
//
//            mDanmakuView.showFPS(true);
//            mDanmakuView.enableDanmakuDrawingCache(true);
//            ((View) mDanmakuView).setOnClickListener(new View.OnClickListener() {
//
//                @Override
//                public void onClick(View view) {
//                    mMediaController.setVisibility(View.VISIBLE);
//                }
//            });
//        }

		
	}

	private BaseDanmakuParser createParser(InputStream stream) {
        
        if(stream==null){
            return new BaseDanmakuParser() {
                
                @Override
                protected Danmakus parse() {
                    return new Danmakus();
                }
            };
        }
            
        
        ILoader loader = DanmakuLoaderFactory.create(DanmakuLoaderFactory.TAG_BILI);

        try {
            loader.load(stream);
        } catch (IllegalDataException e) {
            e.printStackTrace();
        }
        BaseDanmakuParser parser = new BiliDanmukuParser();
        IDataSource<?> dataSource = loader.getDataSource();
        parser.load(dataSource);
        return parser;
    }
	
private BaseDanmakuParser createParser(String uri) {
	   InputStream stream = null;
        if(uri==null){
            return new BaseDanmakuParser() {
                
                @Override
                protected Danmakus parse() {
                    return new Danmakus();
                }
            };
        }
        try {
			Response rsp = (Response) Jsoup.connect(uri).execute();
			stream = new ByteArrayInputStream(CompressionTools.decompressXML(rsp.bodyAsBytes()));
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (DataFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}    
        
        ILoader loader = DanmakuLoaderFactory.create(DanmakuLoaderFactory.TAG_BILI);

        try {
            loader.load(stream);
        } catch (IllegalDataException e) {
            e.printStackTrace();
        }
        BaseDanmakuParser parser = new BiliDanmukuParser();
        IDataSource<?> dataSource = loader.getDataSource();
        parser.load(dataSource);
        return parser;
    }

	private void attachMediaController() {
		if (mMediaController != null) {
			mNeedLock = mMediaController.isLocked();
			mMediaController.release();
		}
		mMediaController = new MediaController(this, mNeedLock);
		mMediaController.setMediaPlayer(this);
		mMediaController.setAnchorView(mVideoView.getRootView());
		setFileName();
		setBatteryLevel();
	}

	@Override
	public void onStart() {
		super.onStart();
		if (!mCreated)
			return;
//		Intent serviceIntent = new Intent(this, PlayerService.class);
//		serviceIntent.putExtra("isHWCodec", mIsHWCodec);
//		bindService(serviceIntent, vPlayerServiceConnection,
//				Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onResume() {
		super.onResume();
		if (!mCreated)
			return;
		if (isInitialized()) {
			KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
			if (!keyguardManager.inKeyguardRestrictedInputMode()) {
				startPlayer();
			}
		} else {
			if (mCloseComplete) {
				reOpen();
			}
		}
        if (mDanmakuView != null && mDanmakuView.isPrepared() && mDanmakuView.isPaused()) {
            mDanmakuView.resume();
        }
	}

	@Override
	public void onPause() {
		super.onPause();
		if (!mCreated)
			return;
		if (isInitialized()) {
			//savePosition();
			if (vPlayer != null && vPlayer.isPlaying()) {
				stopPlayer();
			}
		}
        if (mDanmakuView != null && mDanmakuView.isPrepared()) {
            mDanmakuView.pause();
        }
	}

	@Override
	public void onStop() {
		super.onStop();
		if (!mCreated)
			return;
		if (isInitialized()) {
			vPlayer.releaseSurface();
		}
		if (mServiceConnected) {
			unbindService(vPlayerServiceConnection);
			mServiceConnected = false;
		}
//        if (mDanmakuView != null) {
//            // dont forget release!
//            mDanmakuView.release();
//            mDanmakuView = null;
//        }

	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (!mCreated)
			return;
		manageReceivers();
		if (isInitialized() && !vPlayer.isPlaying())
			release();
		if (mMediaController != null)
			mMediaController.release();
        if (mDanmakuView != null) {
            // dont forget release!
            mDanmakuView.release();
            mDanmakuView = null;
        }
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		if (isInitialized()) {
			setVideoLayout();
			attachMediaController();
		}

		super.onConfigurationChanged(newConfig);
	}

	@Override
	public void onBackPressed() {
//		super.onBackPressed();
//        if (mDanmakuView != null) {
//            // dont forget release!
//            mDanmakuView.release();
//            mDanmakuView = null;
//        }
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		// http://code.google.com/p/android/issues/detail?id=19917
		outState.putString("WORKAROUND_FOR_BUG_19917_KEY",
				"WORKAROUND_FOR_BUG_19917_VALUE");
		super.onSaveInstanceState(outState);
	}

	@Override
	public void showMenu() {

	}

	private void loadView(int id) {
		setContentView(id);
		getWindow().setBackgroundDrawable(null);
		mViewRoot = findViewById(R.id.video_root);
		mVideoView = (VideoView) findViewById(R.id.video);
		mVideoView.initialize(this, this, mIsHWCodec);
		mSubtitleContainer = findViewById(R.id.subtitle_container);
		mSubtitleText = (OutlineTextView) findViewById(R.id.subtitle_text);
		mSubtitleImage = (ImageView) findViewById(R.id.subtitle_image);
		mVideoLoadingText = (TextView) findViewById(R.id.video_loading_text);
		mVideoLoadingLayout = findViewById(R.id.video_loading);
		// mLoadingProgressView =
		// mVideoLoadingLayout.findViewById(R.id.video_loading_progress);

		// mLoadingAnimation = AnimationUtils.loadAnimation(VideoActivity.this,
		// R.anim.loading_rotate);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	private void parseIntent(Intent i) {
//		Uri dat = IntentHelper.getIntentUri(i);
//		if (dat == null)
//			resultFinish(RESULT_FAILED);
//
//		String datString = dat.toString();
//		if (!datString.equals(dat.toString()))
//			dat = Uri.parse(datString);
//
//		mUri = dat;
//		danmakuPath = "http://comment.bilibili.com/"+i.getStringExtra("CID")+".xml";
//        Logger.d(danmakuPath);
		av = i.getStringExtra("av");
		page = i.getStringExtra("page");
		Logger.d("----->"+av+"/"+page);

		mNeedLock = i.getBooleanExtra("lockScreen", false);
		mDisplayName = i.getStringExtra("displayName");
		mFromStart = i.getBooleanExtra("fromStart", false);
		mSaveUri = i.getBooleanExtra("saveUri", true);
		mStartPos = i.getFloatExtra("startPosition", -1.0f);
		mLoopCount = i.getIntExtra("loopCount", 1);
		mParentId = i.getIntExtra("parentId", 0);
		mSubPath = i.getStringExtra("subPath");
		mSubShown = i.getBooleanExtra("subShown", true);
		mIsHWCodec = i.getBooleanExtra("hwCodec", false);
		Log.i("L: %b, N: %s, S: %b, P: %f, LP: %d", mNeedLock, mDisplayName,
				mFromStart, mStartPos, mLoopCount);
	}

	private void manageReceivers() {
		if (!mReceiverRegistered) {
			mScreenReceiver = new ScreenReceiver();
			registerReceiver(mScreenReceiver, SCREEN_FILTER);
			mUserPresentReceiver = new UserPresentReceiver();
			registerReceiver(mUserPresentReceiver, USER_PRESENT_FILTER);
			mBatteryReceiver = new BatteryReceiver();
			registerReceiver(mBatteryReceiver, BATTERY_FILTER);
			mHeadsetPlugReceiver = new HeadsetPlugReceiver();
			registerReceiver(mHeadsetPlugReceiver, HEADSET_FILTER);
			mReceiverRegistered = true;
		} else {
			try {
				if (mScreenReceiver != null)
					unregisterReceiver(mScreenReceiver);
				if (mUserPresentReceiver != null)
					unregisterReceiver(mUserPresentReceiver);
				if (mHeadsetPlugReceiver != null)
					unregisterReceiver(mHeadsetPlugReceiver);
				if (mBatteryReceiver != null)
					unregisterReceiver(mBatteryReceiver);
			} catch (IllegalArgumentException e) {
			}
			mReceiverRegistered = false;
		}
	}

	private void setFileName() {
		if (mUri != null) {
			String name = null;
			if (mUri.getScheme() == null || mUri.getScheme().equals("file"))
				name = FileUtils.getName(mUri.toString());
			else
				name = mUri.getLastPathSegment();
			if (name == null)
				name = "null";
			if (mDisplayName == null)
				mDisplayName = name;
			mMediaController.setFileName(mDisplayName);
		}
	}

	private void applyResult(int resultCode) {
		vPlayerHandler.removeMessages(BUFFER_PROGRESS);
		Intent i = new Intent();
		i.putExtra("filePath", mUri.toString());
		if (isInitialized()) {
			i.putExtra("position", (double) vPlayer.getCurrentPosition()
					/ vPlayer.getDuration());
			i.putExtra("duration", vPlayer.getDuration());
			//savePosition();
		}
		switch (resultCode) {
		case RESULT_FAILED:
			ToastUtils.showLongToast(R.string.video_cannot_play);
			break;
		case RESULT_CANCELED:
		case RESULT_OK:
			break;
		}
		setResult(resultCode, i);
	}

	private void resultFinish(int resultCode) {
		applyResult(resultCode);
		if (DeviceUtils.hasICS() && resultCode != RESULT_FAILED) {
			android.os.Process.killProcess(android.os.Process.myPid());
		} else {
			finish();
		}
	}

	private void release() {
		if (vPlayer != null) {
			if (DeviceUtils.hasICS()) {
				android.os.Process.killProcess(android.os.Process.myPid());
			} else {
				vPlayer.release();
				vPlayer.releaseContext();
			}
		}
	}

	private void reOpen(Uri path, String name, boolean fromStart) {
		if (isInitialized()) {
			//savePosition();
			vPlayer.release();
			vPlayer.releaseContext();
		}
		Intent i = getIntent();
		if (mMediaController != null)
			i.putExtra("lockScreen", mMediaController.isLocked());
		i.putExtra("startPosition", PreferenceUtils.getFloat(mUri
				+ VP.SESSION_LAST_POSITION_SUFIX, 7.7f));
		i.putExtra("fromStart", fromStart);
		i.putExtra("displayName", name);
		i.setData(path);
		parseIntent(i);
		mUri = path;
		if (mViewRoot != null)
			mViewRoot.invalidate();
		if (mOpened != null)
			mOpened.set(false);
	}

	public void reOpen() {
		reOpen(mUri, mDisplayName, false);
	}

	protected void startPlayer() {
		if (isInitialized() && mScreenReceiver.screenOn
				&& !vPlayer.isBuffering()&&isload) {
			Log.i("VideoActivity#startPlayer");
			if (!vPlayer.isPlaying()) {
				vPlayer.start();
				if(mDanmakuView.isPaused())
				mDanmakuView.resume();
				else
				mDanmakuView.start();
			}
		}
	}

	protected void stopPlayer() {
		if (isInitialized()) {
			vPlayer.stop();
			if(mDanmakuView != null){
				if(mDanmakuView.isPrepared()&&mDanmakuView.isShown());
				mDanmakuView.pause();
			}
		}
	}

	private void setBatteryLevel() {
		if (mMediaController != null)
			mMediaController.setBatteryLevel(mBatteryLevel);
	}

	private class BatteryReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
			int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
			int percent = scale > 0 ? level * 100 / scale : 0;
			if (percent > 100)
				percent = 100;
			mBatteryLevel = String.valueOf(percent) + "%";
			setBatteryLevel();
		}
	}

	private class UserPresentReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (isRootActivity()) {
				startPlayer();
			}
		}
	}

	private boolean isRootActivity() {
		return ApplicationUtils.isTopActivity(getApplicationContext(),
				getClass().getName());
		// ActivityManager activity = (ActivityManager)
		// getSystemService(Context.ACTIVITY_SERVICE);
		// return
		// activity.getRunningTasks(1).get(0).topActivity.flattenToString().endsWith("io.vov.vitamio.activity.VideoActivity");
	}

	public class HeadsetPlugReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent != null && intent.hasExtra("state")) {
				int state = intent.getIntExtra("state", -1);
				if (state == 0) {
					mHeadsetPlaying = isPlaying();
					stopPlayer();
				} else if (state == 1) {
					if (mHeadsetPlaying)
						startPlayer();
				}
			}
		};
	}

	private class ScreenReceiver extends BroadcastReceiver {
		private boolean screenOn = true;

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
				screenOn = false;
				stopPlayer();
			} else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
				screenOn = true;
			}
		}
	}

	private void loadVPlayerPrefs() {
		if (!isInitialized())
			return;
		vPlayer.setBuffer(VP.DEFAULT_BUF_SIZE);
		vPlayer.setVideoQuality(VP.DEFAULT_VIDEO_QUALITY);
		vPlayer.setDeinterlace(VP.DEFAULT_DEINTERLACE);
		vPlayer.setVolume(VP.DEFAULT_STEREO_VOLUME, VP.DEFAULT_STEREO_VOLUME);
		vPlayer.setSubEncoding(VP.DEFAULT_SUB_ENCODING);
		MarginLayoutParams lp = (MarginLayoutParams) mSubtitleContainer
				.getLayoutParams();
		lp.bottomMargin = (int) VP.DEFAULT_SUB_POS;
		mSubtitleContainer.setLayoutParams(lp);
		vPlayer.setSubShown(mSubShown);
		setTextViewStyle(mSubtitleText);
		if (!TextUtils.isEmpty(mSubPath))
			vPlayer.setSubPath(mSubPath);
		if (mVideoView != null && isInitialized())
			setVideoLayout();
	}

	private void setTextViewStyle(OutlineTextView v) {
		v.setTextColor(VP.DEFAULT_SUB_COLOR);
		v.setTypeface(VP.getTypeface(VP.DEFAULT_TYPEFACE_INT),
				VP.DEFAULT_SUB_STYLE);
		v.setShadowLayer(VP.DEFAULT_SUB_SHADOWRADIUS, 0, 0,
				VP.DEFAULT_SUB_SHADOWCOLOR);
	}

	private boolean isInitialized() {
		return (mCreated && vPlayer != null && vPlayer.isInitialized());
	}

	private Handler mSubHandler = new Handler() {
		Bundle data;
		String text;
		byte[] pixels;
		int width = 0, height = 0;
		Bitmap bm = null;
		int oldType = SUBTITLE_TEXT;

		@Override
		public void handleMessage(Message msg) {
			data = msg.getData();
			switch (msg.what) {
			case SUBTITLE_TEXT:
				if (oldType != SUBTITLE_TEXT) {
					mSubtitleImage.setVisibility(View.GONE);
					mSubtitleText.setVisibility(View.VISIBLE);
					oldType = SUBTITLE_TEXT;
				}
				text = data.getString(VP.SUB_TEXT_KEY);
				mSubtitleText.setText(text == null ? "" : text.trim());
				break;
			case SUBTITLE_BITMAP:
				if (oldType != SUBTITLE_BITMAP) {
					mSubtitleText.setVisibility(View.GONE);
					mSubtitleImage.setVisibility(View.VISIBLE);
					oldType = SUBTITLE_BITMAP;
				}
				pixels = data.getByteArray(VP.SUB_PIXELS_KEY);
				if (bm == null || width != data.getInt(VP.SUB_WIDTH_KEY)
						|| height != data.getInt(VP.SUB_HEIGHT_KEY)) {
					width = data.getInt(VP.SUB_WIDTH_KEY);
					height = data.getInt(VP.SUB_HEIGHT_KEY);
					bm = Bitmap.createBitmap(width, height,
							Bitmap.Config.ARGB_8888);
				}
				if (pixels != null)
					bm.copyPixelsFromBuffer(ByteBuffer.wrap(pixels));
				mSubtitleImage.setImageBitmap(bm);
				break;
			}
		}
	};

	private AtomicBoolean mOpened = new AtomicBoolean(Boolean.FALSE);
	private boolean mSurfaceCreated = false;
	private boolean mServiceConnected = false;
	private Object mOpenLock = new Object();
	private static final int OPEN_FILE = 0;
	private static final int OPEN_START = 1;
	private static final int OPEN_SUCCESS = 2;
	private static final int OPEN_FAILED = 3;
	private static final int HW_FAILED = 4;
	private static final int LOAD_PREFS = 5;
	private static final int BUFFER_START = 11;
	private static final int BUFFER_PROGRESS = 12;
	private static final int BUFFER_COMPLETE = 13;
	private static final int CLOSE_START = 21;
	private static final int CLOSE_COMPLETE = 22;
	private static final int SUBTITLE_TEXT = 0;
	private static final int SUBTITLE_BITMAP = 1;
	private Handler vPlayerHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case OPEN_FILE:
				synchronized (mOpenLock) {
					if (!mOpened.get() && vPlayer != null) {
						mOpened.set(true);
						vPlayer.setVPlayerListener(vPlayerListener);
						if (vPlayer.isInitialized())
							mUri = vPlayer.getUri();

						if (mVideoView != null)
							vPlayer.setDisplay(mVideoView.getHolder());
						if (mUri != null)
							vPlayer.initialize(mUri, mDisplayName, mSaveUri,
									getStartPosition(), vPlayerListener,
									mParentId, mIsHWCodec);
					}
				}
				break;
			case OPEN_START:
				mVideoLoadingText.setText(R.string.video_layout_loading);
				setVideoLoadingLayoutVisibility(View.VISIBLE);
				break;
			case OPEN_SUCCESS:
				loadVPlayerPrefs();
				setVideoLoadingLayoutVisibility(View.GONE);
				setVideoLayout();
				vPlayer.start();
				if(mDanmakuView.isPaused())
					mDanmakuView.resume();
				else
					mDanmakuView.start();
				attachMediaController();
				break;
			case OPEN_FAILED:
				resultFinish(RESULT_FAILED);
				break;
			case BUFFER_START:
				setVideoLoadingLayoutVisibility(View.VISIBLE);
				vPlayerHandler.sendEmptyMessageDelayed(BUFFER_PROGRESS, 1000);
				break;
			case BUFFER_PROGRESS:
				if (vPlayer.getBufferProgress() >= 100) {
					setVideoLoadingLayoutVisibility(View.GONE);
				} else {
					mVideoLoadingText.setText(getString(
							R.string.video_layout_buffering_progress,
							vPlayer.getBufferProgress()));
					vPlayerHandler.sendEmptyMessageDelayed(BUFFER_PROGRESS,
							1000);
					stopPlayer();
				}
				break;
			case BUFFER_COMPLETE:
				setVideoLoadingLayoutVisibility(View.GONE);
				vPlayerHandler.removeMessages(BUFFER_PROGRESS);
				break;
			case CLOSE_START:
				mVideoLoadingText.setText(R.string.closing_file);
				setVideoLoadingLayoutVisibility(View.VISIBLE);
				break;
			case CLOSE_COMPLETE:
				mCloseComplete = true;
				break;
			case HW_FAILED:
				if (mVideoView != null) {
					mVideoView.setVisibility(View.GONE);
					mVideoView.setVisibility(View.VISIBLE);
					mVideoView.initialize(BiliVideoViewActivity.this,
							BiliVideoViewActivity.this, false);
				}
				break;
			case LOAD_PREFS:
				loadVPlayerPrefs();
				break;
			}
		}
	};

	private void setVideoLoadingLayoutVisibility(int visibility) {
		if (mVideoLoadingLayout != null) {
			// if (visibility == View.VISIBLE)
			// mLoadingProgressView.startAnimation(mLoadingAnimation);
			mVideoLoadingLayout.setVisibility(visibility);
		}
	}

	private PlayerService.VPlayerListener vPlayerListener = new PlayerService.VPlayerListener() {
		@Override
		public void onHWRenderFailed() {
			if (Build.VERSION.SDK_INT < 11 && mIsHWCodec) {
				vPlayerHandler.sendEmptyMessage(HW_FAILED);
				vPlayerHandler.sendEmptyMessageDelayed(HW_FAILED, 200);
			}
		}

		@Override
		public void onSubChanged(String sub) {
			Message msg = new Message();
			Bundle b = new Bundle();
			b.putString(VP.SUB_TEXT_KEY, sub);
			msg.setData(b);
			msg.what = SUBTITLE_TEXT;
			mSubHandler.sendMessage(msg);
		}

		@Override
		public void onSubChanged(byte[] pixels, int width, int height) {
			Message msg = new Message();
			Bundle b = new Bundle();
			b.putByteArray(VP.SUB_PIXELS_KEY, pixels);
			b.putInt(VP.SUB_WIDTH_KEY, width);
			b.putInt(VP.SUB_HEIGHT_KEY, height);
			msg.setData(b);
			msg.what = SUBTITLE_BITMAP;
			mSubHandler.sendMessage(msg);
		}

		@Override
		public void onOpenStart() {
			vPlayerHandler.sendEmptyMessage(OPEN_START);
		}

		@Override
		public void onOpenSuccess() {
			vPlayerHandler.sendEmptyMessage(OPEN_SUCCESS);
		}

		@Override
		public void onOpenFailed() {
			vPlayerHandler.sendEmptyMessage(OPEN_FAILED);
		}

		@Override
		public void onBufferStart() {
			//if(isfirst){}
			vPlayerHandler.sendEmptyMessage(BUFFER_START);
			stopPlayer();
		}

		@Override
		public void onBufferComplete() {
			Log.i("VideoActivity#onBufferComplete " + vPlayer.needResume());
			if(isfirst){
				startVideo.setVisibility(View.GONE);
				anim.stop();
				isfirst = false;
			}
			vPlayerHandler.sendEmptyMessage(BUFFER_COMPLETE);
			if (vPlayer != null && !vPlayer.needResume())
				startPlayer();
		}

		@Override
		public void onPlaybackComplete() {
			mEnd = true;
			if (mLoopCount == 0 || mLoopCount-- > 1) {
				vPlayer.start();
				if(mDanmakuView.isPaused())
					mDanmakuView.resume();
				else
					mDanmakuView.start();
				vPlayer.seekTo(0);
			} else {
				resultFinish(RESULT_OK);
			}
		}

		@Override
		public void onCloseStart() {
			vPlayerHandler.sendEmptyMessage(CLOSE_START);
		}

		@Override
		public void onCloseComplete() {
			vPlayerHandler.sendEmptyMessage(CLOSE_COMPLETE);
		}

		@Override
		public void onVideoSizeChanged(int width, int height) {
			if (mVideoView != null) {
				setVideoLayout();
			}
		}

		@Override
		public void onDownloadRateChanged(int kbPerSec) {
			if (!MediaUtils.isNative(mUri.toString())
					&& mMediaController != null) {
				mMediaController.setDownloadRate(String.format("%dKB/s",
						kbPerSec));
			}
		}

	};

	private int mVideoMode = VideoView.VIDEO_LAYOUT_SCALE;

	private void setVideoLayout() {
		mVideoView.setVideoLayout(mVideoMode, VP.DEFAULT_ASPECT_RATIO,
				vPlayer.getVideoWidth(), vPlayer.getVideoHeight(),
				vPlayer.getVideoAspectRatio());
	}

	private void savePosition() {
		if (vPlayer != null && mUri != null) {
			PreferenceUtils.put(
					mUri.toString(),
					StringUtils.generateTime((int) (0.5 + vPlayer
							.getCurrentPosition()))
							+ " / "
							+ StringUtils.generateTime(vPlayer.getDuration()));
			if (mEnd)
				PreferenceUtils
						.put(mUri + VP.SESSION_LAST_POSITION_SUFIX, 1.0f);
			else
				PreferenceUtils
						.put(mUri + VP.SESSION_LAST_POSITION_SUFIX,
								(float) (vPlayer.getCurrentPosition() / (double) vPlayer
										.getDuration()));
		}
	}

	private float getStartPosition() {
		if (mFromStart)
			return 1.1f;
		if (mStartPos <= 0.0f || mStartPos >= 1.0f)
			return PreferenceUtils.getFloat(mUri
					+ VP.SESSION_LAST_POSITION_SUFIX, 7.7f);
		return mStartPos;
	}

	@Override
	public int getBufferPercentage() {
		if (isInitialized())
			return (int) (vPlayer.getBufferProgress() * 100);
		return 0;
	}

	@Override
	public long getCurrentPosition() {
		if (isInitialized())
			return vPlayer.getCurrentPosition();
		return (long) (getStartPosition() * vPlayer.getDuration());
	}

	@Override
	public long getDuration() {
		if (isInitialized())
			return vPlayer.getDuration();
		return 0;
	}

	@Override
	public boolean isPlaying() {
		if (isInitialized())
			return vPlayer.isPlaying();
		return false;
	}

	@Override
	public void pause() {
		if (isInitialized())
			vPlayer.stop();
		mDanmakuView.pause();
	}

	@Override
	public void seekTo(long arg0) {
		if (isInitialized())
			vPlayer.seekTo((float) ((double) arg0 / vPlayer.getDuration()));
		mDanmakuView.seekTo(arg0);
		mDanmakuView.pause();
	}

	@Override
	public void start() {
		if (isInitialized())
			vPlayer.start();
		if(mDanmakuView.isPaused())
		mDanmakuView.resume();
		else
		mDanmakuView.start();
	}

	@Override
	public void previous() {
	}

	@Override
	public void next() {
	}

	private static final int VIDEO_MAXIMUM_HEIGHT = 2048;
	private static final int VIDEO_MAXIMUM_WIDTH = 2048;

	@Override
	public float scale(float scaleFactor) {
		float userRatio = VP.DEFAULT_ASPECT_RATIO;
		int videoWidth = vPlayer.getVideoWidth();
		int videoHeight = vPlayer.getVideoHeight();
		float videoRatio = vPlayer.getVideoAspectRatio();
		float currentRatio = mVideoView.mVideoHeight / (float) videoHeight;

		currentRatio += (scaleFactor - 1);
		if (videoWidth * currentRatio >= VIDEO_MAXIMUM_WIDTH)
			currentRatio = VIDEO_MAXIMUM_WIDTH / (float) videoWidth;

		if (videoHeight * currentRatio >= VIDEO_MAXIMUM_HEIGHT)
			currentRatio = VIDEO_MAXIMUM_HEIGHT / (float) videoHeight;

		if (currentRatio < 0.5f)
			currentRatio = 0.5f;

		mVideoView.mVideoHeight = (int) (videoHeight * currentRatio);
		mVideoView.setVideoLayout(mVideoMode, userRatio, videoWidth,
				videoHeight, videoRatio);
		return currentRatio;
	}

	@SuppressLint("SimpleDateFormat")
	@Override
	public void snapshot() {
		if (!com.vanco.abplayer.view.FileUtils.sdAvailable()) {
			ToastUtils.showToast(R.string.file_explorer_sdcard_not_available);
		} else {
			Uri imgUri = null;
			Bitmap bitmap = vPlayer.getCurrentFrame();
			if (bitmap != null) {
				File screenshotsDirectory = new File(
						Environment
								.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
								+ VP.SNAP_SHOT_PATH);
				if (!screenshotsDirectory.exists()) {
					screenshotsDirectory.mkdirs();
				}

				File savePath = new File(
						screenshotsDirectory.getPath()
								+ "/"
								+ new SimpleDateFormat("yyyyMMddHHmmss")
										.format(new Date()) + ".jpg");
				if (ImageUtils.saveBitmap(savePath.getPath(), bitmap)) {
					imgUri = Uri.fromFile(savePath);
				}
			}
			if (imgUri != null) {
				sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
						imgUri));
				ToastUtils.showLongToast(getString(
						R.string.video_screenshot_save_in, imgUri.getPath()));
			} else {
				ToastUtils.showToast(R.string.video_screenshot_failed);
			}
		}
	}

	@Override
	public void toggleVideoMode(int mode) {
		mVideoMode = mode;
		setVideoLayout();
	}

	@Override
	public void stop() {
		onBackPressed();
	}

	@Override
	public long goForward() {
		return 0;
	}

	@Override
	public long goBack() {
		return 0;
	}

	@Override
	public void removeLoadingView() {
		mVideoLoadingLayout.setVisibility(View.GONE);
	}

	@Override
	public void onSurfaceCreated(SurfaceHolder holder) {
		Log.i("onSurfaceCreated");
		mSurfaceCreated = true;
		if (mServiceConnected)
			vPlayerHandler.sendEmptyMessage(OPEN_FILE);
		if (vPlayer != null)
			vPlayer.setDisplay(holder);
	}

	@Override
	public void onSurfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		if (vPlayer != null) {
			setVideoLayout();
		}
	}

	@Override
	public void onSurfaceDestroyed(SurfaceHolder holder) {
		Log.i("onSurfaceDestroyed");
		if (vPlayer != null && vPlayer.isInitialized()) {
			if (vPlayer.isPlaying()) {
				vPlayer.stop();
				vPlayer.setState(PlayerService.STATE_NEED_RESUME);
			}
			vPlayer.releaseSurface();
			if (vPlayer.needResume()){
				vPlayer.start();
				if(mDanmakuView.isPaused())
				mDanmakuView.resume();
				else
				mDanmakuView.start();
			}
			
		}
	}
	
	@Override
	public void setDanmakushow(boolean isShow) {
		// TODO Auto-generated method stub
		if(mDanmakuView != null){
			if(isShow){
				mDanmakuView.show();
			}else{
				mDanmakuView.hide();
			}
		}
	}
	
	private class VideoViewInitTask extends AsyncTask<String, Void, Integer> {

		@Override
		protected Integer doInBackground(String... arg0) {
			// TODO Auto-generated method stub
			Log.d("TAG", "????????????????????????");
			try {
				JSONObject videopathjson = new JSONObject(HttpUtil.getHtmlString("http://www.bilibili.com/m/html5?aid="+av+"&page="+page));
				Log.d("QAQ--->","===>"+videopathjson.getString("src").toString());
				danmakuPath = videopathjson.getString("cid").toString();
				mUri = Uri.parse(videopathjson.getString("src").toString());
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			Log.d("TAG", "??????????????????");
			mParser = createParser(danmakuPath);
			return null;
		}
		
		@Override
		protected void onPostExecute(Integer result) {
			// TODO Auto-generated method stub
			super.onPostExecute(result);

			Log.d("TAG", "??????????????????");
//			 if (mDanmakuView != null) {
//		            mDanmakuView.prepare(mParser);
//		            mDanmakuView.showFPS(true);
//		            mDanmakuView.enableDanmakuDrawingCache(true);
//                	isload = true;
//                	//startPlayer();
//                	
//		    }
			if(mParser != null){
				mDanmakuView.prepare(mParser);
		        mDanmakuView.showFPS(false);
		        mDanmakuView.enableDanmakuDrawingCache(false);
			}else{
				
				startText = startText + "????????????\n???????????????...";
				startVideoInfo.setText(startText);
			}
//	        mDanmakuView.prepare(mParser);
//	        mDanmakuView.showFPS(true);
//	        mDanmakuView.enableDanmakuDrawingCache(true);
            isload = true;
			Intent serviceIntent = new Intent(getApplicationContext(), PlayerService.class);
			serviceIntent.putExtra("isHWCodec", mIsHWCodec);
			bindService(serviceIntent, vPlayerServiceConnection,Context.BIND_AUTO_CREATE);
			startText = startText + "????????????\n???????????????...";
			startVideoInfo.setText(startText);
		}
	}
	
	/*????????????????????? */
	private static final long EXIT_INTERVAL_TIME = 2000;
	private long touchTime = 0;

    public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK
			) {
			long currentTime = System.currentTimeMillis();

			if ((currentTime - touchTime) >= EXIT_INTERVAL_TIME) {
				Toast.makeText(BiliVideoViewActivity.this, "?????????????????????(??????????????)~", 1).show();
				touchTime = currentTime;
			} else {
				finish();
				if(mDanmakuView!=null)
				mDanmakuView.release();
			}

			return false;
		} else {
			return true;
		}
	}

}
