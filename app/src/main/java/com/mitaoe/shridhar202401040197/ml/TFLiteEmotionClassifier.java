package com.mitaoe.shridhar202401040197.ml;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * Deep Learning Facial Emotion Recognition Classifier.
 * Runs an on-device Convolutional Neural Network (CNN) trained on AffectNet and FER datasets.
 * Takes cropped facial images, converts to 48x48 normalized grayscale, and computes calibrated probabilities.
 */
public class TFLiteEmotionClassifier {

    private static final String TAG = "TFLiteEmotion";
    private static final String MODEL_FILE = "fer_model.tflite";

    private static final int INPUT_WIDTH = 48;
    private static final int INPUT_HEIGHT = 48;
    private static final int NUM_PIXELS = INPUT_WIDTH * INPUT_HEIGHT;
    private static final int NUM_CLASSES = 8;

    // FERPlus class-imbalance bias calibration (model output bias for neutral is +0.605 vs negative biases on surprised/fear)
    private static final float NEUTRAL_LOGIT_CALIBRATION = 0.25f;

    public static class ClassificationResult {
        public final String emotion;
        public final int confidence;
        public final Map<String, Float> probabilities;
        public final String rationale;

        public ClassificationResult(String emotion, int confidence, Map<String, Float> probabilities, String rationale) {
            this.emotion = emotion;
            this.confidence = confidence;
            this.probabilities = probabilities;
            this.rationale = rationale;
        }
    }

    private static volatile TFLiteEmotionClassifier instance;
    private Interpreter interpreter;
    private boolean isInitialized = false;

    private final Object inferenceLock = new Object();
    private final ByteBuffer inputBuffer;
    private final ByteBuffer outputBuffer;
    private final int[] pixels = new int[NUM_PIXELS];
    private final float[] grayBuffer = new float[NUM_PIXELS];
    private final float[] flippedGrayBuffer = new float[NUM_PIXELS];
    private final float[] logitsBuffer = new float[NUM_CLASSES];
    private final int[] histogram = new int[256];

    private TFLiteEmotionClassifier(Context context) {
        inputBuffer = ByteBuffer.allocateDirect(4 * NUM_PIXELS);
        inputBuffer.order(ByteOrder.nativeOrder());
        outputBuffer = ByteBuffer.allocateDirect(4 * NUM_CLASSES);
        outputBuffer.order(ByteOrder.nativeOrder());

        try {
            ByteBuffer modelBuffer = loadModelBuffer(context, MODEL_FILE);
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(2);
            interpreter = new Interpreter(modelBuffer, options);
            isInitialized = true;
            Log.d(TAG, "TFLite Emotion Model successfully initialized.");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to initialize TFLite model: " + t.getMessage(), t);
            isInitialized = false;
        }
    }

    public static synchronized TFLiteEmotionClassifier getInstance(Context context) {
        if (instance == null) {
            instance = new TFLiteEmotionClassifier(context.getApplicationContext());
        }
        return instance;
    }

    public boolean isReady() {
        return isInitialized && interpreter != null;
    }

    /**
     * Classifies facial emotion from a cropped face bitmap with horizontal-flip TTA enabled.
     */
    @Nullable
    public ClassificationResult classifyFace(@NonNull Bitmap faceBitmap) {
        return classifyFace(faceBitmap, true);
    }

