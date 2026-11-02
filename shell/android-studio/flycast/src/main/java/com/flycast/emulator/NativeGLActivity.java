package com.flycast.emulator;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.RelativeLayout;

import androidx.annotation.Nullable;

import com.flycast.emulator.emu.NativeGLView;
import com.flycast.emulator.emu.VGamepad;
import com.flycast.emulator.shell.ui.DreamcastControls;
import gb.frame.Hotkeys;
import gb.frame.TouchControlsMode;
import com.flycast.emulator.periph.InputDeviceManager;

public final class NativeGLActivity extends BaseGLActivity {

    private ViewGroup mLayout;   // used for text input
    private NativeGLView mView;
    View mTextEdit;
    private boolean mScreenKeyboardShown;
    private boolean restoreAutoLoadAfterStart;
    private boolean originalAutoLoadState;
    // Resume gameplay if Android backgrounded the activity while the game was running.
    private boolean resumeGameplayAfterBackground;
    private InputManager inputManager;
    private boolean ignoreMenuBackUp;
    private final InputManager.InputDeviceListener gamepadListener = new InputManager.InputDeviceListener() {
        @Override public void onInputDeviceAdded(int id) { updateTouchControlsVisibility(); }
        @Override public void onInputDeviceRemoved(int id) { updateTouchControlsVisibility(); }
        @Override public void onInputDeviceChanged(int id) { updateTouchControlsVisibility(); }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Log.i("flycast", "NativeGLActivity.onCreate");
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        restoreAutoLoadAfterStart = getIntent().getBooleanExtra(
                com.flycast.emulator.shell.GBCastBridge.EXTRA_RESTORE_AUTO_LOAD, false);
        originalAutoLoadState = getIntent().getBooleanExtra(
                com.flycast.emulator.shell.GBCastBridge.EXTRA_AUTO_LOAD_VALUE, false);

        super.onCreate(savedInstanceState);

        // System navigation/gestures do not pass through onKeyDown. Keep the game Activity alive
        // and route Back to the pause menu; Compose's later callback owns nested menu pages.
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                com.flycast.emulator.shell.GbcastGameUi.onAndroidBack(NativeGLActivity.this);
            }
        });

        // Create the actual GL view
        mView = new NativeGLView(this);
        mLayout = new RelativeLayout(this);
        mLayout.addView(mView);

        setContentView(mLayout);
        // Immersive from the first frame: waiting for the window focus showed the status bar while the game opened
        hideSystemBars();
        com.flycast.emulator.shell.GbcastGameUi.attach(this, mLayout, mView);
        DreamcastControls.INSTANCE.initialize(this);
        inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
        // Touch controls from the first frame only when there is no physical controller (they start hidden in the core)
        updateTouchControlsVisibility();
        if (inputManager != null) inputManager.registerInputDeviceListener(gamepadListener, null);
        DreamcastControls.configureHotkeys();
        wantLoad = getIntent().getBooleanExtra("gbcast_load_state", false);
        Log.i("flycast", "NativeGLActivity.onCreate done");
    }

    @Override
    protected void onPause() {
        // Preserve the pre-background gameplay intent. Do not auto-resume if the quick menu
        // was already open or Flycast was in another native menu.
        resumeGameplayAfterBackground =
                !com.flycast.emulator.shell.GbcastGameUi.getMenuOwnsKeys()
                && com.flycast.emulator.emu.JNIdc.guiState()
                == com.flycast.emulator.shell.GbcastGameUi.CLOSED;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
            com.flycast.emulator.shell.GbcastGameUi.detach(this);
            if (inputManager != null) inputManager.unregisterInputDeviceListener(gamepadListener);
            restoreAutoLoadSetting();
            super.onDestroy();
        } finally {
            // The shared detector tracks held/suppressed buttons process-wide; clear it when the game session ends.
            Hotkeys.INSTANCE.reset();
        }
    }

    @Override
    @SuppressLint({"RestrictedApi", "GestureBackNavigation"}) // Physical B/Back pairs are deduplicated here; navigation still uses AndroidX Back callbacks.
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                ignoreMenuBackUp = com.flycast.emulator.shell.GbcastInput.consumePairedBack(event);
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                if (!ignoreMenuBackUp && !event.isCanceled()) getOnBackPressedDispatcher().onBackPressed();
                ignoreMenuBackUp = false;
            }
            // Handle the release once. Opening on DOWN then redispatching UP closed the menu immediately.
            return true;
        }
        if (com.flycast.emulator.shell.GbcastGameUi.getMenuOwnsKeys()) {
            final int key = event.getKeyCode();
            if (key == KeyEvent.KEYCODE_BUTTON_B) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) com.flycast.emulator.shell.GbcastInput.note(event);
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled())
                    getOnBackPressedDispatcher().onBackPressed();
                return true;
            }
            com.flycast.emulator.shell.GbcastGameUi.prepareMenuKey(event);
            if (key == KeyEvent.KEYCODE_BUTTON_A) {
                return super.dispatchKeyEvent(new KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(),
                        KeyEvent.KEYCODE_DPAD_CENTER, event.getRepeatCount(), event.getMetaState(), event.getDeviceId(),
                        event.getScanCode(), event.getFlags(), event.getSource()));
            }
            return super.dispatchKeyEvent(event);
        }
        if (!com.flycast.emulator.shell.GbcastGameUi.getMenuOwnsKeys() &&
                event.getKeyCode() != KeyEvent.KEYCODE_BACK &&
                (event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_UP)) {
            boolean wasSuppressed = Hotkeys.INSTANCE.isSuppressed(event.getKeyCode());
            boolean consumed = Hotkeys.INSTANCE.onKey(event.getKeyCode(), event.getAction() == KeyEvent.ACTION_DOWN);
            if (consumed || wasSuppressed || Hotkeys.INSTANCE.isSuppressed(event.getKeyCode()))
                return true;

            int mapped = DreamcastControls.mapKeyCode(event.getKeyCode());
            if (mapped != event.getKeyCode()) {
                return super.dispatchKeyEvent(new KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(),
                        mapped, event.getRepeatCount(), event.getMetaState(), event.getDeviceId(), event.getScanCode(),
                        event.getFlags(), event.getSource()));
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean wantLoad;
    private int loadAttempts;

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus)
            hideSystemBars();
    }

    private void hideSystemBars() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        androidx.core.view.WindowInsetsControllerCompat controller =
                new androidx.core.view.WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private boolean hasPhysicalGamepad() {
        if (inputManager == null) return false;
        for (int id : inputManager.getInputDeviceIds()) {
            InputDevice device = inputManager.getInputDevice(id);
            if (device == null || device.isVirtual()) continue;
            int sources = device.getSources();
            if ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) return true;
        }
        return false;
    }

    /** Auto hides on-screen controls for physical gamepads; Show explicitly overrides detection. */
    private void updateTouchControlsVisibility() {
        setTouchControlsVisible(areTouchControlsAllowed());
    }

    /** Whether the current policy permits the virtual controls to appear. */
    public boolean areTouchControlsAllowed() {
        TouchControlsMode mode = DreamcastControls.INSTANCE.getTouchControlsMode();
        return mode == TouchControlsMode.SHOW || (mode == TouchControlsMode.AUTO && !hasPhysicalGamepad());
    }

    /** Update the on-screen controls while a game is already running. */
    public void setTouchControlsVisible(boolean visible) {
        if (mView == null) return;
        if (visible) mView.showVGamepad();
        else VGamepad.hide();
    }

    /** Re-evaluate the current GBFrame mode after a preference changes in the quick menu. */
    public void refreshTouchControlsVisibility() {
        updateTouchControlsVisibility();
    }

    @Override
    protected void doPause() {
        if (mView != null)
            mView.pause();
    }

    @Override
    protected void doResume() {
        if (mView == null)
            return;
        mView.resume();

        // Android can cause Flycast to enter PAUSE while the activity is backgrounded.
        // Only undo that transition when gameplay was active before onPause; never toggle
        // blindly, and leave an intentionally opened pause menu untouched.
        if (resumeGameplayAfterBackground) {
            resumeGameplayAfterBackground = false;
            if (com.flycast.emulator.emu.JNIdc.guiState()
                    == com.flycast.emulator.shell.GbcastGameUi.PAUSE) {
                com.flycast.emulator.emu.JNIdc.guiTogglePause();
            }
        }
    }

    @Override
    public boolean isSurfaceReady() {
        return mView != null && mView.isSurfaceReady();
    }

    @Override
    public void onGameStateChange(boolean started) {
        super.onGameStateChange(started);
        runOnUiThread(new Runnable() {
            public void run() {
                if (started) {
                    restoreAutoLoadSetting();
                    updateTouchControlsVisibility();
                    if (wantLoad)
                        handler.post(NativeGLActivity.this::tryLoadState);
                }
            }
        });
    }

    private void restoreAutoLoadSetting() {
        if (restoreAutoLoadAfterStart) {
            com.flycast.emulator.emu.JNIdc.configSetBool(
                    "config", "Dreamcast.AutoLoadState", originalAutoLoadState);
            restoreAutoLoadAfterStart = false;
        }
    }

    private void tryLoadState() {
        if (!wantLoad)
            return;
        int slot = getIntent().getIntExtra(com.flycast.emulator.shell.GBCastBridge.EXTRA_SLOT, -1);
        if (slot < 0 || slot > com.flycast.emulator.shell.GBCastBridge.AUTO_SAVE_SLOT) {
            wantLoad = false;
            return;
        }
        if (com.flycast.emulator.emu.JNIdc.loadStateSlot(slot)) {
            wantLoad = false;
            return;
        }
        if (++loadAttempts > 40) {
            wantLoad = false;
            return;
        }
        handler.postDelayed(this::tryLoadState, 300);
    }

    // Called from native code
    public void setVGamepadEditMode(boolean editing) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                mView.setVGamepadEditMode(editing);
            }
        });
    }

    // On-screen keyboard borrowed from SDL core android code
    class ShowTextInputTask implements Runnable {
        /*
         * This is used to regulate the pan&scan method to have some offset from
         * the bottom edge of the input region and the top edge of an input
         * method (soft keyboard)
         */
        static final int HEIGHT_PADDING = 15;

        public int x, y, w, h;

        public ShowTextInputTask(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;

            /* Minimum size of 1 pixel, so it takes focus. */
            if (this.w <= 0) {
                this.w = 1;
            }
            if (this.h + HEIGHT_PADDING <= 0) {
                this.h = 1 - HEIGHT_PADDING;
            }
        }

        @Override
        public void run() {
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(w, h + HEIGHT_PADDING);
            params.leftMargin = x;
            params.topMargin = y;
            if (mTextEdit == null) {
                mTextEdit = new DummyEdit(getApplicationContext(), NativeGLActivity.this);

                mLayout.addView(mTextEdit, params);
            } else {
                mTextEdit.setLayoutParams(params);
            }

            mTextEdit.setVisibility(View.VISIBLE);
            mTextEdit.requestFocus();

            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(mTextEdit, 0);

            mScreenKeyboardShown = true;
            Log.d("flycast", "ShowTextInputTask: run");
        }
    }

    private void showTextInput(int x, int y, int w, int h) {
        // Transfer the task to the main thread as a Runnable
        handler.post(new ShowTextInputTask(x, y, w, h));
    }

    public void hideTextInput() {
        Log.d("flycast", "hideTextInput " + (mTextEdit != null ? "mTextEdit != null" : ""));
        if (mTextEdit != null) {
            mTextEdit.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // Note: On some devices setting view to GONE creates a flicker in landscape.
                    // Setting the View's sizes to 0 is similar to GONE but without the flicker.
                    // The sizes will be set to useful values when the keyboard is shown again.
                    mTextEdit.setLayoutParams(new RelativeLayout.LayoutParams(0, 0));

                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(mTextEdit.getWindowToken(), 0);

                    mScreenKeyboardShown = false;

                    mView.requestFocus();
                }
            }, 50);
        }
    }

    // Called from native code
    public void showScreenKeyboard(boolean show) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!show && (mTextEdit == null || !mScreenKeyboardShown))
                    return;

                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (show != imm.isAcceptingText()) {
                    if (show)
                        showTextInput(0, 0, 16, 100);
                    else
                        hideTextInput();
                }
            }
        });
    }
}

