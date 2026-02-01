package com.prismOS.bilimusic;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
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

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    // UI组件
    private TextView PlayingTime, allPlayTime, RestSleepTime;
    private ImageButton settingsButton, infoButton, modeButton, prevButton, playButton, nextButton, musicListButton;
    private ProgressBar musicProgressBar;
    private ImageView songImage;
    private TextView currentMusicText, electricQuantityText, nowTimeText;
    private GestureDetector gestureDetector;
    private LinearLayout layoutTouch;
    private boolean isDoubleTap = false;
    private View t1, r0, r1, r2, r3, r4, r5;

    // 音乐数据
    protected static final List<String> musicFolders = new ArrayList<>();
    protected static final List<String> shuffledList = new ArrayList<>();
    protected static final Map<String, String> folderPathMap = new HashMap<>();
    protected static String TheMusicText;

    // Service相关
    private MusicService musicService;
    private boolean isServiceBound = false;

    // 广播接收器
    private BroadcastReceiver musicStateReceiver;
    private BroadcastReceiver errorReceiver;

    // 其他状态
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
    protected static boolean isLoop = false;
    private static final long COOLDOWN_DURATION = 1000;
    private static long sleepTime = 0;
    private long sleepTimerEndTime;

    // 时间和电量
    protected final Handler timeHandler = new Handler();
    protected Runnable timeUpdateTask;
    protected BroadcastReceiver batteryReceiver;

    // SharedPreferences相关
    private SharedPreferences sharedPreferences;
    protected static final String PREFS_NAME = "MusicPlayerPrefs";
    protected static final String KEY_SCAN_PATH = "scan_path";
    protected static final String KEY_MUSIC_FILE = "music_file";
    protected static final String KEY_PLAY_SPEED = "play_speed";
    protected static final String KEY_LAST_POSITION = "last_position";
    protected static final String KEY_LAST_PLAYBACK_POSITION = "last_playback_position";
    protected static final String KEY_IS_PLAYING = "is_playing";
    protected static final String KEY_PLAY_MODE = "play_mode";
    protected static final String KEY_IMAGE_SIZE = "image_size";
    protected static final String KEY_TEXT_SIZE = "text_size";
    protected static final String DEFAULT_SCAN_PATH = "/storage/sdcard1/1/Folder1";
    protected static final String DEFAULT_MUSIC_FILE = "video.mp4";
    protected static String SCAN_PATH;
    protected static String MUSIC_FILE;
    protected static int songImageSize;
    protected static int theTextSize;
    protected static int buttonInterval;

    // 后台播放控制
    private boolean shouldPlayInBackground = false;

    // Service连接
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            isServiceBound = true;

            // 将音乐数据传递给Service
            if (musicService != null) {
                musicService.updateMusicData(musicFolders, folderPathMap, shuffledList);
                Log.d("MainActivity", "Service connected and music data sent");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            musicService = null;
            Log.d("MainActivity", "Service disconnected");
        }
    };

    // 播放音乐接收器
    private final BroadcastReceiver playMusicReceiverInternal = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null && intent.getAction().equals(MusicService.ACTION_PLAY)) {
                int position = intent.getIntExtra(MusicService.EXTRA_POSITION, -1);
                if (position != -1) {
                    playMusic(position);
                }
            }
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupSharedPreferences();
        initViews();
        setImageViewSize(songImage, songImageSize);
        setViewSize(r0, buttonInterval);
        setViewSize(r1, buttonInterval);
        setViewSize(r2, buttonInterval);
        setViewSize(r3, buttonInterval);
        setViewSize(r4, buttonInterval);
        setViewSize(r5, buttonInterval);

        if (theTextSize == 0) {
            t1.setVisibility(View.GONE);
            currentMusicText.setVisibility(View.GONE);
        } else {
            currentMusicText.setVisibility(View.VISIBLE);
            t1.setVisibility(View.VISIBLE);
            currentMusicText.setTextSize(theTextSize);
        }

        setupButtonListeners();
        setupGestureDetector();
        setupSleepTimer();
        setupBroadcastReceivers();

        // 注册播放音乐广播接收器
        IntentFilter playFilter = new IntentFilter(MusicService.ACTION_PLAY);
        registerReceiver(playMusicReceiverInternal, playFilter);

        // 注册电量广播接收器
        registerBatteryReceiver();

        // 启动音乐后台Service
        startAndBindMusicService();

        // 扫描音乐文件夹
        scanMusicFolders(SCAN_PATH, MUSIC_FILE);

        // 恢复上次播放状态
        playSpeed = sharedPreferences.getFloat(KEY_PLAY_SPEED, 1.0f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String newScanPath = sharedPreferences.getString(KEY_SCAN_PATH, DEFAULT_SCAN_PATH);
        String newMusicFile = sharedPreferences.getString(KEY_MUSIC_FILE, DEFAULT_MUSIC_FILE);
        int timedOffMinutes = sharedPreferences.getInt(SettingsActivity.KEY_TIMED_OFF, 0);
        float newPlaySpeed = sharedPreferences.getFloat(KEY_PLAY_SPEED, 1.0f);
        isPowerOff = sharedPreferences.getBoolean(SettingsActivity.KEY_IS_POWER_OFF, false);

        songImageSize = sharedPreferences.getInt(KEY_IMAGE_SIZE, 160);
        theTextSize = sharedPreferences.getInt(KEY_TEXT_SIZE, 16);
        buttonInterval = sharedPreferences.getInt(SettingsActivity.KEY_BUTTON_INTERVAL, 0);

        setImageViewSize(songImage, songImageSize);
        setViewSize(r0, buttonInterval);
        setViewSize(r1, buttonInterval);
        setViewSize(r2, buttonInterval);
        setViewSize(r3, buttonInterval);
        setViewSize(r4, buttonInterval);
        setViewSize(r5, buttonInterval);

        registerBatteryReceiver();

        if (theTextSize == 0) {
            t1.setVisibility(View.GONE);
            currentMusicText.setVisibility(View.GONE);
        } else {
            currentMusicText.setVisibility(View.VISIBLE);
            t1.setVisibility(View.VISIBLE);
            currentMusicText.setTextSize(theTextSize);
        }

        if (!newScanPath.equals(SCAN_PATH) || !newMusicFile.equals(MUSIC_FILE)) {
            SCAN_PATH = newScanPath;
            MUSIC_FILE = newMusicFile;
            if (!scanMusicFolders(SCAN_PATH, MUSIC_FILE)) {
                Toast.makeText(this, "扫描路径不存在: " + SCAN_PATH, Toast.LENGTH_LONG).show();
            } else {
                // 更新Service中的音乐数据
                if (isServiceBound && musicService != null) {
                    musicService.updateMusicData(musicFolders, folderPathMap, shuffledList);
                }
            }
        }

        if (newPlaySpeed != playSpeed) {
            playSpeed = newPlaySpeed;
            changePlaySpeed(playSpeed);
        }

        if (timedOffMinutes > 0) {
            if (timedOffMinutes != sleepTime) {
                cancelSleepTimer();
                startSleepTimer(timedOffMinutes);
            }
        } else {
            cancelSleepTimer();
            sleepTimeUpdateHandler.removeCallbacks(sleepTimeUpdateRunnable);
            RestSleepTime.setVisibility(View.GONE);
        }
    }

    private void setImageViewSize(ImageView imageView, int size) {
        if (size == 0) {
            imageView.setVisibility(View.GONE);
            if (t1 != null) t1.setVisibility(View.GONE);
        } else {
            float density = getResources().getDisplayMetrics().density;
            int sizeInPx = (int) (size * density + 0.5f);

            imageView.setVisibility(View.VISIBLE);
            if (t1 != null) t1.setVisibility(View.VISIBLE);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(sizeInPx, sizeInPx);
            imageView.setLayoutParams(layoutParams);
            ((ViewGroup) imageView.getParent()).requestLayout();
        }
    }

    private void setViewSize(View view, int size) {
        if (size == 0) {
            view.setVisibility(View.GONE);
        } else {
            float density = getResources().getDisplayMetrics().density;
            int sizeInPx = (int) (size * density + 0.5f);

            view.setVisibility(View.VISIBLE);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(sizeInPx, 40);
            view.setLayoutParams(layoutParams);
            ((ViewGroup) view.getParent()).requestLayout();
        }
    }

    private void initViews() {
        currentMusicText = findViewById(R.id.currentMusicText);
        PlayingTime = findViewById(R.id.PlayingTime);
        allPlayTime = findViewById(R.id.allPlayTime);
        songImage = findViewById(R.id.songImage);
        musicProgressBar = findViewById(R.id.musicProgressBar);
        settingsButton = findViewById(R.id.settingsButton);
        infoButton = findViewById(R.id.infoButton);
        modeButton = findViewById(R.id.modeButton);
        prevButton = findViewById(R.id.prevButton);
        playButton = findViewById(R.id.playButton);
        nextButton = findViewById(R.id.nextButton);
        musicListButton = findViewById(R.id.musicListButton);
        electricQuantityText = findViewById(R.id.electricQuantity);
        nowTimeText = findViewById(R.id.nowTime);
        RestSleepTime = findViewById(R.id.RestSleepTime);
        layoutTouch = findViewById(R.id.layoutTouch);
        t1 = findViewById(R.id.t1);
        r0 = findViewById(R.id.r0);
        r1 = findViewById(R.id.r1);
        r2 = findViewById(R.id.r2);
        r3 = findViewById(R.id.r3);
        r4 = findViewById(R.id.r4);
        r5 = findViewById(R.id.r5);

        RestSleepTime.setVisibility(View.GONE);

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
        songImageSize = sharedPreferences.getInt(KEY_IMAGE_SIZE, 160);
        theTextSize = sharedPreferences.getInt(KEY_TEXT_SIZE, 16);
        buttonInterval = sharedPreferences.getInt(SettingsActivity.KEY_BUTTON_INTERVAL, 0);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void setupBroadcastReceivers() {
        // 音乐状态更新接收器
        musicStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (MusicService.ACTION_UPDATE_STATE.equals(intent.getAction())) {
                    int position = intent.getIntExtra(MusicService.EXTRA_POSITION, -1);
                    String playMode = intent.getStringExtra(MusicService.EXTRA_PLAY_MODE);
                    long currentTime = intent.getLongExtra(MusicService.EXTRA_CURRENT_TIME, 0);
                    long totalTime = intent.getLongExtra(MusicService.EXTRA_TOTAL_TIME, 0);

                    updateUI(position, playMode);
                    updatePlayTimeDisplay(currentTime, totalTime);

                    if (MusicService.isPlaying) {
                        startTimeUpdate();
                    } else {
                        stopTimeUpdate();
                    }
                }
            }
        };

        // 错误接收器 - 修复ACTION常量
        errorReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (MusicService.ACTION_ERROR.equals(intent.getAction())) {
                    String errorMsg = intent.getStringExtra(MusicService.EXTRA_ERROR_MESSAGE);
                    Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                }
            }
        };

        IntentFilter stateFilter = new IntentFilter(MusicService.ACTION_UPDATE_STATE);
        IntentFilter errorFilter = new IntentFilter(MusicService.ACTION_ERROR);
        registerReceiver(musicStateReceiver, stateFilter);
        registerReceiver(errorReceiver, errorFilter);
    }

    private void updateUI(int position, String playMode) {
        if (MusicService.isPlaying) {
            playButton.setImageResource(R.drawable.btn_stop);
        } else {
            playButton.setImageResource(R.drawable.btn_play);
        }

        if (position != -1 && position < musicFolders.size()) {
            currentMusicText.setText(musicFolders.get(position));
            TheMusicText = currentMusicText.getText().toString().trim();
            loadCoverImage(position);
        }

        // 更新播放模式图标
        if (playMode != null) {
            switch (playMode) {
                case "RANDOM":
                    modeButton.setImageResource(R.drawable.btn_play_random);
                    break;
                case "LOOP":
                    modeButton.setImageResource(R.drawable.btn_play_rotate);
                    break;
                default:
                    modeButton.setImageResource(R.drawable.btn_play_sequential);
                    break;
            }
        }
    }

    private void startTimeUpdate() {
        playTimeUpdateHandler.removeCallbacks(timeUpdateRunnable);
        playTimeUpdateHandler.post(timeUpdateRunnable);
    }

    private void stopTimeUpdate() {
        playTimeUpdateHandler.removeCallbacks(timeUpdateRunnable);
    }

    private final Runnable timeUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (isServiceBound && musicService != null) {
                long currentTime = musicService.getCurrentPlaybackTime();
                long totalTime = musicService.getDuration();
                updatePlayTimeDisplay(currentTime, totalTime);
            }
            playTimeUpdateHandler.postDelayed(this, 1000);
        }
    };

    @SuppressLint("DefaultLocale")
    private void updatePlayTimeDisplay(long currentTime, long totalTime) {
        if (totalTime > 0) {
            int progress = (int) (500 * currentTime / totalTime);
            musicProgressBar.setProgress(progress);
        } else {
            musicProgressBar.setProgress(0);
        }

        String currentTimeStr = formatTime(currentTime);
        String totalTimeStr = formatTime(totalTime);

        PlayingTime.setText(currentTimeStr);
        allPlayTime.setText(totalTimeStr);
    }

    @SuppressLint("DefaultLocale")
    private String formatTime(long milliseconds) {
        if (milliseconds < 0) milliseconds = 0;
        long totalSeconds = milliseconds / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    // 播放时间显示更新
    private final Runnable sleepTimeUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            updateSleepTimeDisplay();
            sleepTimeUpdateHandler.postDelayed(this, 1000);
        }
    };

    private void updateSleepTimeDisplay() {
        long nowSleepTime = sleepTimerEndTime - System.currentTimeMillis();
        if (isSleepTimerActive && nowSleepTime >= 0) {
            RestSleepTime.setVisibility(View.VISIBLE);
            long minutes = nowSleepTime / (60 * 1000);
            long seconds = (nowSleepTime % (60 * 1000)) / 1000;
            RestSleepTime.setText(String.format(Locale.ROOT, "%d分%02d秒", minutes, seconds));
        } else {
            sleepTimeUpdateHandler.removeCallbacks(sleepTimeUpdateRunnable);
            RestSleepTime.setVisibility(View.GONE);
        }
    }

    private void startClockUpdate() {
        timeUpdateTask = new Runnable() {
            @Override
            public void run() {
                updateClockDisplay();
                timeHandler.postDelayed(this, 30000);
            }
        };
        timeHandler.post(timeUpdateTask);
    }

    @SuppressLint("SimpleDateFormat")
    private void updateClockDisplay() {
        if (nowTimeText == null) return;

        SimpleDateFormat sdf = new SimpleDateFormat("ahh:mm", Locale.CHINA);
        String currentTime = sdf.format(new Date());
        nowTimeText.setText(currentTime);
    }

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

    private void loadCoverImage(int position) {
        if (position < 0 || position >= musicFolders.size()) return;
        String folderPath = folderPathMap.get(musicFolders.get(position));
        if (folderPath == null) return;

        RequestOptions requestOptions = new RequestOptions()
                .centerCrop()
                .error(R.drawable.img_error)
                .placeholder(R.drawable.img_loading)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(400, 400);

        File coverFile = new File(folderPath, "cover.png");

        if (coverFile.exists() && coverFile.length() > 0) {
            Glide.with(this)
                    .load(coverFile)
                    .apply(requestOptions)
                    .into(songImage);
        } else {
            Glide.with(this)
                    .load(R.drawable.img_none)
                    .apply(requestOptions)
                    .into(songImage);
        }
    }

    // 播放控制方法
    protected void playMusic(int position) {
        // 发送给MusicService播放指定位置的音乐
        Intent intent = new Intent(MusicService.ACTION_PLAY_POSITION);
        intent.putExtra(MusicService.EXTRA_POSITION, position);
        sendBroadcast(intent);
    }

    public void togglePlayPause() {
        // 发送给MusicService切换播放/暂停
        Intent intent = new Intent(MusicService.ACTION_PAUSE);
        sendBroadcast(intent);
    }

    public static boolean scanMusicFolders(String scanPath, String musicFile) {
        musicFolders.clear();
        shuffledList.clear();
        folderPathMap.clear();

        File baseDir = new File(scanPath);

        if (baseDir.exists() && baseDir.isDirectory()) {
            scanDirectory(baseDir, musicFile);
            shuffledList.addAll(musicFolders);
            Collections.shuffle(shuffledList);
            return true;
        } else {
            return false;
        }
    }

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

    private void changePlaySpeed(float speed) {
        this.playSpeed = speed;
        Intent intent = new Intent(MusicService.ACTION_CHANGE_SPEED);
        intent.putExtra(MusicService.EXTRA_PLAY_SPEED, speed);
        sendBroadcast(intent);

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat(KEY_PLAY_SPEED, speed);
        editor.apply();
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
                if (!isDoubleTap) {
                    isDoubleTap = true;
                    togglePlayPause();
                    new Handler().postDelayed(() -> isDoubleTap = false, 300);
                    return true;
                }
                return false;
            }

            @Override
            public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 100) {
                    isLoop = false;
                    if (diffX > 0) {
                        playPrevious();
                    } else {
                        playNext();
                    }
                    return true;
                }
                return false;
            }
        });

        layoutTouch.setOnTouchListener((v, event) -> {
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

        if (isServiceBound && musicService != null) {
            String nextMusic = musicService.getNextMusicInfo();
            if (nextMusic != null) {
                Toast.makeText(this, "下一首: " + nextMusic, Toast.LENGTH_SHORT).show();
            }
        }
    }

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
                                isLoop = false;
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
                                isLoop = false;
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
                                isLoop = false;
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
                                isLoop = false;
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

    // 播放功能
    private void switchPlayMode() {
        if (isServiceBound && musicService != null) {
            MusicService.PlayMode currentMode = musicService.playMode;
            MusicService.PlayMode nextMode;

            switch (currentMode) {
                case SEQUENTIAL:
                    nextMode = MusicService.PlayMode.RANDOM;
                    break;
                case RANDOM:
                    nextMode = MusicService.PlayMode.LOOP;
                    break;
                case LOOP:
                default:
                    nextMode = MusicService.PlayMode.SEQUENTIAL;
                    break;
            }

            Intent intent = new Intent(MusicService.ACTION_CHANGE_MODE);
            intent.putExtra(MusicService.EXTRA_PLAY_MODE, nextMode.name());
            sendBroadcast(intent);
        }
    }

    public void playPrevious() {
        Intent intent = new Intent(MusicService.ACTION_PREVIOUS);
        sendBroadcast(intent);
    }

    private void fastRewind() {
        if (isServiceBound && musicService != null && musicService.isPlaying()) {
            long current = musicService.getCurrentPlaybackTime();
            long newPosition = Math.max(0, current - 5000);

            Intent intent = new Intent(MusicService.ACTION_SEEK_TO);
            intent.putExtra(MusicService.EXTRA_SEEK_POSITION, newPosition);
            sendBroadcast(intent);

            updatePlayTimeDisplay(newPosition, musicService.getDuration());
        } else {
            Toast.makeText(this, "请先开始播放", Toast.LENGTH_SHORT).show();
        }
    }

    private void fastForward() {
        if (isServiceBound && musicService != null && musicService.isPlaying()) {
            long current = musicService.getCurrentPlaybackTime();
            long newPosition = current + 5000;

            Intent intent = new Intent(MusicService.ACTION_SEEK_TO);
            intent.putExtra(MusicService.EXTRA_SEEK_POSITION, newPosition);
            sendBroadcast(intent);

            updatePlayTimeDisplay(newPosition, musicService.getDuration());
        } else {
            Toast.makeText(this, "请先开始播放", Toast.LENGTH_SHORT).show();
        }
    }

    private void playNext() {
        Intent intent = new Intent(MusicService.ACTION_NEXT);
        sendBroadcast(intent);
    }

    // 定时设置
    private void startSleepTimer(int minutes) {
        if (!isSleepTimerActive) {
            sleepTime = minutes;
            sleepTimerEndTime = System.currentTimeMillis() + (minutes * 60 * 1000L);
            long delay = minutes * 60 * 1000L;
            sleepTimerHandler.postDelayed(sleepTimerRunnable, delay);
            sleepTimeUpdateHandler.post(sleepTimeUpdateRunnable);
            isSleepTimerActive = true;
        }
    }

    private void setupSleepTimer() {
        sleepTimerRunnable = () -> {
            if (isServiceBound && musicService != null && musicService.isPlaying()) {
                Intent pauseIntent = new Intent(MusicService.ACTION_PAUSE);
                sendBroadcast(pauseIntent);
            }

            Toast.makeText(MainActivity.this, "定时关闭时间到，播放已停止", Toast.LENGTH_LONG).show();
            isSleepTimerActive = false;

            if (isPowerOff) {
                Intent intent = new Intent();
                intent.setClassName("com.mediatek.schpwronoff", "com.mediatek.schpwronoff.ShutdownActivity");
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "关机失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        };
    }

    private void cancelSleepTimer() {
        sleepTimerHandler.removeCallbacks(sleepTimerRunnable);
        isSleepTimerActive = false;
        sleepTime = -1;
    }

    @Override
    public void onBackPressed() {
        if (isServiceBound && musicService != null && musicService.isPlaying()) {
            shouldPlayInBackground = true;
            savePlaybackState();
            moveTaskToBack(true);
            Toast.makeText(this, "音乐在后台播放中", Toast.LENGTH_SHORT).show();
        } else {
            super.onBackPressed();
        }
    }

    private void savePlaybackState() {
        if (isServiceBound && musicService != null) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt(KEY_LAST_POSITION, musicService.getCurrentPosition());
            editor.putBoolean(KEY_IS_PLAYING, musicService.isPlaying());
            editor.putFloat(KEY_PLAY_SPEED, playSpeed);
            editor.apply();
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

        // 标记Activity已销毁
        isDoubleTap = false;
        isServiceBound = false;

        // 注销广播接收器
        if (playMusicReceiverInternal != null) {
            try {
                unregisterReceiver(playMusicReceiverInternal);
            } catch (IllegalArgumentException e) {
                Log.e("MainActivity", "playMusicReceiverInternal already unregistered", e);
            }
        }
        if (musicStateReceiver != null) {
            try {
                unregisterReceiver(musicStateReceiver);
            } catch (IllegalArgumentException e) {
                Log.e("MainActivity", "musicStateReceiver already unregistered", e);
            }
        }
        if (errorReceiver != null) {
            try {
                unregisterReceiver(errorReceiver);
            } catch (IllegalArgumentException e) {
                Log.e("MainActivity", "errorReceiver already unregistered", e);
            }
        }

        // 停止时钟更新
        if (timeUpdateTask != null) {
            timeHandler.removeCallbacks(timeUpdateTask);
        }

        // 保存播放状态
        savePlaybackState();

        // 停止时间更新
        playTimeUpdateHandler.removeCallbacks(timeUpdateRunnable);
        sleepTimeUpdateHandler.removeCallbacks(sleepTimeUpdateRunnable);

        // 取消定时器
        cancelSleepTimer();

        // 清理Handler
        longPressHandler.removeCallbacksAndMessages(null);
        keyLongPressHandler.removeCallbacksAndMessages(null);
        cooldownHandler.removeCallbacksAndMessages(null);

        // 解除服务绑定
        if (isServiceBound) {
            try {
                unbindService(serviceConnection);
            } catch (IllegalArgumentException e) {
                Log.e("MainActivity", "Service already unbound", e);
            }
            isServiceBound = false;
        }

        // 停止后台播放
        shouldPlayInBackground = false;

        // 如果不应该在后台播放，停止服务
        if (!shouldPlayInBackground) {
            Intent serviceIntent = new Intent(this, MusicService.class);
            try {
                stopService(serviceIntent);
            } catch (IllegalStateException e) {
                Log.e("MainActivity", "Service already stopped", e);
            }
        }

        // 清理UI引用
        layoutTouch = null;
        songImage = null;
    }
}