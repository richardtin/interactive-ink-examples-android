package com.myscript.iink.getstarted;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatTextView;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.ImageView;

import com.myscript.certificate.MyCertificate;
import com.myscript.iink.Configuration;
import com.myscript.iink.ContentBlock;
import com.myscript.iink.ContentPackage;
import com.myscript.iink.ContentPart;
import com.myscript.iink.ConversionState;
import com.myscript.iink.Editor;
import com.myscript.iink.Engine;
import com.myscript.iink.MimeType;
import com.myscript.iink.uireferenceimplementation.EditorView;
import com.myscript.iink.uireferenceimplementation.IInputControllerListener;
import com.myscript.iink.uireferenceimplementation.InputController;

import java.io.File;
import java.io.IOException;

public class MathService extends Service {

    private static final String TAG = MathService.class.getSimpleName();

    private WindowManager mWindowManager;
    private LayoutParams mLayoutParams;
    private View mFloatingWindow, mBaseLayout;
    private float mScreenX, mScreenY;
    private boolean mSelectionMode;

    private ImageView mDelete, mScale;
    private Button mClear;
    private AppCompatTextView mFormulaText, mResultText;
    float mLastTouchX, mLastTouchY;
    private int mMinWidth, mMinHeight;

    private Engine mEngine;
    private ContentPackage mContentPackage;
    private ContentPart mContentPart;
    private EditorView mEditorView;

