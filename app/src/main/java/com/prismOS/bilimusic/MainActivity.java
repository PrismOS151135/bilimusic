package com.prismOS.bilimusic;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.text.SimpleDateFormat;

public class MainActivity extends AppCompatActivity {
    private TextView PlayingTime, allPlayTime, RestSleepTime;
    private ImageButton settingsButton, infoButton, modeButton, prevButton, playButton, nextButton, musicListButton;
    private ProgressBar musicProgressBar;
    private ImageView songImage;

    private GestureDetector gestureDetector;
    private LinearLayout layoutTouch;
    private boolean isDoubleTap = false;

    private View t1, r0, r1, r2, r3, r4, r5;
    protected static final List<String> musicFolders = new ArrayList<>();
    protected static final List<String> shuffledList = new ArrayList<>();
    private static ExoPlayer exoPlayer;
    protected static int currentPosition = -1;
    private static PlayMode playMode = PlayMode.SEQUENTIAL;
    protected static boolean isPlaying = false;
    private float playSpeed = 1.0f;
    private final Handler longPressHandler = new Handler();
    private final Handler keyLongPressHandler = new Handler();
    private final Handler cooldownHandler = new Handler();
    private final Handler playTimeUpdateHandler = new Handler();
    private final Handler sleepTimerHandler = new Handler();
    private final Handler sleepTimeUpdateHandler = new Handler();
    private Runnable sleepTimerRunnable;
    private boolean isSleepTimerActive = false;
    private boolean isPowerOff = false;
    private boolean isKeyLongPress = false;
    private boolean isCooldownActive = false;
    private static final long COOLDOWN_DURATION = 1000;
    private static long sleepTime = 0;

