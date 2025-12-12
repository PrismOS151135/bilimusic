package com.prismOS.bilimusic;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private EditText scanPathEditText;
    private EditText TimedOff;
    private EditText PlaySpeed;
    private EditText ImageSize;
    private Button resetButton;
    private CheckBox isPowerOffBox;
    private CheckBox isAnimOffBox;
    private SharedPreferences sharedPreferences;
    // 文本操作相关
    private PopupWindow textOperationPopup;
    private EditText currentFocusedEditText;
    private TextView popupCopyBtn;
    private TextView popupCutBtn;
    private TextView popupPasteBtn;
    private TextView popupSelectAllBtn;
    private EditText TextSize;
    private ClipboardManager clipboardManager;

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
    private boolean isPowerOff;
    private boolean isAnimOff;
    private int imageSize;
    private int textSize;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        initViews();
        setupSharedPreferences();
        loadCurrentSettings();
        setupButtonListeners();
        setupTextOperationPopup();
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

        // 为所有EditText设置长按监听
        setupEditTextLongClick(scanPathEditText);
        setupEditTextLongClick(TimedOff);
        setupEditTextLongClick(PlaySpeed);
        setupEditTextLongClick(ImageSize);
        setupEditTextLongClick(TextSize);
    }

    private void setupEditTextLongClick(EditText editText) {
        // 确保EditText可聚焦和触摸聚焦
        editText.setFocusable(true);
        editText.setFocusableInTouchMode(true);

        editText.setOnLongClickListener(v -> {
            currentFocusedEditText = editText;
            showTextOperationPopup(editText);
            return true;
        });

        // 点击监听用于隐藏弹窗
        editText.setOnClickListener(v -> {
            if (textOperationPopup != null && textOperationPopup.isShowing()) {
                textOperationPopup.dismiss();
            } else {
                editText.requestFocus();
                showKeyboard(editText);
            }
        });

        // 文本变化监听，当文本变化时更新按钮状态
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (currentFocusedEditText == editText && textOperationPopup != null && textOperationPopup.isShowing()) {
                    updatePopupButtonStates();
                }
            }
        });
    }
    // 显示软键盘的辅助方法
    private void showKeyboard(EditText editText) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private void setupTextOperationPopup() {
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        @SuppressLint("InflateParams") View popupView = inflater.inflate(R.layout.text_operation_popup, null);

        popupCopyBtn = popupView.findViewById(R.id.popup_copy);
        popupCutBtn = popupView.findViewById(R.id.popup_cut);
        popupPasteBtn = popupView.findViewById(R.id.popup_paste);
        popupSelectAllBtn = popupView.findViewById(R.id.popup_select_all);
        TextView popupCancelBtn = popupView.findViewById(R.id.popup_cancel);

        // 设置按钮点击监听
        popupCopyBtn.setOnClickListener(v -> copyText());
        popupCutBtn.setOnClickListener(v -> cutText());
        popupPasteBtn.setOnClickListener(v -> pasteText());
        popupSelectAllBtn.setOnClickListener(v -> selectAllText());
        popupCancelBtn.setOnClickListener(v -> textOperationPopup.dismiss());

        // 创建PopupWindow
        textOperationPopup = new PopupWindow(
                popupView,
                90,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        
        textOperationPopup.setBackgroundDrawable(getResources().getDrawable(android.R.drawable.dialog_holo_light_frame));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textOperationPopup.setElevation(20);
        }
        textOperationPopup.setFocusable(true);
        textOperationPopup.setOutsideTouchable(true);
    }

    private void showTextOperationPopup(EditText editText) {
        if (textOperationPopup != null) {
            // 更新按钮状态
            updatePopupButtonStates();

            // 计算弹窗位置
            int[] location = new int[2];
            editText.getLocationOnScreen(location);

            // 显示在EditText下方
            textOperationPopup.showAtLocation(
                    editText,
                    Gravity.NO_GRAVITY,
                    location[0] + editText.getWidth() / 2 - 150, // 居中偏移
                    location[1] + editText.getHeight()
            );
        }
    }

    private void updatePopupButtonStates() {
        if (currentFocusedEditText == null) return;

        // 检查是否有选中文本
        int selectionStart = currentFocusedEditText.getSelectionStart();
        int selectionEnd = currentFocusedEditText.getSelectionEnd();
        boolean hasSelection = selectionStart != selectionEnd;

        // 检查剪贴板是否有内容
        boolean hasClipboardContent = clipboardManager.hasPrimaryClip()
                && clipboardManager.getPrimaryClip() != null
                && clipboardManager.getPrimaryClip().getItemCount() > 0;

        // 更新按钮可用状态
        popupCopyBtn.setEnabled(hasSelection);
        popupCutBtn.setEnabled(hasSelection);
        popupPasteBtn.setEnabled(hasClipboardContent);
        popupSelectAllBtn.setEnabled(currentFocusedEditText.getText().length() > 0);

        // 更新按钮透明度
        popupCopyBtn.setAlpha(hasSelection ? 1.0f : 0.5f);
        popupCutBtn.setAlpha(hasSelection ? 1.0f : 0.5f);
        popupPasteBtn.setAlpha(hasClipboardContent ? 1.0f : 0.5f);
        popupSelectAllBtn.setAlpha(currentFocusedEditText.getText().length() > 0 ? 1.0f : 0.5f);
    }

    private void copyText() {
        if (currentFocusedEditText == null) return;

        int selectionStart = currentFocusedEditText.getSelectionStart();
        int selectionEnd = currentFocusedEditText.getSelectionEnd();

        if (selectionStart != selectionEnd) {
            String selectedText = currentFocusedEditText.getText()
                    .subSequence(selectionStart, selectionEnd)
                    .toString();

            ClipData clip = ClipData.newPlainText("text", selectedText);
            clipboardManager.setPrimaryClip(clip);

            Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
            textOperationPopup.dismiss();
        }
    }

    private void cutText() {
        if (currentFocusedEditText == null) return;

        int selectionStart = currentFocusedEditText.getSelectionStart();
        int selectionEnd = currentFocusedEditText.getSelectionEnd();

        if (selectionStart != selectionEnd) {
            String selectedText = currentFocusedEditText.getText()
                    .subSequence(selectionStart, selectionEnd)
                    .toString();

            // 复制到剪贴板
            ClipData clip = ClipData.newPlainText("text", selectedText);
            clipboardManager.setPrimaryClip(clip);

            // 删除选中文本
            currentFocusedEditText.getText().delete(selectionStart, selectionEnd);

            Toast.makeText(this, "已剪切到剪贴板", Toast.LENGTH_SHORT).show();
            textOperationPopup.dismiss();
        }
    }

    private void pasteText() {
        if (currentFocusedEditText == null || !clipboardManager.hasPrimaryClip()) return;

        ClipData clipData = clipboardManager.getPrimaryClip();
        if (clipData != null && clipData.getItemCount() > 0) {
            CharSequence pasteData = clipData.getItemAt(0).getText();
            if (pasteData != null) {
                int selectionStart = currentFocusedEditText.getSelectionStart();
                int selectionEnd = currentFocusedEditText.getSelectionEnd();

                if (selectionStart != selectionEnd) {
                    // 替换选中文本
                    currentFocusedEditText.getText().replace(selectionStart, selectionEnd, pasteData);
                } else {
                    // 在光标处插入
                    currentFocusedEditText.getText().insert(selectionStart, pasteData);
                }

                Toast.makeText(this, "已粘贴", Toast.LENGTH_SHORT).show();
                textOperationPopup.dismiss();
            }
        }
    }

    private void selectAllText() {
        if (currentFocusedEditText == null) return;

        currentFocusedEditText.selectAll();
        updatePopupButtonStates();
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
        if (imageSize != Integer.parseInt(nowImageSize) && !nowImageSize.isEmpty()) {
            int currentImageSize = Integer.parseInt(nowImageSize);
            if (currentImageSize >= 0) {
                editor.putInt(KEY_IMAGE_SIZE, currentImageSize);
                hasChanges = true;
            }
        }
        String nowTextSize = TextSize.getText().toString().trim();
        if (textSize != Integer.parseInt(nowTextSize) && !nowTextSize.isEmpty()){
            int currentTextSize = Integer.parseInt(nowTextSize);
            if (currentTextSize >= 0) {
                editor.putInt(KEY_TEXT_SIZE, currentTextSize);
                hasChanges = true;
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