package com.baidu.paddle.lite.demo.tts;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.baidu.paddle.lite.MobileConfig;
import com.baidu.paddle.lite.PaddlePredictor;
import com.baidu.paddle.lite.PowerMode;
import com.baidu.paddle.lite.Tensor;

import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.Vector;


public class Predictor {
    private static final String TAG = Predictor.class.getSimpleName();
    public boolean isLoaded = false;
    public int warmupIterNum = 1;
    public int inferIterNum = 1;
    public int cpuThreadNum = 1;
    public String cpuPowerMode = "LITE_POWER_HIGH";
    public String modelPath = "";
    protected PaddlePredictor AMPredictor = null;
    protected PaddlePredictor VOCPredictor = null;
    protected float inferenceTime = 0;
    // Only for image classification
    protected long[] inputShape = new long[]{1, 3, 224, 224};

    public Predictor() {
    }

    public boolean init(Context appCtx, String modelPath, String AMmodelName, String VOCmodelName, int cpuThreadNum, String cpuPowerMode) {
        if (inputShape.length != 4) {
            Log.i(TAG, "Size of input shape should be: 4");
            return false;
        }

        if (inputShape[0] != 1) {
            Log.i(TAG, "Only one batch is supported in the image classification demo, you can use any batch size in " +
                    "your Apps!");
            return false;
        }
        if (inputShape[1] != 1 && inputShape[1] != 3) {
            Log.i(TAG, "Only one/three channels are supported in the image classification demo, you can use any " +
                    "channel size in your Apps!");
            return false;
        }
        // Release model if exists
        releaseModel();

        AMPredictor = loadModel(appCtx, modelPath, AMmodelName, cpuThreadNum, cpuPowerMode);
        if (AMPredictor==null) {
            Log.i(TAG, "Load am failed!!!!");
            return false;
        }
        Log.i(TAG, "Load am success!!!!");
        VOCPredictor = loadModel(appCtx, modelPath, VOCmodelName, cpuThreadNum, cpuPowerMode);
        if (VOCPredictor==null) {
            Log.i(TAG, "Load voc failed!!!!");
            return false;
        }
        Log.i(TAG, "Load voc success!!!!");
        isLoaded = true;
        return true;
    }

    protected PaddlePredictor loadModel(Context appCtx, String modelPath, String modelName, int cpuThreadNum, String cpuPowerMode) {
        // Load model
        if (modelPath.isEmpty()) {
            return null;
        }
        String realPath = modelPath;
        if (!modelPath.substring(0, 1).equals("/")) {
            // Read model files from custom path if the first character of mode path is '/'
            // otherwise copy model to cache from assets
            realPath = appCtx.getCacheDir() + "/" + modelPath;
            // push model to mobile
            Utils.copyDirectoryFromAssets(appCtx, modelPath, realPath);
        }
        if (realPath.isEmpty()) {
            return null;
        }
        MobileConfig config = new MobileConfig();
        config.setModelFromFile(realPath + File.separator + modelName);
        Log.e(TAG, "File:"+realPath + File.separator + modelName);
        config.setThreads(cpuThreadNum);
        if (cpuPowerMode.equalsIgnoreCase("LITE_POWER_HIGH")) {
            config.setPowerMode(PowerMode.LITE_POWER_HIGH);
        } else if (cpuPowerMode.equalsIgnoreCase("LITE_POWER_LOW")) {
            config.setPowerMode(PowerMode.LITE_POWER_LOW);
        } else if (cpuPowerMode.equalsIgnoreCase("LITE_POWER_FULL")) {
            config.setPowerMode(PowerMode.LITE_POWER_FULL);
        } else if (cpuPowerMode.equalsIgnoreCase("LITE_POWER_NO_BIND")) {
            config.setPowerMode(PowerMode.LITE_POWER_NO_BIND);
        } else if (cpuPowerMode.equalsIgnoreCase("LITE_POWER_RAND_HIGH")) {
            config.setPowerMode(PowerMode.LITE_POWER_RAND_HIGH);
        } else if (cpuPowerMode.equalsIgnoreCase("LITE_POWER_RAND_LOW")) {
            config.setPowerMode(PowerMode.LITE_POWER_RAND_LOW);
        } else {
            Log.e(TAG, "Unknown cpu power mode!");
            return null;
        }
          return PaddlePredictor.createPaddlePredictor(config);
    }

    public void releaseModel() {
        AMPredictor = null;
        VOCPredictor = null;
        isLoaded = false;
        cpuThreadNum = 1;
        cpuPowerMode = "LITE_POWER_HIGH";
        modelPath = "";
    }

    public boolean runModel() {
        Log.e(TAG, "in runModel");
        if (!isLoaded()) {
            return false;
        }
        Log.e(TAG, "in runModel222222");
        float[] phones={261, 231, 175, 116, 179, 262, 44, 154, 126, 177, 19, 262, 42, 241, 72, 177, 56, 174, 245, 37, 186, 37, 49, 151, 127, 69, 19, 179, 72, 69, 4, 260, 126, 177, 116, 151, 239, 153, 141};
        Log.e(TAG, "in runModel33333333");
        getAMOutput(phones,AMPredictor);

//        // Warm up
//        for (int i = 0; i < warmupIterNum; i++) {
//            AMPredictor.run();
//            VOCPredictor.run();
//        }
//        // Run inference
          Date start = new Date();
//        for (int i = 0; i < inferIterNum; i++) {
//            AMPredictor.run();
//            VOCPredictor.run();
//        }
        Date end = new Date();
        inferenceTime = (end.getTime() - start.getTime()) / (float) inferIterNum;

        // Fetch output tensor
        return true;
    }

    public float[] getAMOutput(float[] phones, PaddlePredictor am_predictor) {
        Tensor phones_handle = am_predictor.getInput(0);
        long[] dims = {phones.length};
        Log.e(TAG, Arrays.toString(dims));
        phones_handle.resize(dims);
        phones_handle.setData(phones);
        am_predictor.run();
        Tensor am_output_handle = am_predictor.getOutput(0);
        // [349, 80]
        long outputShape[] = am_output_handle.shape();
        Log.e(TAG, Arrays.toString(outputShape));
        float[] am_output_data = am_output_handle.getFloatData();
        // [349 * 80]
        long[] am_output_data_shape = {am_output_data.length};
        Log.e(TAG, Arrays.toString(am_output_data_shape));
        // Log.e(TAG, Arrays.toString(am_output_data));
        // 打印 mel 数组
        for (int i=0;i<outputShape[0];i++) {
            Log.e(TAG, Arrays.toString(Arrays.copyOfRange(am_output_data,i*80,(i+1)*80)));
        }
        Log.e(TAG, "in getAMOutput777777");
        return am_output_data;
    }


    public boolean isLoaded() {
        return AMPredictor != null && VOCPredictor != null && isLoaded;
    }



    public float inferenceTime() {
        return inferenceTime;
    }


}
