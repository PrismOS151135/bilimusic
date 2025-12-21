package com.prismOS.bilimusic;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private EditText scanPathEditText, TimedOff, PlaySpeed, ImageSize, ButtonInterval;
    private Button resetButton;
    private CheckBox isPowerOffBox;
    private CheckBox isAnimOffBox;
    private SharedPreferences sharedPreferences;
    // 文本操作相关
    private PopupWindow textOperationPopup;
    private EditText TextSize;

    protected static final String PREFS_NAME = "MusicPlayerPrefs";
    private static final String KEY_SCAN_PATH = "scan_path";
    private static final String KEY_MUSIC_FILE = "music_file";
    private static final String KEY_PLAY_SPEED = "play_speed";
    private static final String DEFAULT_SCAN_PATH = "/storage/emulated/0/Android/media/com.RobinNotBad.BiliClient/";
    public static final String KEY_TIMED_OFF = "timed_off_minutes";
    public static final String KEY_IS_POWER_OFF = "is_power_off";
    public static final String KEY_IS_ANIM_OFF = "is_anim_off";
    public static final String KEY_IMAGE_SIZE = "image_size";
    public static final String KEY_TEXT_SIZE = "text_size";
    public static final String KEY_BUTTON_INTERVAL = "button_interval";
    private boolean isPowerOff;
    private boolean isAnimOff;
    private int imageSize;
    private int textSize;
    private int buttonInterval;
    
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
        PlaySpeed = findViewById(R.id.Playspeed);
        isPowerOffBox = findViewById(R.id.isPowerOffBox);
        isAnimOffBox = findViewById(R.id.isAnimOffBox);
        ImageSize = findViewById(R.id.ImageSize);
        TextSize = findViewById(R.id.TextSize);
        ButtonInterval = findViewById(R.id.ButtonInterval);

    }

    private void setupSharedPreferences() {
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    private void loadCurrentSettings() {
        String currentScanPath = sharedPreferences.getString(KEY_SCAN_PATH, DEFAULT_SCAN_PATH);
        scanPathEditText.setText(currentScanPath);
        int currentTimedOff = sharedPreferences.getInt(KEY_TIMED_OFF, 0);
        TimedOff.setText(currentTimedOff == 0 ? "" : String.valueOf(currentTimedOff));
        float currentPlaySpeed = sharedPreferences.getFloat(KEY_PLAY_SPEED, 1.0f);
        PlaySpeed.setText(String.valueOf(currentPlaySpeed));
        isPowerOff = sharedPreferences.getBoolean(KEY_IS_POWER_OFF, false);
        isPowerOffBox.setChecked(isPowerOff);
        isAnimOff = sharedPreferences.getBoolean(KEY_IS_ANIM_OFF,false);
        isAnimOffBox.setChecked(isAnimOff);
        imageSize = sharedPreferences.getInt(KEY_IMAGE_SIZE,120);
        ImageSize.setText(String.valueOf(imageSize));
        textSize = sharedPreferences.getInt(KEY_TEXT_SIZE,16);
        TextSize.setText(String.valueOf(textSize));
        buttonInterval = sharedPreferences.getInt(KEY_BUTTON_INTERVAL,0);
        ButtonInterval.setText(String.valueOf(buttonInterval));
    }

    private void setupButtonListeners() {
        resetButton.setOnClickListener(v -> resetToDefaults());

        // 点击页面其他区域关闭弹窗
        findViewById(R.id.topBar).setOnClickListener(v -> {
            if (textOperationPopup != null && textOperationPopup.isShowing()) {
                textOperationPopup.dismiss();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 退出页面时自动保存所有设置
        saveAllSettings();
        // 关闭弹窗
        if (textOperationPopup != null && textOperationPopup.isShowing()) {
            textOperationPopup.dismiss();
        }
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
        String speedStr = PlaySpeed.getText().toString().trim();
        if (!speedStr.isEmpty()) {
            try {
                float currentPlaySpeed = Float.parseFloat(speedStr);
                if (currentPlaySpeed > 0 && currentPlaySpeed <= 4.0f) {
                    float speed = sharedPreferences.getFloat(KEY_PLAY_SPEED, 1.0f);
                    if (speed != currentPlaySpeed) {
                        editor.putFloat(KEY_PLAY_SPEED, currentPlaySpeed);
                        hasChanges = true;
                    }
                }
            } catch (NumberFormatException e) {
                // 输入无效，忽略
            }
        }

        boolean nowPowerOff = isPowerOffBox.isChecked();
        if (nowPowerOff != isPowerOff){
            editor.putBoolean(KEY_IS_POWER_OFF, nowPowerOff);
            hasChanges = true;
        }

        boolean nowAnimOff = isAnimOffBox.isChecked();
        if (nowAnimOff != isAnimOff){
            editor.putBoolean(KEY_IS_ANIM_OFF, nowAnimOff);
            hasChanges = true;
        }

        String nowImageSize = ImageSize.getText().toString().trim();
        if (!nowImageSize.isEmpty()) {
            int currentImageSize = Integer.parseInt(nowImageSize);
            if (imageSize != currentImageSize && currentImageSize >= 0) {
                editor.putInt(KEY_IMAGE_SIZE, currentImageSize);
                hasChanges = true;
            }
        }else{
            editor.putInt(KEY_IMAGE_SIZE, 0);
            hasChanges = true;
        }

        String nowTextSize = TextSize.getText().toString().trim();
        if (!nowTextSize.isEmpty()){
            int currentTextSize = Integer.parseInt(nowTextSize);
            if (textSize != currentTextSize && currentTextSize >= 0) {
                editor.putInt(KEY_TEXT_SIZE, currentTextSize);
                hasChanges = true;
            }
        }else{
            editor.putInt(KEY_TEXT_SIZE, 0);
            hasChanges = true;
        }

        String nowButtonInterval = ButtonInterval.getText().toString().trim();
        if (!nowButtonInterval.isEmpty()){
            int currentButtonInterval = Integer.parseInt(nowButtonInterval);
            if (buttonInterval != currentButtonInterval && currentButtonInterval >= 0) {
                editor.putInt(KEY_BUTTON_INTERVAL, currentButtonInterval);
                hasChanges = true;
            }
        }else{
            editor.putInt(KEY_BUTTON_INTERVAL, 0);
            hasChanges = true;
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
        editor.remove(KEY_PLAY_SPEED);
        editor.remove(KEY_IS_POWER_OFF);
        editor.remove(KEY_IS_ANIM_OFF);
        editor.remove(KEY_IMAGE_SIZE);
        editor.remove(KEY_TEXT_SIZE);
        editor.apply();

        // 重新加载默认设置
        loadCurrentSettings();

        Toast.makeText(this, "已恢复默认设置", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textOperationPopup != null) {
            textOperationPopup.dismiss();
            textOperationPopup = null;
        }
    }
}