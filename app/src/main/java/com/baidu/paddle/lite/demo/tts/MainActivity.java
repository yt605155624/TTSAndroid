package com.baidu.paddle.lite.demo.tts;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener {
    public static final int REQUEST_LOAD_MODEL = 0;
    public static final int REQUEST_RUN_MODEL = 1;
    public static final int RESPONSE_LOAD_MODEL_SUCCESSED = 0;
    public static final int RESPONSE_LOAD_MODEL_FAILED = 1;
    public static final int RESPONSE_RUN_MODEL_SUCCESSED = 2;
    public static final int RESPONSE_RUN_MODEL_FAILED = 3;

    protected ProgressDialog pbLoadModel = null;
    protected ProgressDialog pbRunModel = null;
    // Receive messages from worker thread
    protected Handler receiver = null;
    // Send command to worker thread
    protected Handler sender = null;
    // Worker thread to load&run model
    protected HandlerThread worker = null;

    // UI components of image classification
    protected TextView tvInputSetting;
    protected TextView tvInferenceTime;

    protected Button btn_syn ;
    protected Button btn_play;
    protected Button btn_pause ;
    protected Button btn_stop ;


    // protected Switch mSwitch;

    // Model settings of image classification
    protected String modelPath = "";
    protected int cpuThreadNum = 1;
    protected String cpuPowerMode = "";
    protected boolean useGpu = false;
    private static final String TAG = Predictor.class.getSimpleName();

    protected Predictor predictor = new Predictor();
    private MediaPlayer mediaPlayer = new MediaPlayer();
    private String wavName = "hf.wav";
    private String wavFile = Environment.getExternalStorageDirectory() + File.separator + wavName;
    private String AMmodelName = "fastspeech2_csmsc_arm.nb";
    private String VOCmodelName = "mb_melgan_csmsc_arm.nb";
    private float[] phones = {261, 231, 175, 116, 179, 262, 44, 154, 126, 177, 19, 262, 42, 241, 72, 177, 56, 174, 245, 37, 186, 37, 49, 151, 127, 69, 19, 179, 72, 69, 4, 260, 126, 177, 116, 151, 239, 153, 141};
//    private float[] phones = {261, 231, 175, 116, 179, 262, 44, 154, 126, 177, 19, 262};
    int sampleRate=24000;

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_syn:
                if (onLoadModelSuccessed()) {
                    Log.e(TAG,"R.id.btn_syn");
                    runModel();
                }
                break;
            case R.id.btn_play:
                if (!mediaPlayer.isPlaying()) {
                    mediaPlayer.start();
                }
                break;
            case R.id.btn_pause:
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                }
                break;
            case R.id.btn_stop:
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.reset();
                    initMediaPlayer();
                }
                break;
            default:
                break;
        }
    }
    private void initMediaPlayer() {
        try {
            File file = new File(wavFile);
//            file.createNewFile();
            // 指定音频文件的路径
            mediaPlayer.setDataSource(file.getPath());
            // 让 MediaPlayer 进入到准备状态
            mediaPlayer.prepare();
            // 该方法使得进入应用时就播放音频
            // mediaPlayer.setOnPreparedListener(this);
            // prepare async to not block main thread
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPrepared(MediaPlayer player){
        player.start();
        Log.d("TAG","prepared");
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        // The MediaPlayer has moved to the Error state, must be reset!
        mediaPlayer.reset();
        initMediaPlayer();
        return true;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestAllPermissions();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btn_syn = findViewById(R.id.btn_syn);
        btn_play = findViewById(R.id.btn_play);
        btn_pause =  findViewById(R.id.btn_pause);
        btn_stop = findViewById(R.id.btn_stop);

        btn_syn.setOnClickListener(this);
        btn_play.setOnClickListener(this);
        btn_pause.setOnClickListener(this);
        btn_stop.setOnClickListener(this);

        btn_play.setVisibility(View.INVISIBLE);
        btn_pause.setVisibility(View.INVISIBLE);
        btn_stop.setVisibility(View.INVISIBLE);


        // Clear all setting items to avoid app crashing due to the incorrect settings
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.commit();

        // Prepare the worker thread for mode loading and inference
        receiver = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case RESPONSE_LOAD_MODEL_SUCCESSED:
                        pbLoadModel.dismiss();
                        onLoadModelSuccessed();
                        break;
                    case RESPONSE_LOAD_MODEL_FAILED:
                        pbLoadModel.dismiss();
                        Toast.makeText(MainActivity.this, "Load model failed!", Toast.LENGTH_SHORT).show();
                        onLoadModelFailed();
                        break;
                    case RESPONSE_RUN_MODEL_SUCCESSED:
                        pbRunModel.dismiss();
                        onRunModelSuccessed();
                        break;
                    case RESPONSE_RUN_MODEL_FAILED:
                        pbRunModel.dismiss();
                        Toast.makeText(MainActivity.this, "Run model failed!", Toast.LENGTH_SHORT).show();
                        onRunModelFailed();
                        break;
                    default:
                        break;
                }
            }
        };

        worker = new HandlerThread("Predictor Worker");
        worker.start();
        sender = new Handler(worker.getLooper()) {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case REQUEST_LOAD_MODEL:
                        // Load model and reload test image
                        if (onLoadModel()) {
                            receiver.sendEmptyMessage(RESPONSE_LOAD_MODEL_SUCCESSED);
                        } else {
                            receiver.sendEmptyMessage(RESPONSE_LOAD_MODEL_FAILED);
                        }
                        break;
                    case REQUEST_RUN_MODEL:
                        // Run model if model is loaded
                        if (onRunModel()) {
                            Log.e(TAG, "4444444");
                            receiver.sendEmptyMessage(RESPONSE_RUN_MODEL_SUCCESSED);
                        } else {
                            receiver.sendEmptyMessage(RESPONSE_RUN_MODEL_FAILED);
                        }
                        break;
                    default:
                        break;
                }
            }
        };

        // Setup the UI components
        tvInputSetting = findViewById(R.id.tv_input_setting);
        tvInferenceTime = findViewById(R.id.tv_inference_time);
        tvInputSetting.setMovementMethod(ScrollingMovementMethod.getInstance());
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean settingsChanged = false;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String model_path = sharedPreferences.getString(getString(R.string.MODEL_PATH_KEY),
                getString(R.string.MODEL_PATH_DEFAULT));

        settingsChanged |= !model_path.equalsIgnoreCase(modelPath);

        int cpu_thread_num = Integer.parseInt(sharedPreferences.getString(getString(R.string.CPU_THREAD_NUM_KEY),
                getString(R.string.CPU_THREAD_NUM_DEFAULT)));
        settingsChanged |= cpu_thread_num != cpuThreadNum;
        String cpu_power_mode =
                sharedPreferences.getString(getString(R.string.CPU_POWER_MODE_KEY),
                        getString(R.string.CPU_POWER_MODE_DEFAULT));
        settingsChanged |= !cpu_power_mode.equalsIgnoreCase(cpuPowerMode);

        if (settingsChanged) {
            modelPath = model_path;
            cpuThreadNum = cpu_thread_num;
            cpuPowerMode = cpu_power_mode;
            if (useGpu) {
                modelPath = modelPath.split("/")[0] + "/gpu";
            } else {
                modelPath = modelPath.split("/")[0] + "/cpu";
            }
            // Update UI
            tvInputSetting.setText("Model: " + modelPath.substring(modelPath.lastIndexOf("/") + 1) + "\n" + "CPU" +
                    " Thread Num: " + Integer.toString(cpuThreadNum) + "\n" + "CPU Power Mode: " + cpuPowerMode + "\n");
            tvInputSetting.scrollTo(0, 0);
            // Reload model if configure has been changed
            loadModel();
        }
    }

    public void loadModel() {
        Log.e(TAG,"Loading model...");
        pbLoadModel = ProgressDialog.show(this, "", "Loading model...", false, false);
        sender.sendEmptyMessage(REQUEST_LOAD_MODEL);
    }

    public void runModel() {
        pbRunModel = ProgressDialog.show(this, "", "Running model...", false, false);
        sender.sendEmptyMessage(REQUEST_RUN_MODEL);
    }

    public boolean onLoadModel() {
        return predictor.init(MainActivity.this, modelPath, AMmodelName, VOCmodelName, cpuThreadNum,
                cpuPowerMode);
    }

    public boolean onRunModel() {
        return predictor.isLoaded() && predictor.runModel(phones);
    }

    public boolean onLoadModelSuccessed() {
        // Load test image from path and run model
//        runModel();
        return true;
    }

    public void onLoadModelFailed() {
    }

    public void onRunModelSuccessed() {
        // Obtain results and update UI
        btn_play.setVisibility(View.VISIBLE);
        btn_pause.setVisibility(View.VISIBLE);
        btn_stop.setVisibility(View.VISIBLE);
        tvInferenceTime.setText(" Inference done！\n Inference time: " + predictor.inferenceTime() + " ms" + "\n Audio saved in " + wavFile);
        try {
            Utils.rawToWave(wavFile, predictor.wav, sampleRate);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{ Manifest.permission. WRITE_EXTERNAL_STORAGE }, 1);
        } else {
            // 初始化MediaPlayer
            initMediaPlayer();
        }
    }

    public void onRunModelFailed() {
    }


    public void onSettingsClicked() {
        startActivity(new Intent(MainActivity.this, SettingsActivity.class));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_action_options, menu);
        return true;
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean isLoaded = predictor.isLoaded();
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.settings:
                onSettingsClicked();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    protected void onDestroy() {
        if (predictor != null) {
            predictor.releaseModel();
        }
        worker.quit();
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }
    }

    private boolean requestAllPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    0);
            return false;
        }
        return true;
    }
}
