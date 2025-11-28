package com.prismOS.bilimusic;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.widget.Toast;

public class MusicService extends Service {
    private final IBinder binder = new MusicBinder();
    private int currentPosition = -1;
    private boolean isPlaying = false;

    public class MusicBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public void updatePlaybackState(int currentPosition, boolean isPlaying) {
        this.currentPosition = currentPosition;
        this.isPlaying = isPlaying;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Toast.makeText(this, "后台播放已停止", Toast.LENGTH_SHORT).show();
    }
}