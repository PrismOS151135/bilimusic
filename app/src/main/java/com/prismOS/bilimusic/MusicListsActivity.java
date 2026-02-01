package com.prismOS.bilimusic;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.CopyOnWriteArrayList;

public class MusicListsActivity extends AppCompatActivity {
    private ListView musicListView;
    protected static ArrayAdapter<String> adapter;

    // 使用线程安全的列表
    private final CopyOnWriteArrayList<String> musicList = new CopyOnWriteArrayList<>();

    // 使用Handler进行UI更新
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean isActivityDestroyed = false;

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_musiclists);

        // 初始化音乐列表（线程安全）
        musicList.addAll(MainActivity.musicFolders);

        initViews();
    }


    public void initViews() {
        musicListView = findViewById(R.id.musicListView);

        // 创建适配器时使用线程安全的列表副本
        adapter = new ArrayAdapter<>(this, R.layout.list_item_music, R.id.folderNameText, musicList) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull android.view.ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView folderNameText = view.findViewById(R.id.folderNameText);
                ImageView playingIndicator = view.findViewById(R.id.playingIndicator);

                // 安全检查
                if (folderNameText == null || playingIndicator == null) {
                    return view;
                }

                // 设置歌曲名称
                String folderName = getItem(position);
                folderNameText.setText(folderName);

                // 判断是否为当前正在播放的歌曲
                if (position == MusicService.currentPosition) {
                    // 正在播放的歌曲显示为蓝色
                    folderNameText.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
                    playingIndicator.setVisibility(MusicService.isPlaying ? View.VISIBLE : View.GONE);
                } else {
                    // 非播放歌曲显示为黑色
                    folderNameText.setTextColor(getResources().getColor(android.R.color.white));
                    playingIndicator.setVisibility(View.GONE);
                }

                return view;
            }
        };

        // 安全地设置适配器和监听器
        mainHandler.post(() -> {
            if (!isActivityDestroyed && musicListView != null) {
                musicListView.setAdapter(adapter);
                musicListView.setOnItemClickListener((parent, view, position, id) -> {
                    ToPlayMusic(position);

                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                });

                // 如果已经有正在播放的歌曲，滚动到该位置
                if (MusicService.currentPosition != -1 && MusicService.currentPosition < musicList.size()) {
                    musicListView.smoothScrollToPosition(MusicService.currentPosition);
                }
            }
        });
    }

    public void ToPlayMusic(int position) {
        // 发送广播通知MusicService播放音乐
        Intent playIntent = new Intent(MusicService.ACTION_PLAY_POSITION);
        playIntent.putExtra(MusicService.EXTRA_POSITION, position);
        sendBroadcast(playIntent);

        // 关闭当前Activity
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityDestroyed = true;

        // 清除Handler的所有任务
        mainHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActivityDestroyed = true;

        // 移除所有Handler回调
        mainHandler.removeCallbacksAndMessages(null);

        // 释放资源
        if (musicListView != null) {
            musicListView.setAdapter(null);
        }
        adapter = null;
    }
}