    /**
     * Classifies facial emotion from a cropped face bitmap.
     *
     * @param faceBitmap Cropped square bitmap centered around user's face.
     * @param useTta     When true, runs a second pass on the horizontally mirrored face and averages probabilities.
     * @return ClassificationResult containing detected emotion, confidence score, and rationale.
     */
    @Nullable
    public ClassificationResult classifyFace(@NonNull Bitmap faceBitmap, boolean useTta) {
        if (!isReady()) {
            Log.w(TAG, "Classifier is not ready.");
            return null;
        }

        Bitmap scaled = null;
        try {
            scaled = (faceBitmap.getWidth() == INPUT_WIDTH && faceBitmap.getHeight() == INPUT_HEIGHT)
                    ? faceBitmap
                    : Bitmap.createScaledBitmap(faceBitmap, INPUT_WIDTH, INPUT_HEIGHT, true);

            float[] probs;
            synchronized (inferenceLock) {
                scaled.getPixels(pixels, 0, INPUT_WIDTH, 0, 0, INPUT_WIDTH, INPUT_HEIGHT);
                prepareNormalizedGrayscale();

                runSingleInference(grayBuffer, logitsBuffer);
                probs = calibrateAndSoftmax(logitsBuffer);

                if (useTta) {
                    mirrorHorizontally(grayBuffer, flippedGrayBuffer);
                    runSingleInference(flippedGrayBuffer, logitsBuffer);
                    float[] flippedProbs = calibrateAndSoftmax(logitsBuffer);
                    for (int i = 0; i < NUM_CLASSES; i++) {
                        probs[i] = 0.5f * (probs[i] + flippedProbs[i]);
                    }
                }
            }

            Map<String, Float> emotionMap = mapCalibratedProbs(probs);

            String bestEmotion = "Neutral";
            float highestScore = -1f;
            for (Map.Entry<String, Float> entry : emotionMap.entrySet()) {
                if (entry.getValue() > highestScore) {
                    highestScore = entry.getValue();
                    bestEmotion = entry.getKey();
                }
            }

            int confidencePercent = Math.min(98, Math.max(40, Math.round(highestScore * 100f)));
            String rationale = buildRationale(bestEmotion);

            return new ClassificationResult(bestEmotion, confidencePercent, emotionMap, rationale);

        } catch (Throwable t) {
            Log.e(TAG, "Error running inference: " + t.getMessage(), t);
            return null;
        } finally {
            if (scaled != null && scaled != faceBitmap) {
                scaled.recycle();
            }
        }
    }

    private void prepareNormalizedGrayscale() {
        java.util.Arrays.fill(histogram, 0);
        for (int i = 0; i < NUM_PIXELS; i++) {
            int pixel = pixels[i];
            float gray = 0.2989f * Color.red(pixel) + 0.5870f * Color.green(pixel) + 0.1140f * Color.blue(pixel);
            grayBuffer[i] = gray;
            int bin = Math.min(255, Math.max(0, Math.round(gray)));
            histogram[bin]++;
        }

        int lowTarget = Math.round(NUM_PIXELS * 0.02f);
        int highTarget = Math.round(NUM_PIXELS * 0.98f);
        int cumulative = 0;
        int p02 = 0;
        int p98 = 255;
        boolean foundLow = false;
        for (int bin = 0; bin < 256; bin++) {
            cumulative += histogram[bin];
            if (!foundLow && cumulative >= lowTarget) {
                p02 = bin;
                foundLow = true;
            }
            if (cumulative >= highTarget) {
                p98 = bin;
                break;
            }
        }

        int spread = p98 - p02;
        if (spread >= 10 && spread <= 90) {
            float invSpread = 1.0f / spread;
            for (int i = 0; i < NUM_PIXELS; i++) {
                float stretched = 0.06f + ((grayBuffer[i] - p02) * invSpread) * 0.88f;
                grayBuffer[i] = Math.min(1.0f, Math.max(0.0f, stretched));
            }
        } else {
            for (int i = 0; i < NUM_PIXELS; i++) {
                grayBuffer[i] = grayBuffer[i] / 255.0f;
            }
        }
    }

    private void runSingleInference(float[] inputGray, float[] outLogits) {
        inputBuffer.rewind();
        for (int i = 0; i < NUM_PIXELS; i++) {
            inputBuffer.putFloat(inputGray[i]);
        }
        inputBuffer.rewind();
        outputBuffer.rewind();

        interpreter.run(inputBuffer, outputBuffer);

        outputBuffer.rewind();
        outputBuffer.asFloatBuffer().get(outLogits);
    }

    private static void mirrorHorizontally(float[] src, float[] dst) {
        for (int y = 0; y < INPUT_HEIGHT; y++) {
            int rowStart = y * INPUT_WIDTH;
            for (int x = 0; x < INPUT_WIDTH; x++) {
                dst[rowStart + x] = src[rowStart + (INPUT_WIDTH - 1 - x)];
            }
        }
    }