/* This is a fake invisible editor view that receives the input and defines the
+ * pan&scan region
+ */
class DummyEdit extends View implements View.OnKeyListener {
    InputConnection ic;
    NativeGLActivity activity;

    class InputConnection extends BaseInputConnection {
        public InputConnection(boolean fullEditor) {
            super(DummyEdit.this, fullEditor);
        }

        @Override
        public boolean sendKeyEvent(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)
                activity.hideTextInput();
            return super.sendKeyEvent(event);
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            InputDeviceManager devManager = InputDeviceManager.getInstance();
            for (int i = 0; i < text.length(); ) {
                int cp = Character.codePointAt(text, i);
                if (cp == '\n') {
                    activity.hideTextInput();
                    return true;
                }
                devManager.keyboardText(cp);
                i += Character.charCount(cp);
            }
            return super.commitText(text, newCursorPosition);
        }

        @Override
        public boolean deleteSurroundingText(int beforeLength, int afterLength) {
            // Workaround to capture backspace key. Ref: http://stackoverflow.com/questions/14560344/android-backspace-in-webview-baseinputconnection
            // and https://bugzilla.libsdl.org/show_bug.cgi?id=2265
            if (beforeLength > 0 && afterLength == 0) {
                Log.d("flycast", "deleteSurroundingText before=" + beforeLength);
                KeyEvent downEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL);
                downEvent.setSource(InputDevice.SOURCE_KEYBOARD);
                KeyEvent upEvent = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL);
                upEvent.setSource(InputDevice.SOURCE_KEYBOARD);
                boolean ret = true;
                // backspace(s)
                while (beforeLength-- > 0) {
                    boolean ret_key = sendKeyEvent(downEvent);
                    sendKeyEvent(upEvent);
                    ret = ret && ret_key;
                }
                return ret;
            }
            return super.deleteSurroundingText(beforeLength, afterLength);
        }
    }

    public DummyEdit(Context context, NativeGLActivity activity) {
        super(context);
        this.activity = activity;
        setFocusableInTouchMode(true);
        setFocusable(true);
        setOnKeyListener(this);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        /*
         * This handles the hardware keyboard input
         */
        InputDeviceManager devManager = InputDeviceManager.getInstance();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (!event.isCtrlPressed() && (event.isPrintingKey() || event.getKeyCode() == KeyEvent.KEYCODE_SPACE))
                ic.commitText(new String(Character.toChars(event.getUnicodeChar())), 1);
            else
                devManager.keyboardEvent(event.getKeyCode(), true);
            return true;
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            devManager.keyboardEvent(event.getKeyCode(), false);
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        // As seen on StackOverflow: http://stackoverflow.com/questions/7634346/keyboard-hide-event
        // FIXME: Discussion at http://bugzilla.libsdl.org/show_bug.cgi?id=1639
        // FIXME: This is not a 100% effective solution to the problem of detecting if the keyboard is showing or not
        // FIXME: A more effective solution would be to assume our Layout to be RelativeLayout or LinearLayout
        // FIXME: And determine the keyboard presence doing this: http://stackoverflow.com/questions/2150078/how-to-check-visibility-of-software-keyboard-in-android
        // FIXME: An even more effective way would be if Android provided this out of the box, but where would the fun be in that :)
        if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
            if (activity.mTextEdit != null && activity.mTextEdit.getVisibility() == View.VISIBLE) {
//                activity.hideTextInput();
                KeyEvent downEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER);
                downEvent.setSource(InputDevice.SOURCE_KEYBOARD);
                ic.sendKeyEvent(downEvent);
                KeyEvent upEvent = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER);
                upEvent.setSource(InputDevice.SOURCE_KEYBOARD);
                ic.sendKeyEvent(upEvent);
            }
        }
        return super.onKeyPreIme(keyCode, event);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_FULLSCREEN /* API 11 */;
        ic = new InputConnection(true);
        return ic;
    }
}

