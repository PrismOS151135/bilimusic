package com.prismOS.bilimusic;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private EditText scanPathEditText;
    private EditText TimedOff;
    private EditText Playspeed; // 播放速度输入框
    private Button resetButton;
    private SharedPreferences sharedPreferences;

    private static final String PREFS_NAME = "MusicPlayerPrefs";
    private static final String KEY_SCAN_PATH = "scan_path";
    private static final String KEY_MUSIC_FILE = "music_file";
    private static final String KEY_PLAY_SPEED = "play_speed"; // 播放速度键
    // 默认值
    private static final String DEFAULT_SCAN_PATH = "/storage/sdcard1/Android/media/com.RobinNotBad.BiliClient/Folder1";
    public static final String KEY_TIMED_OFF = "timed_off_minutes";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initViews();
        setupSharedPreferences();
        loadCurrentSettings();
        setupButtonListeners();
    }

    private void initViews() {
        scanPathEditText = findViewById(R.id.scanPathEditText);
        resetButton = findViewById(R.id.resetButton);
        TimedOff = findViewById(R.id.TimedOff);
        Playspeed = findViewById(R.id.Playspeed); // 初始化播放速度输入框
    }

    private void setupSharedPreferences() {
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    private void loadCurrentSettings() {
        String currentScanPath = sharedPreferences.getString(KEY_SCAN_PATH, DEFAULT_SCAN_PATH);
        scanPathEditText.setText(currentScanPath);
        int currentTimedOff = sharedPreferences.getInt(KEY_TIMED_OFF, 0);
        TimedOff.setText(currentTimedOff == 0 ? "" : String.valueOf(currentTimedOff));

        // 加载播放速度设置
        float currentPlaySpeed = sharedPreferences.getFloat(KEY_PLAY_SPEED, 1.0f);
        Playspeed.setText(String.valueOf(currentPlaySpeed));
    }

    private void setupButtonListeners() {
        resetButton.setOnClickListener(v -> resetToDefaults());
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 退出页面时自动保存所有设置
        saveAllSettings();
    }

    private void saveAllSettings() {
        boolean hasChanges = false;
        SharedPreferences.Editor editor = sharedPreferences.edit();

        // 保存扫描路径
        String newScanPath = scanPathEditText.getText().toString().trim();
        if (!newScanPath.isEmpty()) {
            String currentScanPath = sharedPreferences.getString(KEY_SCAN_PATH, DEFAULT_SCAN_PATH);
            if (!newScanPath.equals(currentScanPath)) {
                editor.putString(KEY_SCAN_PATH, newScanPath);
                hasChanges = true;
            }
        }

        // 保存定时关闭设置
        String minutesStr = TimedOff.getText().toString().trim();
        if (minutesStr.isEmpty()) {
            // 清空表示取消定时关闭
            int currentTimedOff = sharedPreferences.getInt(KEY_TIMED_OFF, 0);
            if (currentTimedOff != 0) {
                editor.putInt(KEY_TIMED_OFF, 0);
                hasChanges = true;
            }
        } else {
            try {
                int minutes = Integer.parseInt(minutesStr);
                if (minutes > 0) {
                    int currentTimedOff = sharedPreferences.getInt(KEY_TIMED_OFF, 0);
                    if (minutes != currentTimedOff) {
                        editor.putInt(KEY_TIMED_OFF, minutes);
                        hasChanges = true;
                    }
                }
            } catch (NumberFormatException e) {
                // 输入无效，忽略
            }
        }

        // 保存播放速度设置
        String speedStr = Playspeed.getText().toString().trim();
        if (!speedStr.isEmpty()) {
            try {
                float speed = Float.parseFloat(speedStr);
                if (speed > 0 && speed <= 4.0f) {
                    float currentPlaySpeed = sharedPreferences.getFloat(KEY_PLAY_SPEED, 1.0f);
                    if (speed != currentPlaySpeed) {
                        editor.putFloat(KEY_PLAY_SPEED, speed);
                        hasChanges = true;
                    }
                }
            } catch (NumberFormatException e) {
                // 输入无效，忽略
            }
        }

        if (hasChanges) {
            editor.apply();
            Toast.makeText(this, "设置已自动保存", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetToDefaults() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_SCAN_PATH);
        editor.remove(KEY_MUSIC_FILE);
        editor.remove(KEY_TIMED_OFF);
        editor.remove(KEY_PLAY_SPEED); // 重置播放速度
        editor.apply();

        // 重新加载默认设置
        loadCurrentSettings();

        Toast.makeText(this, "已恢复默认设置", Toast.LENGTH_SHORT).show();
    }

}