    private CountDownTimer mRecognitionTimer = new CountDownTimer(1000, 500) {
        @Override
        public void onTick(long millisUntilFinished) {
            Log.d(TAG, "CountDownTimer::onTick() millisUntilFinished = " + millisUntilFinished);
        }

        @Override
        public void onFinish() {
            Log.d(TAG, "CountDownTimer::onFinish()");
            recognize();
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createFloatingWindow();

        mMinWidth = getResources().getDimensionPixelSize(R.dimen.math_window_min_width);
        mMinHeight = getResources().getDimensionPixelSize(R.dimen.math_window_min_height);

        findView();
        setOnClickListener();
        initial();
    }

    @Override
    public void onDestroy() {
        if (mEditorView != null) {
            mEditorView.setOnTouchListener(null);
            mEditorView.close();
        }

        if (mContentPart != null) {
            mContentPart.close();
            mContentPart = null;
        }

        if (mContentPackage != null) {
            mContentPackage.close();
            mContentPackage = null;
        }

        mEngine = null;

        if (mFloatingWindow != null) {
            mWindowManager.removeView(mFloatingWindow);
        }
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    private void createFloatingWindow() {
        mWindowManager = (WindowManager) getApplication().getSystemService(WINDOW_SERVICE);
        mLayoutParams = new WindowManager.LayoutParams();

        setupLayoutParams();
        setupFloatingWindow();

        mWindowManager.addView(mFloatingWindow, mLayoutParams);

        setupBaseLayout();

        mFloatingWindow.setOnTouchListener(new View.OnTouchListener() {
            private boolean isLongPress = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int longClickDuration = 200;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isLongPress = true;
                        Handler handler = new Handler();
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (isLongPress) {
                                    mSelectionMode = true;
                                }
                            }
                        }, longClickDuration);
                        mScreenX = event.getRawX();
                        mScreenY = event.getRawY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (mSelectionMode) {
                            float mDeltaX = event.getRawX() - mScreenX;
                            float mDeltaY = event.getRawY() - mScreenY;
                            mScreenX = event.getRawX();
                            mScreenY = event.getRawY();

                            mLayoutParams.x += (int) mDeltaX;
                            mLayoutParams.y += (int) mDeltaY;
                            updateViewLayout();
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        mSelectionMode = false;
                        isLongPress = false;
                        break;
                }

                return false;
            }
        });
    }

    private void setupLayoutParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            mLayoutParams.type = WindowManager.LayoutParams.TYPE_TOAST;
        }
        mLayoutParams.format = PixelFormat.RGBA_8888;
        mLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        mLayoutParams.gravity = Gravity.CENTER;
        mLayoutParams.width = getResources().getDimensionPixelSize(R.dimen.math_window_min_width);
        mLayoutParams.height = getResources().getDimensionPixelSize(R.dimen.math_window_min_height);
    }

    private void setupFloatingWindow() {
        LayoutInflater mInflater = LayoutInflater.from(getApplicationContext());
        mFloatingWindow = mInflater.inflate(R.layout.math_window, null);
    }

    private void setupBaseLayout() {
        mBaseLayout = mFloatingWindow.findViewById(R.id.math_layout);
    }

    private void updateViewLayout() {
        mWindowManager.updateViewLayout(mFloatingWindow, mLayoutParams);
    }

    private void findView() {
        mDelete = (ImageView) mBaseLayout.findViewById(R.id.btn_delete);
        mClear = (Button) mBaseLayout.findViewById(R.id.btn_clear);
        mScale = (ImageView) mBaseLayout.findViewById(R.id.btn_scale);
        mFormulaText = (AppCompatTextView) mBaseLayout.findViewById(R.id.math_formula);
        mResultText = (AppCompatTextView) mBaseLayout.findViewById(R.id.math_result);
        mEditorView = mBaseLayout.findViewById(R.id.editor_view);
    }

    private void setOnClickListener() {
        mDelete.setOnClickListener(mBtnListener);
        mClear.setOnClickListener(mBtnListener);
        mScale.setOnClickListener(mBtnListener);
        mScale.setOnTouchListener(mOnTouchListener);
    }

    private void initial() {
        mEngine = Engine.create(MyCertificate.getBytes());
        // configure recognition
        Configuration conf = mEngine.getConfiguration();
        String confDir = "zip://" + getPackageCodePath() + "!/assets/conf";
        conf.setStringArray("configuration-manager.search-path", new String[]{confDir});
        String tempDir = getFilesDir().getPath() + File.separator + "tmp";
        conf.setString("content-package.temp-folder", tempDir);
        Log.d(TAG, "math.solver.enable = " + conf.getBoolean("math.solver.enable"));

        mEditorView.setEngine(mEngine);
        final Editor editor = mEditorView.getEditor();
        editor.setPenStyle("color: #ff000000;");

        mEditorView.setInputControllerListener(new IInputControllerListener() {
            @Override
            public boolean onLongPress(float x, float y, ContentBlock contentBlock) {
                return false;
            }

            public void onTouchUp() {
                mRecognitionTimer.cancel();
                mRecognitionTimer.start();
            }
        });
        mEditorView.setInputMode(InputController.INPUT_MODE_FORCE_PEN);

        String packageName = "math.iink";
        File file = new File(getFilesDir(), packageName);
        try {
            mContentPackage = mEngine.createPackage(file);
            mContentPart = mContentPackage.createPart("Math"); // Choose type of content (possible values are: "Text Document", "Text", "Diagram", "Math", and "Drawing")
        } catch (IOException e) {
            Log.e(TAG, "Failed to open package \"" + packageName + "\"", e);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to open package \"" + packageName + "\"", e);
        }

        mEditorView.post(new Runnable() {
            @Override
            public void run() {
                mEditorView.getRenderer().setViewOffset(0, 0);
                mEditorView.getRenderer().setViewScale(1);
                mEditorView.setVisibility(View.VISIBLE);
                editor.setPart(mContentPart);
                Log.d(TAG, "initial(): mEditorView visible");
            }
        });
    }

    private boolean isInvalidFormula(String formula) {
        return !formula.contains("=") && !formula.contains("≃");
    }

    private View.OnClickListener mBtnListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btn_delete:
                    stopSelf();
                    break;
                case R.id.btn_clear:
                    Log.d(TAG, "onClick: Clear");
                    mEditorView.getEditor().clear();
                    break;
                case R.id.btn_recognize:
                    recognize();
                    break;
            }
        }
    };

    private View.OnTouchListener mOnTouchListener = new View.OnTouchListener() {
        public boolean onTouch(View v, MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                mLastTouchX = ev.getRawX();
                mLastTouchY = ev.getRawY();
            } else if (ev.getAction() == MotionEvent.ACTION_MOVE) {
                final float dx = ev.getRawX() - mLastTouchX;
                final float dy = ev.getRawY() - mLastTouchY;

                mLastTouchX = ev.getRawX();
                mLastTouchY = ev.getRawY();

                int tempWidth = mLayoutParams.width + (int) dx;
                int tempHeight = mLayoutParams.height + (int) dy;

                if (tempWidth <= mMinWidth) {
                    mLayoutParams.width = mMinWidth;
                } else {
                    mLayoutParams.width = tempWidth;
                    mLayoutParams.x += (int) (dx / 2);
                }
                if (tempHeight <= mMinHeight) {
                    mLayoutParams.height = mMinHeight;
                } else {
                    mLayoutParams.height = tempHeight;
                    mLayoutParams.y += (int) (dy / 2);
                }

                updateViewLayout();
            }
            return false;
        }
    };

    private void recognize() {
        Editor editor = mEditorView.getEditor();
        if (editor == null) return;

        ConversionState[] supportedStates = editor.getSupportedTargetConversionStates(null);
        if (supportedStates.length > 0) {
            try {
                String content = editor.export_(null, MimeType.TEXT);
                Log.d(TAG, "recognize(): content = " + content);

                if (content.contains(";")) {
                    String[] recognitionList = content.split(" ; ");
                    for (String formula : recognitionList) {
                        if (isInvalidFormula(formula)) return;
                    }
                } else if (isInvalidFormula(content)) {
                    return;
                }
                editor.convert(null, supportedStates[0]);
                String recognition = editor.export_(null, MimeType.TEXT);

                if (recognition.contains(";")) {
                    recognition = recognition.split(" ; ")[0];
                }

                String formula = recognition;
                String calculatorResult = "";
                String relation = "";
                if (recognition.contains("=")) {
                    relation = "=";
                } else if (recognition.contains("≃")) {
                    relation = "≃";
                }
                if (!relation.isEmpty()) {
                    formula = recognition.substring(0, recognition.indexOf(relation));
                    calculatorResult = recognition.substring(recognition.indexOf(relation) + 1);
                }

                mResultText.setText(calculatorResult);
                mFormulaText.setText(formula);
            } catch (IOException e) {
                Log.e(TAG, "recognize(): fail to export recognition string", e);
            }
        }
    }
}
