package com.prismOS.bilimusic;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MainActivity extends Activity {

    private ListView musicListView;
    private TextView currentMusicText;
    private TextView playtimeText;
    private ImageButton settingsButton, infoButton, modeButton, prevButton, playButton, nextButton;

    private final List<String> musicFolders = new ArrayList<>();
    private final List<String> shuffledList = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private ExoPlayer exoPlayer;

    private int currentPosition = -1;
    private PlayMode playMode = PlayMode.SEQUENTIAL;
    private boolean isPlaying = false;
    private float playSpeed = 1.0f;

    private final Handler longPressHandler = new Handler();
    private final Handler keyLongPressHandler = new Handler();
    private final Handler cooldownHandler = new Handler();
    private final Handler timeUpdateHandler = new Handler();

    private final Handler sleepTimerHandler = new Handler();
    private Runnable sleepTimerRunnable;
    private boolean isSleepTimerActive = false;

    private boolean isKeyLongPress = false;

    private boolean isCooldownActive = false;
    private static final long COOLDOWN_DURATION = 1000;

    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "MusicPlayerPrefs";
    private static final String KEY_SCAN_PATH = "scan_path";
    private static final String KEY_MUSIC_FILE = "music_file";
    private static final String KEY_PLAY_SPEED = "play_speed";
    private static final String KEY_LAST_POSITION = "last_position";
    private static final String KEY_LAST_PLAYBACK_POSITION = "last_playback_position";
    private static final String KEY_IS_PLAYING = "is_playing";
    private static final String KEY_PLAY_MODE = "play_mode";

    private static final String DEFAULT_SCAN_PATH = "/storage/sdcard1/Android/media/com.RobinNotBad.BiliClient/Folder1";
    private static final String DEFAULT_MUSIC_FILE = "video.mp4";

    private String SCAN_PATH;
    private String MUSIC_FILE;

    private MusicService musicService;
    private boolean isServiceBound = false;
    private boolean shouldPlayInBackground = false;

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

    private final Runnable timeUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimeDisplay();
            timeUpdateHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupSharedPreferences();
        initViews();
        scanMusicFolders();
        setupExoPlayer();
        setupButtonListeners();
        setupSleepTimer();

        startAndBindMusicService();

        playSpeed = sharedPreferences.getFloat(KEY_PLAY_SPEED, 1.0f);

        // 恢复上次播放状态
        restorePlaybackState();
    }

    private void startAndBindMusicService() {
        Intent serviceIntent = new Intent(this, MusicService.class);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);
    }

    private void setupSharedPreferences() {
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SCAN_PATH = sharedPreferences.getString(KEY_SCAN_PATH, DEFAULT_SCAN_PATH);
        MUSIC_FILE = sharedPreferences.getString(KEY_MUSIC_FILE, DEFAULT_MUSIC_FILE);
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
                modeButton.setImageResource(android.R.drawable.ic_menu_sort_alphabetically);
                break;
            case "LOOP":
                playMode = PlayMode.LOOP;
                modeButton.setImageResource(android.R.drawable.ic_menu_rotate);
                break;
            default:
                playMode = PlayMode.SEQUENTIAL;
                modeButton.setImageResource(android.R.drawable.ic_media_next);
                break;
        }

        if (lastPosition != -1 && lastPosition < musicFolders.size()) {
            currentPosition = lastPosition;

            currentMusicText.setText(musicFolders.get(currentPosition));

            if (!wasPlaying && !musicFolders.isEmpty()) {
                // 设置播放状态为false，这样播放按钮会显示暂停图标
                isPlaying = false;
                updateUI();
                // 准备播放器但不自动播放，等待用户按下播放按钮
                prepareMusicForPlayback(currentPosition, lastPlaybackPosition);
            } else {
                // 如果不是播放状态，只更新UI显示
                wasPlaying = false;
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
            File musicFile = findMusicFile(folderName);

            if (musicFile != null && musicFile.exists()) {
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
            e.printStackTrace();
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

    private void initViews() {
        musicListView = findViewById(R.id.musicListView);
        currentMusicText = findViewById(R.id.currentMusicText);
        playtimeText = findViewById(R.id.playtimeText);
        settingsButton = findViewById(R.id.settingsButton);
        infoButton = findViewById(R.id.infoButton);
        modeButton = findViewById(R.id.modeButton);
        prevButton = findViewById(R.id.prevButton);
        playButton = findViewById(R.id.playButton);
        nextButton = findViewById(R.id.nextButton);

        adapter = new ArrayAdapter<>(this, R.layout.list_item_music, R.id.folderNameText, musicFolders) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull android.view.ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView folderNameText = view.findViewById(R.id.folderNameText);
                ImageView playingIndicator = view.findViewById(R.id.playingIndicator);

                if (folderNameText != null) {
                    folderNameText.setText(getItem(position));
                }

                if (position == currentPosition && isPlaying) {
                    if (playingIndicator != null) playingIndicator.setVisibility(View.VISIBLE);
                    if (folderNameText != null)
                        folderNameText.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
                } else {
                    if (playingIndicator != null) playingIndicator.setVisibility(View.GONE);
                    if (folderNameText != null)
                        folderNameText.setTextColor(getResources().getColor(android.R.color.white));
                }
                return view;
            }
        };

        musicListView.post(() -> {
            musicListView.setAdapter(adapter);
            musicListView.setOnItemClickListener((parent, view, position, id) -> playMusic(position));
        });

        updateTimeDisplay();
        timeUpdateHandler.post(timeUpdateRunnable);
    }

    @SuppressLint({"DefaultLocale", "SetTextI18n"})
    private void updateTimeDisplay() {
        if (playtimeText == null) {
            return;
        }

        if (exoPlayer != null && isPlaying) {
            try {
                long currentPosition = exoPlayer.getCurrentPosition();
                long duration = exoPlayer.getDuration();

                String currentTime = formatTime(currentPosition);
                String totalTime = formatTime(duration);

                playtimeText.setText(currentTime + "/" + totalTime);
            } catch (Exception e) {
                playtimeText.setText("00:00/00:00");
            }
        } else {
            playtimeText.setText("00:00/00:00");
        }
    }

    @SuppressLint("DefaultLocale")
    private String formatTime(long milliseconds) {
        long totalSeconds = milliseconds / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

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

    private void scanMusicFolders() {
        musicFolders.clear();
        shuffledList.clear();
        File baseDir = new File(SCAN_PATH);

        if (baseDir.exists() && baseDir.isDirectory()) {
            scanDirectory(baseDir);
            // 初始化打乱列表
            shuffledList.addAll(musicFolders);
            // 如果当前是随机模式，打乱列表
            if (playMode == PlayMode.RANDOM) {
                Collections.shuffle(shuffledList);
            }
        } else {
            Toast.makeText(this, "扫描路径不存在: " + SCAN_PATH, Toast.LENGTH_LONG).show();
        }

        adapter.notifyDataSetChanged();
    }

    private void scanDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                File musicFile = new File(file, MUSIC_FILE);
                if (musicFile.exists() && musicFile.isFile()) {
                    musicFolders.add(file.getName());
                }
                scanDirectory(file);
            }
        }
    }

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

        setupTouchListeners();
        setupKeyListeners();
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
                if (currentPosition == -1) {
                    Toast.makeText(this, "下一首: " + musicFolders.get(0), Toast.LENGTH_SHORT).show();
                } else {
                    int nextPos = (currentPosition + 1) % musicFolders.size();
                    Toast.makeText(this, "下一首: " + musicFolders.get(nextPos), Toast.LENGTH_SHORT).show();
                }
                break;
            case RANDOM:
                if (shuffledList.isEmpty()) {
                    Toast.makeText(this, "播放列表为空", Toast.LENGTH_SHORT).show();
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

    private void setupKeyListeners() {
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

    @Override
    protected void onResume() {
        super.onResume();
        String newScanPath = sharedPreferences.getString(KEY_SCAN_PATH, DEFAULT_SCAN_PATH);
        String newMusicFile = sharedPreferences.getString(KEY_MUSIC_FILE, DEFAULT_MUSIC_FILE);
        int timedOffMinutes = sharedPreferences.getInt(SettingsActivity.KEY_TIMED_OFF, 0);
        float newPlaySpeed = sharedPreferences.getFloat(KEY_PLAY_SPEED, 1.0f);

        if (!newScanPath.equals(SCAN_PATH) || !newMusicFile.equals(MUSIC_FILE)) {
            SCAN_PATH = newScanPath;
            MUSIC_FILE = newMusicFile;
            scanMusicFolders();
        }

        if (newPlaySpeed != playSpeed) {
            setPlaySpeed(newPlaySpeed);
        }

        if (timedOffMinutes > 0 && !isSleepTimerActive) {
            cancelSleepTimer();
            startSleepTimer(timedOffMinutes);
        }
    }

    @SuppressLint("SetTextI18n")
    private void playMusic(int position) {
        if (position < 0 || position >= musicFolders.size()) return;

        try {
            if (exoPlayer.isPlaying()) {
                exoPlayer.stop();
            }

            String folderName = musicFolders.get(position);
            File musicFile = findMusicFile(folderName);

            if (musicFile != null && musicFile.exists()) {
                Uri audioUri = Uri.fromFile(musicFile);
                MediaItem mediaItem = MediaItem.fromUri(audioUri);

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

                if (isServiceBound && musicService != null) {
                    musicService.updatePlaybackState(currentPosition, isPlaying);
                }

                timeUpdateHandler.post(timeUpdateRunnable);
                savePlaybackState(); // 保存播放状态

            } else {
                Toast.makeText(this, "找不到音乐文件: " + folderName, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "播放失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private File findMusicFile(String folderName) {
        return findMusicFileRecursive(new File(SCAN_PATH), folderName);
    }

    private File findMusicFileRecursive(File dir, String targetFolderName) {
        File[] files = dir.listFiles();
        if (files == null) return null;

        for (File file : files) {
            if (file.isDirectory()) {
                if (file.getName().equals(targetFolderName)) {
                    File musicFile = new File(file, MUSIC_FILE);
                    if (musicFile.exists()) {
                        return musicFile;
                    }
                }
                File result = findMusicFileRecursive(file, targetFolderName);
                if (result != null) return result;
            }
        }
        return null;
    }

    private void togglePlayPause() {
        if (exoPlayer == null) return;

        if (isPlaying) {
            // 暂停播放
            exoPlayer.pause();
            isPlaying = false;
            playButton.setImageResource(android.R.drawable.ic_media_play);
            cancelSleepTimer();
            timeUpdateHandler.removeCallbacks(timeUpdateRunnable);
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
                playButton.setImageResource(android.R.drawable.ic_media_pause);
                timeUpdateHandler.post(timeUpdateRunnable);

                // 更新服务状态
                if (isServiceBound && musicService != null) {
                    musicService.updatePlaybackState(currentPosition, isPlaying);
                }
            }
        }

        updateUI();
        savePlaybackState(); // 保存状态变更
    }

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
                Toast.makeText(this, "单曲循环中", Toast.LENGTH_SHORT).show();
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

    private void playPrevious() {
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
                Toast.makeText(this, "单曲循环中", Toast.LENGTH_SHORT).show();
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

    private void switchPlayMode() {
        switch (playMode) {
            case SEQUENTIAL:
                playMode = PlayMode.RANDOM;
                modeButton.setImageResource(android.R.drawable.ic_menu_sort_alphabetically);
                // 打乱列表
                Collections.shuffle(shuffledList);
                break;
            case RANDOM:
                playMode = PlayMode.LOOP;
                modeButton.setImageResource(android.R.drawable.ic_menu_rotate);
                break;
            case LOOP:
                playMode = PlayMode.SEQUENTIAL;
                modeButton.setImageResource(android.R.drawable.ic_media_next);
                break;
        }

        if (isServiceBound && musicService != null) {
            musicService.updatePlaybackState(currentPosition, isPlaying);
        }

        // 保存播放模式
        savePlaybackState();
    }

    private void fastForward() {
        //不判断exoPlayer.isPlaying()了
        if (exoPlayer != null) {
            long current = exoPlayer.getCurrentPosition();
            exoPlayer.seekTo(current + 5000);
            updateTimeDisplay();
        } else {
            Toast.makeText(this, "请先开始播放", Toast.LENGTH_SHORT).show();
        }
    }

    private void fastRewind() {
        //不判断exoPlayer.isPlaying()了
        if (exoPlayer != null ) {
            long current = exoPlayer.getCurrentPosition();
            exoPlayer.seekTo(Math.max(0, current - 5000));
            updateTimeDisplay();
        } else {
            Toast.makeText(this, "请先开始播放", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupSleepTimer() {
        sleepTimerRunnable = () -> {
            if (exoPlayer != null && exoPlayer.isPlaying()) {
                exoPlayer.stop();
            }
            isPlaying = false;
            updateUI();

            timeUpdateHandler.removeCallbacks(timeUpdateRunnable);
            updateTimeDisplay();

            Toast.makeText(MainActivity.this, "定时关闭时间到，播放已停止", Toast.LENGTH_LONG).show();
            isSleepTimerActive = false;

            if (isServiceBound && musicService != null) {
                musicService.updatePlaybackState(currentPosition, isPlaying);
            }
        };
    }

    private void startSleepTimer(int minutes) {
        if (!isSleepTimerActive) {
            long delay = minutes * 60 * 1000L;
            sleepTimerHandler.postDelayed(sleepTimerRunnable, delay);
            isSleepTimerActive = true;
            Toast.makeText(this, minutes + "分钟后自动关闭", Toast.LENGTH_SHORT).show();
        }
    }

    private void cancelSleepTimer() {
        sleepTimerHandler.removeCallbacks(sleepTimerRunnable);
        isSleepTimerActive = false;
    }

    private void updateUI() {
        adapter.notifyDataSetChanged();

        if (isPlaying) {
            playButton.setImageResource(android.R.drawable.ic_media_pause);
        } else {
            playButton.setImageResource(android.R.drawable.ic_media_play);
        }
    }

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
        savePlaybackState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

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
        timeUpdateHandler.removeCallbacks(timeUpdateRunnable);
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
        longPressHandler.removeCallbacksAndMessages(null);
        keyLongPressHandler.removeCallbacksAndMessages(null);
        cooldownHandler.removeCallbacksAndMessages(null);
    }
}