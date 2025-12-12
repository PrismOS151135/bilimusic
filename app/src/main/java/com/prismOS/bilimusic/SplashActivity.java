package com.prismOS.bilimusic;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.vectordrawable.graphics.drawable.Animatable2Compat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SplashActivity extends AppCompatActivity {

    private ImageView gifImageView;
    private CountDownLatch scanLatch;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        SharedPreferences OnceSharedPreferences = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE);
        boolean isAnimOff = OnceSharedPreferences.getBoolean(SettingsActivity.KEY_IS_ANIM_OFF,false);
        if (isAnimOff){
            SharedPreferences sharedPreferences = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
            String scanPath = sharedPreferences.getString(MainActivity.KEY_SCAN_PATH, MainActivity.DEFAULT_SCAN_PATH);
            String musicFile = sharedPreferences.getString(MainActivity.KEY_MUSIC_FILE, MainActivity.DEFAULT_MUSIC_FILE);
            MainActivity.scanMusicFolders(scanPath, musicFile);
            startMainActivity();
        }else{
            gifImageView = findViewById(R.id.gif_image_view);
            loadGifAndApp();
        }
    }

    private void loadGifAndApp() {
        // 初始化扫描
        scanLatch = new CountDownLatch(1);

        SharedPreferences sharedPreferences = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        String scanPath = sharedPreferences.getString(MainActivity.KEY_SCAN_PATH, MainActivity.DEFAULT_SCAN_PATH);
        String musicFile = sharedPreferences.getString(MainActivity.KEY_MUSIC_FILE, MainActivity.DEFAULT_MUSIC_FILE);

        // 启动后台线程扫描文件
        new Thread(() -> {
            boolean scanResult = MainActivity.scanMusicFolders(scanPath, musicFile);

            scanLatch.countDown();

            runOnUiThread(() -> {
                if (!scanResult) {
                    // 扫描失败时提示
                    Toast.makeText(SplashActivity.this, "扫描路径不存在，请检查设置", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();

        // 使用 Glide 加载 GIF
        Glide.with(this)
                .asGif()
                .load(R.raw.loading)
                .listener(new RequestListener<>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                Target<GifDrawable> target, boolean isFirstResource) {
                        // GIF 加载失败，等待扫描完成后进入主应用
                        waitForScanAndStartMain();
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(GifDrawable resource, Object model,
                                                   Target<GifDrawable> target, DataSource dataSource,
                                                   boolean isFirstResource) {
                        // GIF 加载成功，开始播放，设置只播放一次
                        resource.setLoopCount(1);

                        // 监听播放完成
                        resource.registerAnimationCallback(new Animatable2Compat.AnimationCallback() {
                            @Override
                            public void onAnimationEnd(Drawable drawable) {
                                // GIF 播放完成后，等待扫描完成和应用加载
                                waitForScanAndStartMain();
                            }
                        });

                        // 开始播放
                        resource.start();
                        return false;
                    }
                })
                .into(gifImageView);
    }

    private void waitForScanAndStartMain() {
        // 在新线程中等待扫描完成
        new Thread(() -> {
            try {
                // 等待扫描完成（最多等待5秒）
                boolean isInTime = scanLatch.await(5, TimeUnit.SECONDS);

                runOnUiThread(() -> {
                    if (!isInTime){
                          Toast.makeText(SplashActivity.this, "扫描超时，请检查设置", Toast.LENGTH_SHORT).show();
                    }
                    startMainActivity();
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
                runOnUiThread(this::startMainActivity);
            }
        }).start();
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        // 可选：添加转场动画
        // overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}