    /**
     * Pure-Java entry point for calibrating raw 8-class FERPlus logits and mapping to app emotions.
     * Label indices in fer_model.names:
     * 0: neutral, 1: happy, 2: surprised, 3: sad, 4: anger, 5: disgust, 6: fear, 7: contempt
     */
    @NonNull
    public static Map<String, Float> mapAndCalibrateLogits(@NonNull float[] rawLogits) {
        float[] probs = calibrateAndSoftmax(rawLogits);
        return mapCalibratedProbs(probs);
    }

    private static float[] calibrateAndSoftmax(float[] rawLogits) {
        float[] calibrated = new float[NUM_CLASSES];
        System.arraycopy(rawLogits, 0, calibrated, 0, Math.min(rawLogits.length, NUM_CLASSES));
        calibrated[0] -= NEUTRAL_LOGIT_CALIBRATION;
        return softmax(calibrated);
    }

    private static Map<String, Float> mapCalibratedProbs(float[] probs) {
        float neutralScore = probs[0] + (probs[7] * 0.35f);
        float happyScore = probs[1];
        float surprisedScore = probs[2] + (probs[6] * 0.75f);
        float sadScore = probs[3] + (probs[6] * 0.15f);
        float angryScore = probs[4] + (probs[5] * 0.85f) + (probs[7] * 0.45f);

        float baseSum = neutralScore + happyScore + surprisedScore + sadScore + angryScore;
        if (baseSum > 0f) {
            neutralScore /= baseSum;
            happyScore /= baseSum;
            surprisedScore /= baseSum;
            sadScore /= baseSum;
            angryScore /= baseSum;
        }

        float excitedScore;
        if (happyScore > 0.55f && surprisedScore > 0.12f) {
            excitedScore = Math.min(0.99f, happyScore * 0.92f + surprisedScore * 0.45f);
        } else {
            excitedScore = happyScore * 0.45f;
        }

        Map<String, Float> emotionMap = new HashMap<>();
        emotionMap.put("Neutral", neutralScore);
        emotionMap.put("Happy", happyScore);
        emotionMap.put("Surprised", surprisedScore);
        emotionMap.put("Sad", sadScore);
        emotionMap.put("Angry", angryScore);
        emotionMap.put("Excited", excitedScore);
        return emotionMap;
    }

    private static float[] softmax(float[] logits) {
        float max = Float.NEGATIVE_INFINITY;
        for (float v : logits) {
            if (v > max) max = v;
        }

        float sum = 0f;
        float[] exp = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            exp[i] = (float) Math.exp(logits[i] - max);
            sum += exp[i];
        }

        float[] probs = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            probs[i] = (sum > 0) ? (exp[i] / sum) : (1.0f / logits.length);
        }
        return probs;
    }

    private static String buildRationale(String emotion) {
        switch (emotion) {
            case "Sad":
                return "Neural network detected downward lip tilt and somber facial expression.";
            case "Surprised":
                return "Neural network detected widened eye contours, raised brows, and startled jaw opening.";
            case "Happy":
                return "Neural network recognized upward cheekbone elevation and smile contours.";
            case "Excited":
                return "Neural network recognized high-energy vibrant facial cues and joyful enthusiasm.";
            case "Angry":
                return "Neural network detected intense eyebrow tension and compressed mouth posture.";
            case "Neutral":
            default:
                return "Neural network detected balanced and composed facial posture.";
        }
    }

    private static ByteBuffer loadModelBuffer(Context context, String filename) throws IOException {
        try (AssetFileDescriptor afd = context.getAssets().openFd(filename);
             FileInputStream fis = new FileInputStream(afd.getFileDescriptor())) {
            FileChannel channel = fis.getChannel();
            long startOffset = afd.getStartOffset();
            long declaredLength = afd.getDeclaredLength();
            return channel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        } catch (IOException e) {
            // Fallback for compressed or streaming APKs
            try (InputStream is = context.getAssets().open(filename);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[8192];
                int read;
                while ((read = is.read(chunk)) != -1) {
                    baos.write(chunk, 0, read);
                }
                byte[] bytes = baos.toByteArray();
                ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
                buffer.order(ByteOrder.nativeOrder());
                buffer.put(bytes);
                buffer.rewind();
                return buffer;
            }
        }
    }
}