    private long sleepTimerEndTime;
    private SharedPreferences sharedPreferences;
    private TextView currentMusicText;
    protected static String TheMusicText;
    protected static final String PREFS_NAME = "MusicPlayerPrefs";
    protected static final String KEY_SCAN_PATH = "scan_path";
    protected static final String KEY_MUSIC_FILE = "music_file";
    private static final String KEY_PLAY_SPEED = "play_speed";
    private static final String KEY_LAST_POSITION = "last_position";
    private static final String KEY_LAST_PLAYBACK_POSITION = "last_playback_position";
    private static final String KEY_IS_PLAYING = "is_playing";
    private static final String KEY_PLAY_MODE = "play_mode";
    private static final String KEY_IMAGE_SIZE = "image_size";
    private static final String KEY_TEXT_SIZE = "text_size";
    protected static final String DEFAULT_SCAN_PATH = "/storage/sdcard1/1/Folder1";
    protected static final String DEFAULT_MUSIC_FILE = "video.mp4";
    private static String SCAN_PATH;
    private static String MUSIC_FILE;
    private MusicService musicService;
    private static int songImageSize;
    private static int theTextSize;
    private static int buttonInterval;
    private boolean isServiceBound = false;
    private boolean shouldPlayInBackground = false;
    protected static final Map<String,String> folderPathMap = new HashMap<>();
    //时间与电量
    private TextView electricQuantityText;
    private TextView nowTimeText;
    private final Handler timeHandler = new Handler();
    private Runnable timeUpdateTask;
    private BroadcastReceiver batteryReceiver;
    // 广播接收器，用于接收来自MusicListsActivity的播放请求
    private final BroadcastReceiver playMusicReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null && intent.getAction().equals(MusicListsActivity.ACTION_PLAY_MUSIC)) {
                int position = intent.getIntExtra(MusicListsActivity.EXTRA_POSITION, -1);
                if (position != -1) {
                    playMusic(position);
                }
            }
        }
    };

    enum PlayMode {
        SEQUENTIAL,
        RANDOM,
        LOOP
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            isServiceBound = true;

            if (musicService.isPlaying()) {
                isPlaying = true;
                currentPosition = musicService.getCurrentPosition();
                updateUI();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            musicService = null;
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupSharedPreferences();
        initViews();
        setImageViewSize(songImage,songImageSize);
        setViewSize(r0,buttonInterval);
        setViewSize(r1,buttonInterval);
        setViewSize(r2,buttonInterval);
        setViewSize(r3,buttonInterval);
        setViewSize(r4,buttonInterval);
        setViewSize(r5,buttonInterval);
        if (theTextSize == 0){
            t1.setVisibility(View.GONE);
            currentMusicText.setVisibility(View.GONE);
        } else{
            currentMusicText.setVisibility(View.VISIBLE);
            t1.setVisibility(View.VISIBLE);
            currentMusicText.setTextSize(theTextSize);
        }
        setupExoPlayer();
        setupButtonListeners();
        setupGestureDetector();
        setupSleepTimer();

        // 注册广播接收器
        IntentFilter filter = new IntentFilter(MusicListsActivity.ACTION_PLAY_MUSIC);
        registerReceiver(playMusicReceiver, filter);
        // 注册电量广播接收器
        registerBatteryReceiver();
        //启动音乐后台Service
        startAndBindMusicService();
        // 恢复上次播放状态
        playSpeed = sharedPreferences.getFloat(KEY_PLAY_SPEED, 1.0f);
        restorePlaybackState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        String newScanPath = sharedPreferences.getString(KEY_SCAN_PATH, DEFAULT_SCAN_PATH);
        String newMusicFile = sharedPreferences.getString(KEY_MUSIC_FILE, DEFAULT_MUSIC_FILE);
        int timedOffMinutes = sharedPreferences.getInt(SettingsActivity.KEY_TIMED_OFF, 0);
        float newPlaySpeed = sharedPreferences.getFloat(KEY_PLAY_SPEED, 1.0f);
        isPowerOff = sharedPreferences.getBoolean(SettingsActivity.KEY_IS_POWER_OFF,false);

        songImageSize = sharedPreferences.getInt(KEY_IMAGE_SIZE, 160);
        theTextSize = sharedPreferences.getInt(KEY_TEXT_SIZE, 16);
        buttonInterval = sharedPreferences.getInt(SettingsActivity.KEY_BUTTON_INTERVAL, 0);
        setImageViewSize(songImage, songImageSize);
        setViewSize(r0,buttonInterval);
        setViewSize(r1,buttonInterval);
        setViewSize(r2,buttonInterval);
        setViewSize(r3,buttonInterval);
        setViewSize(r4,buttonInterval);
        setViewSize(r5,buttonInterval);

        registerBatteryReceiver();

        if (theTextSize == 0){
            t1.setVisibility(View.GONE);
            currentMusicText.setVisibility(View.GONE);
        } else{
            currentMusicText.setVisibility(View.VISIBLE);
            t1.setVisibility(View.VISIBLE);
            currentMusicText.setTextSize(theTextSize);
        }

        if (!newScanPath.equals(SCAN_PATH) || !newMusicFile.equals(MUSIC_FILE)) {
            SCAN_PATH = newScanPath;
            MUSIC_FILE = newMusicFile;
            if(!scanMusicFolders(SCAN_PATH, MUSIC_FILE)) Toast.makeText(this, "扫描路径不存在: " + SCAN_PATH, Toast.LENGTH_LONG).show();
        }

        if (newPlaySpeed != playSpeed) {
            setPlaySpeed(newPlaySpeed);
        }

        if (timedOffMinutes > 0) {
            if (timedOffMinutes != sleepTime) {
                cancelSleepTimer();
                startSleepTimer(timedOffMinutes);
            }
        }else {
            cancelSleepTimer();
            sleepTimeUpdateHandler.removeCallbacks(sleepTimeUpdataRunnable);
            RestSleepTime.setVisibility(View.GONE);
        }
    }

    private void setImageViewSize(ImageView imageView,int size) {
        if (size == 0){
            imageView.setVisibility(View.GONE);
            t1.setVisibility(View.GONE);
        } else{
            float density = getResources().getDisplayMetrics().density;
            int sizeInPx = (int) (size * density + 0.5f);  // 四舍五入确保整数像素

            // 或者使用 TypedValue（更精确）
            // int sizeInPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, sizeInDp, getResources().getDisplayMetrics());

            imageView.setVisibility(View.VISIBLE);
            t1.setVisibility(View.VISIBLE);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(sizeInPx, sizeInPx);
            imageView.setLayoutParams(layoutParams);
            ((ViewGroup) imageView.getParent()).requestLayout();  // 强制父容器重新布局
        }
    }

    private void setViewSize(View View,int size) {
        if (size == 0){
            View.setVisibility(android.view.View.GONE);
        } else{
            float density = getResources().getDisplayMetrics().density;
            int sizeInPx = (int) (size * density + 0.5f);  // 四舍五入确保整数像素

            // 或者使用 TypedValue（更精确）
            // int sizeInPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, sizeInDp, getResources().getDisplayMetrics());

            View.setVisibility(android.view.View.VISIBLE);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(sizeInPx, 40);
            View.setLayoutParams(layoutParams);
            ((ViewGroup) View.getParent()).requestLayout();  // 强制父容器重新布局
        }
    }

    private void initViews() {
        currentMusicText = findViewById(R.id.currentMusicText);
        PlayingTime = findViewById(R.id.PlayingTime);//音频播放时长(now)
        allPlayTime = findViewById(R.id.allPlayTime);//音频播放时长(all)
        songImage = findViewById(R.id.songImage);//视频封面(cover.png)
        musicProgressBar = findViewById(R.id.musicProgressBar);//音频进度条
        settingsButton = findViewById(R.id.settingsButton);//设置按钮
        infoButton = findViewById(R.id.infoButton);//查看下一首信息
        modeButton = findViewById(R.id.modeButton);//列表播放模式
        prevButton = findViewById(R.id.prevButton);//⏮ 按钮
        playButton = findViewById(R.id.playButton);//播放键
        nextButton = findViewById(R.id.nextButton);//⏭ 按钮
        musicListButton = findViewById(R.id.musicListButton);//列表查看按钮
        electricQuantityText = findViewById(R.id.electricQuantity);//电量显示
        nowTimeText = findViewById(R.id.nowTime);//视频标题
        RestSleepTime = findViewById(R.id.RestSleepTime);//休眠倒计时
        layoutTouch = findViewById(R.id.layoutTouch);//触控区域
        t1 = findViewById(R.id.t1);//...史
        r0 = findViewById(R.id.r0);
        r1 = findViewById(R.id.r1);
        r2 = findViewById(R.id.r2);
        r3 = findViewById(R.id.r3);
        r4 = findViewById(R.id.r4);
        r5 = findViewById(R.id.r5);

        RestSleepTime.setVisibility(View.GONE);
        //播放时间显示
        updatePlayTimeDisplay();
        playTimeUpdateHandler.post(timeUpdateRunnable);

        // 初始化时间显示
        updateClockDisplay();
        startClockUpdate();
    }

    private void startAndBindMusicService() {
        Intent serviceIntent = new Intent(this, MusicService.class);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);
    }

    private void setupSharedPreferences() {
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        MUSIC_FILE = sharedPreferences.getString(KEY_MUSIC_FILE, DEFAULT_MUSIC_FILE);
        SCAN_PATH = sharedPreferences.getString(KEY_SCAN_PATH, DEFAULT_SCAN_PATH);
        songImageSize = sharedPreferences.getInt(KEY_IMAGE_SIZE,160);
        theTextSize = sharedPreferences.getInt(KEY_TEXT_SIZE, 16);
    }

    private void restorePlaybackState() {
        int lastPosition = sharedPreferences.getInt(KEY_LAST_POSITION, -1);
        long lastPlaybackPosition = sharedPreferences.getLong(KEY_LAST_PLAYBACK_POSITION, 0);
        boolean wasPlaying = sharedPreferences.getBoolean(KEY_IS_PLAYING, false);
        String savedPlayMode = sharedPreferences.getString(KEY_PLAY_MODE, "SEQUENTIAL");

        // 恢复播放模式
        switch (savedPlayMode) {
            case "RANDOM":
                playMode = PlayMode.RANDOM;
                modeButton.setImageResource(R.drawable.btn_play_random);
                break;
            case "LOOP":
                playMode = PlayMode.LOOP;
                modeButton.setImageResource(R.drawable.btn_play_rotate);
                break;
            default:
                playMode = PlayMode.SEQUENTIAL;
                modeButton.setImageResource(R.drawable.btn_play_sequential);
                break;
        }

        if (lastPosition != -1 && lastPosition < musicFolders.size()) {
            currentPosition = lastPosition;

            currentMusicText.setText(musicFolders.get(currentPosition));
            TheMusicText = currentMusicText.getText().toString().trim();

            if (!wasPlaying && !musicFolders.isEmpty()) {
                // 设置播放状态为false，这样播放按钮会显示暂停图标
                isPlaying = false;
                updateUI();
                // 准备播放器但不自动播放，等待用户按下播放按钮

                loadCoverImage(currentPosition);
                prepareMusicForPlayback(currentPosition, lastPlaybackPosition);
            } else {
                // 如果是播放状态
                isPlaying = true;
                loadCoverImage(currentPosition);
                prepareMusicForPlayback(currentPosition, lastPlaybackPosition);
                exoPlayer.play();
                updateUI();
            }
        }
    }

    /**
     * 准备播放器但不自动播放
     */
    private void prepareMusicForPlayback(int position, long playbackPosition) {
        if (position < 0 || position >= musicFolders.size()) return;
        try {
            String folderName = musicFolders.get(position);
            //用Hash进行查找
            File musicFile = new File(folderPathMap.get(folderName),MUSIC_FILE);
            if (musicFile.exists()) {
                Uri audioUri = Uri.fromFile(musicFile);
                MediaItem mediaItem = MediaItem.fromUri(audioUri);

                exoPlayer.setMediaItem(mediaItem);
                exoPlayer.prepare();
                // 设置播放位置但不开始播放
                exoPlayer.seekTo(playbackPosition);
                exoPlayer.pause(); // 确保暂停状态
                if (playSpeed != 1.0f) {
                    setPlaySpeed(playSpeed);
                }
                // 更新服务状态
                if (isServiceBound && musicService != null) {
                    musicService.updatePlaybackState(currentPosition, false); // 设置为未播放状态
                }
            } else {
                Toast.makeText(this, "找不到音乐文件: " + folderName, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "prepareMusicForPlayback错误：");
            Toast.makeText(this, "准备播放失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    private void savePlaybackState() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_LAST_POSITION, currentPosition);
        editor.putBoolean(KEY_IS_PLAYING, isPlaying);
        editor.putString(KEY_PLAY_MODE, playMode.name());
        if (exoPlayer != null) {
            editor.putLong(KEY_LAST_PLAYBACK_POSITION, exoPlayer.getCurrentPosition());
        } else {
            editor.putLong(KEY_LAST_PLAYBACK_POSITION, 0);
        }
        editor.apply();
    }

    //--------------------------------------------------------------------------//

    /**
     * 播放时间显示
     */
    //定义
    private final Runnable timeUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            updatePlayTimeDisplay();
            playTimeUpdateHandler.postDelayed(this, 1000);
        }
    };
    //实现
    @SuppressLint({"DefaultLocale", "SetTextI18n"})
    private void updatePlayTimeDisplay() {
        if (exoPlayer != null && isPlaying) {
            try {
                long currentPosition = exoPlayer.getCurrentPosition();//播放时间
                long duration = exoPlayer.getDuration();//完整时间
                if (duration > 0) {
                    int Progress = (int) (500 * currentPosition / duration);
                    musicProgressBar.setProgress(Progress);
                }else{
                    musicProgressBar.setProgress(0);
                }
                String currentTime = formatTime(currentPosition);
                String totalTime = formatTime(duration);

                PlayingTime.setText(currentTime);
                allPlayTime.setText(totalTime);
            } catch (Exception e) {
                PlayingTime.setText("00:00");
                allPlayTime.setText("00:91");
            }
        } else {
            PlayingTime.setText("00:00");
            allPlayTime.setText("00:91");
        }
    }
    //时间格式化
    @SuppressLint("DefaultLocale")
    private String formatTime(long milliseconds) {
        if(milliseconds < 0)milliseconds = 0;
        long totalSeconds = milliseconds / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    /**
     * 定时结束时间显示
     */
    //定义
    private final Runnable sleepTimeUpdataRunnable = new Runnable() {
        @Override
        public void run() {
            updateSleepTimeDisplay();
            sleepTimeUpdateHandler.postDelayed(this,1000);
        }
    };
    //实现
    private void updateSleepTimeDisplay(){
        long nowSleepTime = sleepTimerEndTime - System.currentTimeMillis();
        if (isSleepTimerActive && nowSleepTime >= 0){
            RestSleepTime.setVisibility(View.VISIBLE);
            long minutes = nowSleepTime / (60 * 1000);
            long seconds = (nowSleepTime % (60 * 1000)) / 1000;
            RestSleepTime.setText(String.format(Locale.ROOT, "%d分%02d秒", minutes, seconds));
        }else{
            sleepTimeUpdateHandler.removeCallbacks(sleepTimeUpdataRunnable);
            RestSleepTime.setVisibility(View.GONE);
        }
    }
    /**
     * 当前时钟显示
     */
    //定义
    private void startClockUpdate() {
        timeUpdateTask = new Runnable() {
            @Override
            public void run() {
                updateClockDisplay();
                // 每30s更新一次（或者可以更频繁，如每秒一次）
                timeHandler.postDelayed(this, 30000);
            }
        };
        timeHandler.post(timeUpdateTask);
    }
    //实现
    @SuppressLint("SimpleDateFormat")
    private void updateClockDisplay() {
        if (nowTimeText == null) return;

        SimpleDateFormat sdf = new SimpleDateFormat("ahh:mm", Locale.CHINA);
        String currentTime = sdf.format(new Date());
        nowTimeText.setText(currentTime);
    }
    /**
     * 创建电池广播接收器
     */
    private void registerBatteryReceiver() {
        batteryReceiver = new BroadcastReceiver() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                    int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                    if (level != -1 && scale != -1) {
                        float batteryPct = level * 100f / scale;
                        electricQuantityText.setText("电量:" + (int) batteryPct + "%");
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        registerReceiver(batteryReceiver, filter);
    }

    //--------------------------------------------------------------------------//

    /**
     * UI更新
     */
    private void updateUI() {
        if (isPlaying) {
            playButton.setImageResource(R.drawable.btn_stop);
        } else {
            playButton.setImageResource(R.drawable.btn_play);
        }
    }
    //使用加载封面图片
    private void loadCoverImage(int position) {
        if (position < 0 || position >= musicFolders.size()) return;
        String folderPath = folderPathMap.get( musicFolders.get(position));
        if (folderPath == null) return;
        // 设置Glide的RequestOptions
        RequestOptions requestOptions = new RequestOptions()
                .centerCrop() // 设置图片居中裁剪
                .error(R.drawable.img_error) // 加载错误时显示的图片
                .placeholder(R.drawable.img_loading) // 加载过程中显示的占位图
                .diskCacheStrategy(DiskCacheStrategy.ALL) // 缓存策略
                .override(400, 400); // 可以设置图片大小，根据实际需要调整
        File coverFile = new File(folderPath, "cover.png");

        if (coverFile.exists() && coverFile.length() > 0) {
            // 使用Glide加载封面图片
            Glide.with(this)
                    .load(coverFile)
                    .apply(requestOptions)
                    .into(songImage);
        } else {
            // 没有找到封面图片，使用默认图片
            Glide.with(this)
                    .load(R.drawable.img_none)
                    .apply(requestOptions)
                    .into(songImage);
        }
    }

    //--------------------------------------------------------------------------//

    /**
     * 核心实现
     */
    //音乐播放核心功能实现
    @SuppressLint("SetTextI18n")
    protected void playMusic(int position) {
        if (position < 0 || position >= musicFolders.size()) return;
        try {
            if (exoPlayer.isPlaying()) {
                exoPlayer.stop();
            }
            String folderName = musicFolders.get(position);
            File musicFile = new File(folderPathMap.get(folderName),MUSIC_FILE);

            if (musicFile.exists()) {
                Uri audioUri = Uri.fromFile(musicFile);
                MediaItem mediaItem = MediaItem.fromUri(audioUri);

                loadCoverImage(position);

                exoPlayer.setMediaItem(mediaItem);
                exoPlayer.prepare();
                exoPlayer.play();

                if (playSpeed != 1.0f) {
                    setPlaySpeed(playSpeed);
                }

                currentPosition = position;
                isPlaying = true;
                updateUI();

                currentMusicText.setText(folderName);
                TheMusicText = currentMusicText.getText().toString().trim();

                if (isServiceBound && musicService != null) {
                    musicService.updatePlaybackState(currentPosition, isPlaying);
                }

                // 发送广播通知MusicListsActivity更新状态
                Intent statusUpdateIntent = new Intent("com.prismOS.bilimusic.STATUS_UPDATE");
                sendBroadcast(statusUpdateIntent);

                playTimeUpdateHandler.post(timeUpdateRunnable);
                savePlaybackState(); // 保存播放状态

            } else {
                Toast.makeText(this, "找不到音乐文件: " + folderName, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "playMusic错误：", e);
            Toast.makeText(this, "播放失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    //暂停、播放功能实现
    public void togglePlayPause() {
        if (exoPlayer == null) return;

        // 添加简单的视觉反馈
        if (isPlaying) {
            // 暂停播放
            exoPlayer.pause();
            isPlaying = false;
            playTimeUpdateHandler.removeCallbacks(timeUpdateRunnable);

            // 发送广播通知MusicListsActivity更新状态
            Intent statusUpdateIntent = new Intent("com.prismOS.bilimusic.STATUS_UPDATE");
            sendBroadcast(statusUpdateIntent);
        } else {
            // 开始播放
            if (currentPosition == -1 && !musicFolders.isEmpty()) {
                // 如果没有当前曲目，从第一首开始
                playMusic(0);
            } else if (currentPosition != -1) {
                // 检查播放器是否有媒体项，如果没有则重新设置
                if (exoPlayer.getMediaItemCount() == 0) {
                    // 重新准备播放器
                    prepareMusicForPlayback(currentPosition, 0);
                }

                // 开始播放
                exoPlayer.play();
                isPlaying = true;
                playTimeUpdateHandler.post(timeUpdateRunnable);

                // 发送广播通知MusicListsActivity更新状态
                Intent statusUpdateIntent = new Intent("com.prismOS.bilimusic.STATUS_UPDATE");
                sendBroadcast(statusUpdateIntent);

                // 更新服务状态
                if (isServiceBound && musicService != null) {
                    musicService.updatePlaybackState(currentPosition, isPlaying);
                }
            }
        }

        updateUI();
        savePlaybackState(); // 保存状态变更
    }
    //音乐列表生成 I
    public static boolean scanMusicFolders(String scanPath, String musicFile) {
        musicFolders.clear();
        shuffledList.clear();
        File baseDir = new File(scanPath);

        if (baseDir.exists() && baseDir.isDirectory()) {
            scanDirectory(baseDir, musicFile);
            // 初始化打乱列表
            shuffledList.addAll(musicFolders);
            // 如果当前是随机模式，打乱列表
            Collections.shuffle(shuffledList);
        } else {
            return false;
        }
        return true;
    }
    //音乐列表生成 II
    public static void scanDirectory(File dir, String musicFile) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                File musicFiles = new File(file, musicFile);
                if (musicFiles.exists() && musicFiles.isFile()) {
                    musicFolders.add(file.getName());
                    folderPathMap.put(file.getName(), file.getAbsolutePath());
                }
                scanDirectory(file, musicFile);
            }
        }
    }
    //...
    private void setPlaySpeed(float speed) {
        this.playSpeed = speed;

        if (exoPlayer != null) {
            PlaybackParameters playbackParameters = new PlaybackParameters(speed);
            exoPlayer.setPlaybackParameters(playbackParameters);
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat(KEY_PLAY_SPEED, speed);
        editor.apply();

        Toast.makeText(this, "播放速度设置为: " + speed + "x", Toast.LENGTH_SHORT).show();
    }
    //...
    private void setupExoPlayer() {
        exoPlayer = new ExoPlayer.Builder(this).build();

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    playNext();
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Toast.makeText(MainActivity.this, "播放错误: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupButtonListeners() {
        settingsButton.setOnClickListener(v -> openSettings());
        infoButton.setOnClickListener(v -> showNextMusicInfo());
        modeButton.setOnClickListener(v -> switchPlayMode());
        playButton.setOnClickListener(v -> togglePlayPause());
        musicListButton.setOnClickListener(v -> openLists());

        setupTouchListeners();
        setupBtnKeyListeners();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                // 双击播放/暂停
                if (!isDoubleTap) {
                    isDoubleTap = true;
                    togglePlayPause();

                    // 重置双击标志
                    new Handler().postDelayed(() -> isDoubleTap = false, 300);
                    return true;
                }
                return false;
            }

            @Override
            public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                // 左右滑动切换歌曲
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                // 确保是水平滑动（水平位移大于垂直位移，且最小滑动距离）
                if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 100) {
                    if (diffX > 0) {
                        // 向右滑动 - 上一首
                        playPrevious();
                    } else {
                        // 向左滑动 - 下一首
                        playNext();
                    }
                    return true;
                }
                return false;
            }

        });
        // 为 layoutTouch 设置触摸监听器
        layoutTouch.setOnTouchListener((v, event) -> {
            // 将触摸事件传递给手势检测器
            gestureDetector.onTouchEvent(event);
            return true;
        });
    }

    private void openLists() {
        Intent intent = new Intent(this, MusicListsActivity.class);
        startActivity(intent);
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    @SuppressLint("SetTextI18n")
    private void showNextMusicInfo() {
        if (musicFolders.isEmpty()) {
            Toast.makeText(this, "没有可播放的音乐", Toast.LENGTH_SHORT).show();
            return;
        }

        switch (playMode) {
            case SEQUENTIAL:
                if (currentPosition > -1) {
                    int nextPos = (currentPosition + 1) % musicFolders.size();
                    Toast.makeText(this, "下一首: " + musicFolders.get(nextPos), Toast.LENGTH_SHORT).show();
                }
                break;
            case RANDOM:
                if (shuffledList.isEmpty()) {
                    Toast.makeText(this, "播放列表为空,请尝试重启应用", Toast.LENGTH_SHORT).show();
                } else {
                    int currentShufflePos = getCurrentShufflePosition();
                    if (currentShufflePos == -1) {
                        // 如果当前不在打乱列表中，随机选择一首
                        int randomPos = new Random().nextInt(shuffledList.size());
                        Toast.makeText(this, "下一首: " + shuffledList.get(randomPos), Toast.LENGTH_SHORT).show();
                    } else {
                        int nextPos = (currentShufflePos + 1) % shuffledList.size();
                        Toast.makeText(this, "下一首: " + shuffledList.get(nextPos), Toast.LENGTH_SHORT).show();
                    }
                }
                break;
            case LOOP:
                if (currentPosition != -1) {
                    Toast.makeText(this, "单曲循环: " + musicFolders.get(currentPosition), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "单曲循环: 无", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    private int getCurrentShufflePosition() {
        if (currentPosition == -1) return -1;
        String currentFolder = musicFolders.get(currentPosition);
        return shuffledList.indexOf(currentFolder);
    }

    //--------------------------------------------------------------------------//

    @SuppressLint("ClickableViewAccessibility")
    private void setupTouchListeners() {
        prevButton.setOnTouchListener(new View.OnTouchListener() {
            private long touchStartTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (isCooldownActive) {
                            return true;
                        }
                        touchStartTime = System.currentTimeMillis();
                        longPressHandler.postDelayed(() -> {
                            if (!isCooldownActive) {
                                startCooldown();
                                playPrevious();
                            }
                        }, 500);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        longPressHandler.removeCallbacksAndMessages(null);
                        if (System.currentTimeMillis() - touchStartTime < 1500 && !isCooldownActive) {
                            fastRewind();
                        }
                        return true;
                }
                return false;
            }
        });

        nextButton.setOnTouchListener(new View.OnTouchListener() {
            private long touchStartTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (isCooldownActive) {
                            return true;
                        }
                        touchStartTime = System.currentTimeMillis();
                        longPressHandler.postDelayed(() -> {
                            if (!isCooldownActive) {
                                startCooldown();
                                playNext();
                            }
                        }, 500);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        longPressHandler.removeCallbacksAndMessages(null);
                        if (System.currentTimeMillis() - touchStartTime < 1500 && !isCooldownActive) {
                            fastForward();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void setupBtnKeyListeners() {
        prevButton.setFocusable(true);
        nextButton.setFocusable(true);

        prevButton.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                switch (event.getAction()) {
                    case KeyEvent.ACTION_DOWN:
                        if (isCooldownActive) {
                            return true;
                        }
                        System.currentTimeMillis();
                        isKeyLongPress = false;
                        keyLongPressHandler.postDelayed(() -> {
                            if (!isCooldownActive) {
                                isKeyLongPress = true;
                                startCooldown();
                                playPrevious();
                            }
                        }, 500);
                        return true;

                    case KeyEvent.ACTION_UP:
                        keyLongPressHandler.removeCallbacksAndMessages(null);
                        if (!isKeyLongPress && !isCooldownActive) {
                            fastRewind();
                        }
                        isKeyLongPress = false;
                        return true;
                }
            }
            return false;
        });

        nextButton.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                switch (event.getAction()) {
                    case KeyEvent.ACTION_DOWN:
                        if (isCooldownActive) {
                            return true;
                        }
                        System.currentTimeMillis();
                        isKeyLongPress = false;
                        keyLongPressHandler.postDelayed(() -> {
                            if (!isCooldownActive) {
                                isKeyLongPress = true;
                                startCooldown();
                                playNext();
                            }
                        }, 500);
                        return true;

                    case KeyEvent.ACTION_UP:
                        keyLongPressHandler.removeCallbacksAndMessages(null);
                        if (!isKeyLongPress && !isCooldownActive) {
                            fastForward();
                        }
                        isKeyLongPress = false;
                        return true;
                }
            }
            return false;
        });
    }


    private void startCooldown() {
        isCooldownActive = true;
        cooldownHandler.postDelayed(() -> isCooldownActive = false, COOLDOWN_DURATION);
    }

    //--------------------------------------------------------------------------//

    /**
     * 播放功能
     */
    //切换播放模式
    private void switchPlayMode() {
        switch (playMode) {
            case SEQUENTIAL:
                playMode = PlayMode.RANDOM;
                modeButton.setImageResource(R.drawable.btn_play_random);
                // 打乱列表
                Collections.shuffle(shuffledList);
                break;
            case RANDOM:
                playMode = PlayMode.LOOP;
                modeButton.setImageResource(R.drawable.btn_play_rotate);
                break;
            case LOOP:
                playMode = PlayMode.SEQUENTIAL;
                modeButton.setImageResource(R.drawable.btn_play_sequential);
                break;
        }

        if (isServiceBound && musicService != null) {
            musicService.updatePlaybackState(currentPosition, isPlaying);
        }

        // 保存播放模式
        savePlaybackState();
    }
    //播放上一首
    public void playPrevious() {
        if (musicFolders.isEmpty()) {
            Toast.makeText(this, "没有可播放的音乐", Toast.LENGTH_SHORT).show();
            return;
        }
        int prevPos;
        switch (playMode) {
            case RANDOM:
                if (shuffledList.isEmpty()) {
                    prevPos = 0;
                } else {
                    int currentShufflePos = getCurrentShufflePosition();
                    if (currentShufflePos == -1) {
                        prevPos = new Random().nextInt(shuffledList.size());
                    } else {
                        prevPos = (currentShufflePos - 1 + shuffledList.size()) % shuffledList.size();
                    }
                    String prevFolder = shuffledList.get(prevPos);
                    prevPos = musicFolders.indexOf(prevFolder);
                }
                break;
            case LOOP:
                prevPos = currentPosition;
                break;
            case SEQUENTIAL:
            default:
                if (currentPosition == -1) {
                    prevPos = musicFolders.size() - 1;
                } else {
                    prevPos = (currentPosition - 1 + musicFolders.size()) % musicFolders.size();
                }
                break;
        }
        playMusic(prevPos);
    }
    //快退
    private void fastRewind() {
        if (exoPlayer != null && isPlaying) {
            long current = exoPlayer.getCurrentPosition();
            exoPlayer.seekTo(Math.max(0, current - 5000));
            updatePlayTimeDisplay();
        } else {
            Toast.makeText(this, "请先开始播放", Toast.LENGTH_SHORT).show();
        }
    }
    //快进
    private void fastForward() {
        if (exoPlayer != null && isPlaying) {
            long current = exoPlayer.getCurrentPosition();
            exoPlayer.seekTo(current + 5000);
            updatePlayTimeDisplay();
        } else {
            Toast.makeText(this, "请先开始播放", Toast.LENGTH_SHORT).show();
        }
    }
    //播放下一首
    private void playNext() {
        if (musicFolders.isEmpty()) {
            Toast.makeText(this, "没有可播放的音乐", Toast.LENGTH_SHORT).show();
            return;
        }
        int nextPos;
        switch (playMode) {
            case RANDOM:
                if (shuffledList.isEmpty()) {
                    nextPos = 0;
                } else {
                    int currentShufflePos = getCurrentShufflePosition();
                    if (currentShufflePos == -1) {
                        nextPos = new Random().nextInt(shuffledList.size());
                    } else {
                        nextPos = (currentShufflePos + 1) % shuffledList.size();
                    }
                    String nextFolder = shuffledList.get(nextPos);
                    nextPos = musicFolders.indexOf(nextFolder);
                }
                break;
            case LOOP:
                nextPos = currentPosition;
                break;
            case SEQUENTIAL:
            default:
                if (currentPosition == -1) {
                    nextPos = 0;
                } else {
                    nextPos = (currentPosition + 1) % musicFolders.size();
                }
                break;
        }
        playMusic(nextPos);
    }

    //--------------------------------------------------------------------------//

    /**
     * 定时设置
     */
    //启动
    private void startSleepTimer(int minutes) {
        if (!isSleepTimerActive) {
            sleepTime = minutes;
            sleepTimerEndTime = System.currentTimeMillis() + (minutes * 60 * 1000L);
            long delay = minutes * 60 * 1000L;
            sleepTimerHandler.postDelayed(sleepTimerRunnable, delay);
            sleepTimeUpdateHandler.post(sleepTimeUpdataRunnable);
            isSleepTimerActive = true;
        }
    }
    //结束
    private void setupSleepTimer() {
        sleepTimerRunnable = () -> {
            updatePlayTimeDisplay();
            if (exoPlayer != null && exoPlayer.isPlaying()) {
                exoPlayer.pause();
            }
            isPlaying = false;
            updateUI();

            playTimeUpdateHandler.removeCallbacks(timeUpdateRunnable);

            Toast.makeText(MainActivity.this, "定时关闭时间到，播放已停止", Toast.LENGTH_LONG).show();
            isSleepTimerActive = false;

            if (isServiceBound && musicService != null) {
                musicService.updatePlaybackState(currentPosition, isPlaying);
            }
            if(isPowerOff){
                Intent intent = new Intent();
                intent.setClassName("com.mediatek.schpwronoff", "com.mediatek.schpwronoff.ShutdownActivity");
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    // 处理Activity不存在的情况
                    Toast.makeText(this, "关机失败because:" + e, Toast.LENGTH_SHORT).show();
                }
            }
        };
    }
    //销毁
    private void cancelSleepTimer() {
        sleepTimerHandler.removeCallbacks(sleepTimerRunnable);
        isSleepTimerActive = false;
        sleepTime = -1;
    }

    //--------------------------------------------------------------------------//

    /**
     * 退出、结束程序
     */
    @Override
    public void onBackPressed() {
        if (isPlaying) {
            // 如果正在播放，转到后台播放
            shouldPlayInBackground = true;
            savePlaybackState();
            moveTaskToBack(true);
            Toast.makeText(this, "音乐在后台播放中", Toast.LENGTH_SHORT).show();
        } else {
            // 如果没有播放，正常退出
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (batteryReceiver != null) {
            try {
                unregisterReceiver(batteryReceiver);
            } catch (IllegalArgumentException e) {
                // 接收器可能未注册，忽略异常
            }
        }
        savePlaybackState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 注销广播接收器
        unregisterReceiver(playMusicReceiver);

        // 注销电量广播接收器(不知道为什么反正不能注销。。。)
        //if (batteryReceiver != null) {unregisterReceiver(batteryReceiver);}

        // 停止时钟更新
        if (timeUpdateTask != null) {
            timeHandler.removeCallbacks(timeUpdateTask);
        }
        // 保存播放状态
        savePlaybackState();

        // 如果不应该在后台播放，停止服务
        if (!shouldPlayInBackground) {
            if (isServiceBound) {
                unbindService(serviceConnection);
                isServiceBound = false;
            }
            Intent serviceIntent = new Intent(this, MusicService.class);
            stopService(serviceIntent);
        }

        cancelSleepTimer();
        playTimeUpdateHandler.removeCallbacks(timeUpdateRunnable);
        sleepTimeUpdateHandler.removeCallbacks(sleepTimeUpdataRunnable);
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
        longPressHandler.removeCallbacksAndMessages(null);
        keyLongPressHandler.removeCallbacksAndMessages(null);
        cooldownHandler.removeCallbacksAndMessages(null);
    }
}