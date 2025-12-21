package com.prismOS.bilimusic;

import static com.prismOS.bilimusic.MainActivity.currentPosition;

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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class MusicListsActivity extends AppCompatActivity {
    private ListView musicListView;
    protected static ArrayAdapter<String> adapter;

    // 定义广播动作和参数
    public static final String ACTION_PLAY_MUSIC = "com.prismOS.bilimusic.PLAY_MUSIC";
    public static final String EXTRA_POSITION = "position";

    // 广播接收器，用于接收MainActivity的状态更新
    private final BroadcastReceiver statusUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null && intent.getAction().equals("com.prismOS.bilimusic.STATUS_UPDATE")) {
                // 更新列表显示
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_musiclists);

        // 注册广播接收器
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.prismOS.bilimusic.STATUS_UPDATE");
        registerReceiver(statusUpdateReceiver, filter);

        initViews();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 注销广播接收器
        unregisterReceiver(statusUpdateReceiver);
    }

    public void initViews() {
        musicListView = findViewById(R.id.musicListView);

        adapter = new ArrayAdapter<>(this, R.layout.list_item_music, R.id.folderNameText, MainActivity.musicFolders) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull android.view.ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView folderNameText = view.findViewById(R.id.folderNameText);
                ImageView playingIndicator = view.findViewById(R.id.playingIndicator);
                boolean isInTheLists = true;
                //判断是否在列表内
                String text = MainActivity.currentMusicText.getText().toString().trim();
                if (!MainActivity.musicFolders.contains(text)) isInTheLists = false;
                if (folderNameText != null)
                    folderNameText.setText(getItem(position));

                if (position == currentPosition && isInTheLists) {
                    if (MainActivity.isPlaying) {
                        playingIndicator.setVisibility(View.VISIBLE);
                    } else {
                        playingIndicator.setVisibility(View.GONE);
                    }
                    if (folderNameText != null)
                        folderNameText.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
                } else {
                    playingIndicator.setVisibility(View.GONE);
                    if (folderNameText != null)
                        folderNameText.setTextColor(getResources().getColor(android.R.color.white));
                }
                return view;
            }
        };

        musicListView.post(() -> {
            musicListView.setAdapter(adapter);
            musicListView.setOnItemClickListener((parent, view, position, id) -> ToPlayMusic(position));
        });
    }

    public void ToPlayMusic(int position) {
        String text = MainActivity.currentMusicText.getText().toString().trim();
        if (MainActivity.musicFolders.contains(text) && position == currentPosition) return;
        // 发送广播通知MainActivity播放音乐
        Intent playIntent = new Intent(ACTION_PLAY_MUSIC);
        playIntent.putExtra(EXTRA_POSITION, position);
        sendBroadcast(playIntent);
        // 可选：返回到MainActivity
        //finish();
    }
}