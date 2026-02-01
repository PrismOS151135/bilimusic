package com.prismOS.bilimusic;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MusicService extends Service {
    private final IBinder binder = new MusicBinder();
    private ExoPlayer exoPlayer;
    protected static int currentPosition = -1;
    protected static boolean isPlaying = false;
    protected PlayMode playMode = PlayMode.SEQUENTIAL;
    private float playSpeed = 1.0f;
    private final Object playerLock = new Object();

    // 音乐数据
    private final List<String> musicFolders = new ArrayList<>();
    private final List<String> shuffledList = new ArrayList<>();
    private final Map<String, String> folderPathMap = new HashMap<>();

    // 广播相关 - 修复ACTION常量名
    public static final String ACTION_PLAY = "com.prismOS.bilimusic.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.prismOS.bilimusic.ACTION_PAUSE";
    public static final String ACTION_PLAY_POSITION = "com.prismOS.bilimusic.ACTION_PLAY_POSITION";
    public static final String ACTION_PREVIOUS = "com.prismOS.bilimusic.ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "com.prismOS.bilimusic.ACTION_NEXT";
    public static final String ACTION_SEEK_TO = "com.prismOS.bilimusic.ACTION_SEEK_TO";
    public static final String ACTION_CHANGE_MODE = "com.prismOS.bilimusic.ACTION_CHANGE_MODE";
    public static final String ACTION_CHANGE_SPEED = "com.prismOS.bilimusic.ACTION_CHANGE_SPEED";
    public static final String ACTION_ERROR = "com.prismOS.bilimusic.ACTION_ERROR"; // 修复常量名
    public static final String EXTRA_ERROR_MESSAGE = "error_message";
    public static final String ACTION_UPDATE_STATE = "com.prismOS.bilimusic.ACTION_UPDATE_STATE";
    public static final String EXTRA_POSITION = "position";
    public static final String EXTRA_IS_PLAYING = "is_playing";
    public static final String EXTRA_CURRENT_TIME = "current_time";
    public static final String EXTRA_TOTAL_TIME = "total_time";
    public static final String EXTRA_PLAY_MODE = "play_mode";
    public static final String EXTRA_PLAY_SPEED = "play_speed";
    public static final String EXTRA_SEEK_POSITION = "seek_position";

    private SharedPreferences sharedPreferences;
    private BroadcastReceiver controlReceiver;

    public class MusicBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("MusicService", "Service onCreate");

        sharedPreferences = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        setupExoPlayer();
        setupControlReceiver();

        // 恢复播放状态
        restorePlaybackState();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("MusicService", "Service onStartCommand");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void setupExoPlayer() {
        exoPlayer = new ExoPlayer.Builder(this).build();

        exoPlayer.setVideoSurface(null);

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                Log.d("MusicService", "Playback state: " + playbackState);
                if (playbackState == Player.STATE_ENDED) {
                    Log.d("MusicService", "播放结束，准备下一首");
                    playNext();
                } else if (playbackState == Player.STATE_READY) {
                    Log.d("MusicService", "播放器准备就绪");
                    sendUpdateBroadcast();
                } else if (playbackState == Player.STATE_BUFFERING) {
                    Log.d("MusicService", "播放器正在缓冲");
                } else if (playbackState == Player.STATE_IDLE) {
                    Log.d("MusicService", "播放器空闲");
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Log.e("MusicService", "播放错误: " + error.getMessage());
                sendErrorBroadcast("播放错误: " + error.getMessage());
                
                // 尝试播放下一首
                if (!musicFolders.isEmpty()) {
                    new android.os.Handler().postDelayed(() -> playNext(), 1000);
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if(exoPlayer.getPlaybackState() != Player.STATE_BUFFERING){
                    Log.d("MusicService", "播放状态变化(): " + isPlaying);
                    MusicService.isPlaying = isPlaying;
                    sendUpdateBroadcast();
                }
            }
        });
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void setupControlReceiver() {
        controlReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action == null) return;

                Log.d("MusicService", "Received action: " + action);
                int position = intent.getIntExtra(EXTRA_POSITION, -1);
                switch (action) {
                    case ACTION_PAUSE:
                        togglePlayPause();
                        break;
                    case ACTION_PLAY_POSITION:
                        if (position != -1) {
                            playMusic(position);
                        } else {
                            pause();
                        }
                        break;
                    case ACTION_PREVIOUS:
                        playPrevious();
                        break;
                    case ACTION_NEXT:
                        playNext();
                        break;
                    case ACTION_SEEK_TO:
                        if (exoPlayer != null) {
                            long seekPosition = intent.getLongExtra(EXTRA_SEEK_POSITION, 0);
                            exoPlayer.seekTo(seekPosition);
                        }
                        break;
                    case ACTION_CHANGE_MODE:
                        String mode = intent.getStringExtra(EXTRA_PLAY_MODE);
                        if (mode != null) {
                            try {
                                changePlayMode(PlayMode.valueOf(mode));
                            } catch (IllegalArgumentException e) {
                                Log.e("MusicService", "Invalid play mode: " + mode);
                            }
                        }
                        break;
                    case ACTION_CHANGE_SPEED:
                        float speed = intent.getFloatExtra(EXTRA_PLAY_SPEED, 1.0f);
                        setPlaySpeed(speed);
                        break;
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PAUSE);
        filter.addAction(ACTION_PLAY_POSITION);
        filter.addAction(ACTION_PREVIOUS);
        filter.addAction(ACTION_NEXT);
        filter.addAction(ACTION_SEEK_TO);
        filter.addAction(ACTION_CHANGE_MODE);
        filter.addAction(ACTION_CHANGE_SPEED);
        registerReceiver(controlReceiver, filter);
    }

    // 播放控制方法
    public void playMusic(int position) {
        synchronized (playerLock) {
            if (musicFolders.isEmpty()) {
                sendErrorBroadcast("没有可播放的音乐");
                return;
            }

            if (position < 0 || position >= musicFolders.size()) {
                position = 0; // 默认播放第一首
            }

            try {
                String folderName = musicFolders.get(position);
                String folderPath = folderPathMap.get(folderName);

                if (folderPath == null) {
                    sendErrorBroadcast("找不到音乐文件夹路径");
                    return;
                }

                // 获取音乐文件名
                String musicFileName = getMusicFileName();
                File musicFile = new File(folderPath, musicFileName);

                Log.d("MusicService", "尝试播放: " + musicFile.getAbsolutePath());
                Log.d("MusicService", "文件存在: " + musicFile.exists());
                Log.d("MusicService", "文件大小: " + (musicFile.exists() ? musicFile.length() : 0));

                if (musicFile.exists() && musicFile.length() > 0) {
                    Uri audioUri = Uri.fromFile(musicFile);

                    // 停止当前播放
                    if (exoPlayer.isPlaying()) {
                        exoPlayer.stop();
                    }

                    exoPlayer.setMediaItem(MediaItem.fromUri(audioUri));
                    exoPlayer.prepare();
                    exoPlayer.play();

                    currentPosition = position;
                    isPlaying = true;

                    // 设置播放速度
                    if (playSpeed != 1.0f) {
                        setPlaySpeed(playSpeed);
                    }

                    savePlaybackState();
                    sendUpdateBroadcast();

                    Log.d("MusicService", "开始播放: " + folderName);
                } else {
                    String errorMsg = "找不到音乐文件: " + musicFileName;
                    Log.e("MusicService", errorMsg);
                    sendErrorBroadcast(errorMsg);

                    // 尝试播放下一首
                    if (musicFolders.size() > 1) {
                        int nextPos = (position + 1) % musicFolders.size();
                        if (nextPos != position) {
                            new android.os.Handler().postDelayed(() -> playMusic(nextPos), 1000);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("MusicService", "playMusic错误：", e);
                sendErrorBroadcast("播放失败: " + e.getMessage());
            }
        }
    }

    public void togglePlayPause() {
        if (exoPlayer == null) {
            Log.e("MusicService", "ExoPlayer is null");
            return;
        }

        if (currentPosition == -1 && !musicFolders.isEmpty()) {
            // 如果没有当前播放的音乐，播放第一首
            playMusic(0);
        } else if (currentPosition != -1) {
            if (isPlaying) {
                pause();
            } else {
                play();
            }
        } else {
            sendErrorBroadcast("没有可播放的音乐");
        }
    }

    private void play() {
        if (exoPlayer == null) return;
        
        if (exoPlayer.getMediaItemCount() == 0 && currentPosition != -1) {
            // 重新准备当前音乐
            playMusic(currentPosition);
        } else {
            exoPlayer.play();
            isPlaying = true;
            savePlaybackState();
            sendUpdateBroadcast();
            Log.d("MusicService", "继续播放");
        }
    }

    private void pause() {
        if (exoPlayer != null) {
            exoPlayer.pause();
            isPlaying = false;
            savePlaybackState();
            sendUpdateBroadcast();
            Log.d("MusicService", "暂停播放");
        }
    }

    public void playPrevious() {
        if (musicFolders.isEmpty()) {
            sendErrorBroadcast("没有可播放的音乐");
            return;
        }

        int prevPos = calculatePreviousPosition();
        playMusic(prevPos);
    }

    public void playNext() {
        if (musicFolders.isEmpty()) {
            sendErrorBroadcast("没有可播放的音乐");
            return;
        }

        int nextPos = calculateNextPosition();
        playMusic(nextPos);
    }

    private int calculatePreviousPosition() {
        if (musicFolders.isEmpty()) return 0;
        
        if (currentPosition == -1) {
            return musicFolders.size() - 1;
        }
        
        switch (playMode) {
            case RANDOM:
                if (shuffledList.isEmpty()) return 0;
                int currentShufflePos = getCurrentShufflePosition();
                if (currentShufflePos == -1) {
                    return new Random().nextInt(shuffledList.size());
                } else {
                    int prevPos = (currentShufflePos - 1 + shuffledList.size()) % shuffledList.size();
                    String prevFolder = shuffledList.get(prevPos);
                    return musicFolders.indexOf(prevFolder);
                }
            case LOOP:
                if (MainActivity.isLoop) {
                    return currentPosition;
                } else {
                    return (currentPosition - 1 + musicFolders.size()) % musicFolders.size();
                }
            case SEQUENTIAL:
            default:
                return (currentPosition - 1 + musicFolders.size()) % musicFolders.size();
        }
    }

    private int calculateNextPosition() {
        if (musicFolders.isEmpty()) return 0;
        
        if (currentPosition == -1) {
            return 0;
        }
        
        switch (playMode) {
            case RANDOM:
                if (shuffledList.isEmpty()) return 0;
                int currentShufflePos = getCurrentShufflePosition();
                if (currentShufflePos == -1) {
                    return new Random().nextInt(shuffledList.size());
                } else {
                    int nextPos = (currentShufflePos + 1) % shuffledList.size();
                    String nextFolder = shuffledList.get(nextPos);
                    return musicFolders.indexOf(nextFolder);
                }
            case LOOP:
                if (MainActivity.isLoop) {
                    return currentPosition;
                } else {
                    return (currentPosition + 1) % musicFolders.size();
                }
            case SEQUENTIAL:
            default:
                return (currentPosition + 1) % musicFolders.size();
        }
    }

    private int getCurrentShufflePosition() {
        if (currentPosition == -1 || currentPosition >= musicFolders.size()) return -1;
        String currentFolder = musicFolders.get(currentPosition);
        return shuffledList.indexOf(currentFolder);
    }

    private void changePlayMode(PlayMode newMode) {
        playMode = newMode;
        if (newMode == PlayMode.RANDOM) {
            Collections.shuffle(shuffledList);
            Log.d("MusicService", "切换到随机播放模式");
        } else if (newMode == PlayMode.LOOP) {
            MainActivity.isLoop = true;
            Log.d("MusicService", "切换到单曲循环模式");
        } else {
            MainActivity.isLoop = false;
            Log.d("MusicService", "切换到顺序播放模式");
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(MainActivity.KEY_PLAY_MODE, newMode.name());
        editor.apply();

        sendUpdateBroadcast();
    }

    private void setPlaySpeed(float speed) {
        this.playSpeed = speed;

        if (exoPlayer != null) {
            PlaybackParameters playbackParameters = new PlaybackParameters(speed);
            exoPlayer.setPlaybackParameters(playbackParameters);
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat(MainActivity.KEY_PLAY_SPEED, speed);
        editor.apply();

        sendUpdateBroadcast();
        Log.d("MusicService", "播放速度设置为: " + speed);
    }

    // 广播发送方法
    private void sendUpdateBroadcast() {
        Intent intent = new Intent(ACTION_UPDATE_STATE);
        intent.putExtra(EXTRA_POSITION, currentPosition);
        intent.putExtra(EXTRA_IS_PLAYING, isPlaying);
        intent.putExtra(EXTRA_PLAY_MODE, playMode.name());
        intent.putExtra(EXTRA_PLAY_SPEED, playSpeed);

        if (exoPlayer != null) {
            intent.putExtra(EXTRA_CURRENT_TIME, exoPlayer.getCurrentPosition());
            intent.putExtra(EXTRA_TOTAL_TIME, exoPlayer.getDuration());
        }

        sendBroadcast(intent);
        Log.d("MusicService", "发送更新广播: position=" + currentPosition + ", isPlaying=" + isPlaying);
    }

    private void sendErrorBroadcast(String message) {
        Intent intent = new Intent(ACTION_ERROR);
        intent.putExtra(EXTRA_ERROR_MESSAGE, message);
        sendBroadcast(intent);
        Log.e("MusicService", "发送错误广播: " + message);
    }

    // 数据管理方法
    public void updateMusicData(List<String> folders, Map<String, String> pathMap, List<String> shuffled) {
        musicFolders.clear();
        folderPathMap.clear();
        shuffledList.clear();

        musicFolders.addAll(folders);
        folderPathMap.putAll(pathMap);
        shuffledList.addAll(shuffled);

        Log.d("MusicService", "更新音乐数据: " + musicFolders.size() + " 首歌曲");
        
        if (playMode == PlayMode.RANDOM) {
            Collections.shuffle(shuffledList);
        }
    }

    private String getMusicFileName() {
        String fileName = sharedPreferences.getString(MainActivity.KEY_MUSIC_FILE, MainActivity.DEFAULT_MUSIC_FILE);
        Log.d("MusicService", "获取音乐文件名: " + fileName);
        return fileName;
    }

    // 状态保存与恢复
    private void savePlaybackState() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(MainActivity.KEY_LAST_POSITION, currentPosition);
        editor.putBoolean(MainActivity.KEY_IS_PLAYING, isPlaying);
        editor.putString(MainActivity.KEY_PLAY_MODE, playMode.name());

        if (exoPlayer != null && exoPlayer.getCurrentPosition() > 0) {
            editor.putLong(MainActivity.KEY_LAST_PLAYBACK_POSITION, exoPlayer.getCurrentPosition());
        }
        editor.apply();
        
        Log.d("MusicService", "保存播放状态: position=" + currentPosition + ", isPlaying=" + isPlaying);
    }

    private void restorePlaybackState() {
        int lastPosition = sharedPreferences.getInt(MainActivity.KEY_LAST_POSITION, -1);
        boolean wasPlaying = sharedPreferences.getBoolean(MainActivity.KEY_IS_PLAYING, false);
        String savedPlayMode = sharedPreferences.getString(MainActivity.KEY_PLAY_MODE, "SEQUENTIAL");

        try {
            playMode = PlayMode.valueOf(savedPlayMode);
        } catch (IllegalArgumentException e) {
            playMode = PlayMode.SEQUENTIAL;
        }
        
        playSpeed = sharedPreferences.getFloat(MainActivity.KEY_PLAY_SPEED, 1.0f);

        Log.d("MusicService", "恢复播放状态: lastPosition=" + lastPosition + 
              ", wasPlaying=" + wasPlaying + ", playMode=" + playMode);

        // 注意：这里不立即播放，等待音乐数据加载完成后再决定
        currentPosition = lastPosition;
    }

    // Getters
    public boolean isPlaying() {
        return isPlaying;
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public long getCurrentPlaybackTime() {
        return exoPlayer != null ? exoPlayer.getCurrentPosition() : 0;
    }

    public long getDuration() {
        return exoPlayer != null ? exoPlayer.getDuration() : 0;
    }
    
    public String getNextMusicInfo() {
        if (musicFolders.isEmpty()) {
            return "";
        }
        
        int nextPos = calculateNextPosition();
        if (nextPos >= 0 && nextPos < musicFolders.size()) {
            return musicFolders.get(nextPos);
        }
        return "";
    }

    // 修改onDestroy方法
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("MusicService", "Service onDestroy");

        synchronized (playerLock) {
            if (controlReceiver != null) {
                try {
                    unregisterReceiver(controlReceiver);
                } catch (IllegalArgumentException e) {
                    Log.e("MusicService", "controlReceiver already unregistered", e);
                }
            }

            if (exoPlayer != null) {
                try {
                    exoPlayer.stop();
                    exoPlayer.release();
                } catch (Exception e) {
                    Log.e("MusicService", "Error releasing ExoPlayer", e);
                }
                exoPlayer = null;
            }
        }

        savePlaybackState();
        Log.d("MusicService", "音乐服务已停止");
    }

    public enum PlayMode {
        SEQUENTIAL,
        RANDOM,
        LOOP
    }
}