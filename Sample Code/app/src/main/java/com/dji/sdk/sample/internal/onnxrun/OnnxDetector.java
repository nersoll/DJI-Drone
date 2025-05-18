package com.dji.sdk.sample.internal.onnxrun;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import ai.onnxruntime.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.*;

public class OnnxDetector {
    private static final String TAG = "ONNX_DETECTION";
    private final OrtEnvironment env;
    private final OrtSession session;

    public OnnxDetector(Context context, String modelName) throws Exception {
        env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        File modelFile = new File(context.getFilesDir(), modelName);

        if (!modelFile.exists()) {
            try (InputStream is = context.getAssets().open(modelName);
                 FileOutputStream fos = new FileOutputStream(modelFile)) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
            }
        }

        session = env.createSession(modelFile.getAbsolutePath(), opts);
    }

    public List<float[]> runModel(Bitmap bitmap) {
        try {
            Bitmap resized = Bitmap.createScaledBitmap(bitmap, 640, 640, true);
            float[] inputData = preprocessImage(resized);
            OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), new long[]{1, 3, 640, 640});
            OrtSession.Result result = session.run(Collections.singletonMap("images", inputTensor));
            float[][][] outputs = (float[][][]) result.get(0).getValue();
            float[][] output0 = outputs[0];
            float[][] output = transpose(output0);
            return extractBoxes(output, 0.25f, 0.45f);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка инференса: ", e);
            return Collections.emptyList();
        }
    }

    private float[] preprocessImage(Bitmap bitmap) {
        float[] result = new float[3 * 640 * 640];
        int[] pixels = new int[640 * 640];
        bitmap.getPixels(pixels, 0, 640, 0, 0, 640, 640);
        for (int i = 0; i < pixels.length; i++) {
            int px = pixels[i];
            float r = ((px >> 16) & 0xFF) / 255.0f;
            float g = ((px >> 8) & 0xFF) / 255.0f;
            float b = (px & 0xFF) / 255.0f;
            int row = i / 640;
            int col = i % 640;
            result[0 * 640 * 640 + row * 640 + col] = b;
            result[1 * 640 * 640 + row * 640 + col] = g;
            result[2 * 640 * 640 + row * 640 + col] = r;
        }
        return result;
    }

    private float[][] transpose(float[][] array) {
        int rows = array.length, cols = array[0].length;
        float[][] transposed = new float[cols][rows];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                transposed[j][i] = array[i][j];
        return transposed;
    }

    private List<float[]> extractBoxes(float[][] output, float confThreshold, float iouThreshold) {
        List<float[]> detections = new ArrayList<>();
        int numClasses = output[0].length - 4;

        for (float[] det : output) {
            float[] scores = Arrays.copyOfRange(det, 4, det.length);
            int classId = argmax(scores);
            float conf = scores[classId];
            if (conf > confThreshold) {
                float[] box = new float[6];
                System.arraycopy(det, 0, box, 0, 4);
                box[4] = conf;
                box[5] = classId;
                detections.add(box);
            }
        }
        return nonMaximumSuppression(detections, iouThreshold);
    }

    private List<float[]> nonMaximumSuppression(List<float[]> boxes, float iouThreshold) {
        boxes.sort((a, b) -> Float.compare(b[4], a[4]));
        List<float[]> picked = new ArrayList<>();
        for (float[] box : boxes) {
            boolean keep = true;
            for (float[] sel : picked) {
                if (iou(box, sel) > iouThreshold) {
                    keep = false;
                    break;
                }
            }
            if (keep) picked.add(box);
        }
        return picked;
    }

    private float iou(float[] box1, float[] box2) {
        float x1 = Math.max(box1[0] - box1[2] / 2, box2[0] - box2[2] / 2);
        float y1 = Math.max(box1[1] - box1[3] / 2, box2[1] - box2[3] / 2);
        float x2 = Math.min(box1[0] + box1[2] / 2, box2[0] + box2[2] / 2);
        float y2 = Math.min(box1[1] + box1[3] / 2, box2[1] + box2[3] / 2);
        float w = Math.max(0, x2 - x1);
        float h = Math.max(0, y2 - y1);
        float inter = w * h;
        float area1 = box1[2] * box1[3];
        float area2 = box2[2] * box2[3];
        return inter / (area1 + area2 - inter);
    }

    private int argmax(float[] array) {
        int maxIdx = 0;
        float max = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i] > max) {
                max = array[i];
                maxIdx = i;
            }
        }
        return maxIdx;
    }
}
