package com.mitaoe.shridhar202401040197.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;
import com.mitaoe.shridhar202401040197.MyEmotionsApp;
import com.mitaoe.shridhar202401040197.ml.TFLiteEmotionClassifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Multimodal Emotion Recognition Engine.
 * Combines an on-device TensorFlow Lite CNN (trained on AffectNet/FERPlus),
 * eye-aligned tight square face cropping, FACS-inspired geometric landmark signals,
 * and NLP reflection note sentiment analysis.
 */
public class EmotionRecognitionEngine {

    private static final String[] EMOTION_KEYS = {
            "Neutral", "Happy", "Surprised", "Sad", "Angry", "Excited"
    };

    private static final String[] BASE_KEYS = {
            "Neutral", "Happy", "Surprised", "Sad", "Angry"
    };

    private static volatile FaceDetector accurateDetector;
    private static volatile FaceDetector liveDetector;

    public static class EmotionResult {
        public final String emotion;
        public final String emoji;
        public final int confidence;
        public final String rationale;
        public final Map<String, Float> scores;

        public EmotionResult(String emotion, String emoji, int confidence, String rationale, Map<String, Float> scores) {
            this.emotion = emotion;
            this.emoji = emoji;
            this.confidence = confidence;
            this.rationale = rationale;
            this.scores = scores != null ? scores : new HashMap<>();
        }

        public String getFormatted() {
            return emoji + " " + emotion;
        }
    }

    /**
     * Rotation- and scale-invariant facial landmark features extracted from ML Kit contours & classifications.
     */
    public static class LandmarkSignals {
        public final float smileProb;
        public final float avgEyeOpen;
        public final float innerMouthOpenRatio;
        public final float lipCornerLift;
        public final float browRaiseRatio;
        public final float browFurrowRatio;
        public final float eyeAspectRatio;
        public final float headPitch;
        public final boolean hasMouthContours;
        public final boolean hasBrowContours;
        public final boolean hasEyeContours;

        public LandmarkSignals(
                float smileProb,
                float avgEyeOpen,
                float innerMouthOpenRatio,
                float lipCornerLift,
                float browRaiseRatio,
                float browFurrowRatio,
                float eyeAspectRatio,
                float headPitch,
                boolean hasMouthContours,
                boolean hasBrowContours,
                boolean hasEyeContours
        ) {
            this.smileProb = smileProb;
            this.avgEyeOpen = avgEyeOpen;
            this.innerMouthOpenRatio = innerMouthOpenRatio;
            this.lipCornerLift = lipCornerLift;
            this.browRaiseRatio = browRaiseRatio;
            this.browFurrowRatio = browFurrowRatio;
            this.eyeAspectRatio = eyeAspectRatio;
            this.headPitch = headPitch;
            this.hasMouthContours = hasMouthContours;
            this.hasBrowContours = hasBrowContours;
            this.hasEyeContours = hasEyeContours;
        }
    }

    /**
     * Exponential Moving Average (EMA) smoother for stabilizing Live AI Camera predictions across frames.
     */
    public static class TemporalEmotionSmoother {
        private static final float ALPHA = 0.40f;
        private final Map<String, Float> emaScores = new HashMap<>();
        private boolean initialized = false;

        public synchronized void reset() {
            emaScores.clear();
            initialized = false;
        }

        @Nullable
        public synchronized EmotionResult smooth(@Nullable EmotionResult current) {
            if (current == null) return null;

            if (!initialized) {
                emaScores.clear();
                for (String key : EMOTION_KEYS) {
                    float val = current.scores.containsKey(key)
                            ? current.scores.get(key)
                            : (key.equalsIgnoreCase(current.emotion) ? current.confidence / 100f : 0f);
                    emaScores.put(key, val);
                }
                initialized = true;
                return current;
            }

            String bestEmotion = current.emotion;
            float highestScore = -1f;
            Map<String, Float> snapshot = new HashMap<>();

            for (String key : EMOTION_KEYS) {
                float prev = emaScores.containsKey(key) ? emaScores.get(key) : 0f;
                float curr = current.scores.containsKey(key)
                        ? current.scores.get(key)
                        : (key.equalsIgnoreCase(current.emotion) ? current.confidence / 100f : 0f);
                float smoothed = ALPHA * curr + (1f - ALPHA) * prev;
                emaScores.put(key, smoothed);
                snapshot.put(key, smoothed);
                if (smoothed > highestScore) {
                    highestScore = smoothed;
                    bestEmotion = key;
                }
            }

            int smoothedConfidence = Math.min(98, Math.max(48, Math.round(highestScore * 100f)));
            String rationale = bestEmotion.equalsIgnoreCase(current.emotion)
                    ? current.rationale
                    : buildVisualRationale(bestEmotion, snapshot, null);

            return new EmotionResult(
                    bestEmotion,
                    getEmojiForEmotion(bestEmotion),
                    smoothedConfidence,
                    rationale,
                    snapshot
            );
        }
    }

    public interface EmotionCallback {
        void onEmotionDetected(EmotionResult result);
        void onNoFaceDetected();
        void onError(Exception e);
    }

    private static FaceDetector getDetector(boolean isLiveFrame) {
        if (isLiveFrame) {
            if (liveDetector == null) {
                synchronized (EmotionRecognitionEngine.class) {
                    if (liveDetector == null) {
                        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                                .setMinFaceSize(0.10f)
                                .build();
                        liveDetector = FaceDetection.getClient(options);
                    }
                }
            }
            return liveDetector;
        } else {
            if (accurateDetector == null) {
                synchronized (EmotionRecognitionEngine.class) {
                    if (accurateDetector == null) {
                        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                                .setMinFaceSize(0.10f)
                                .build();
                        accurateDetector = FaceDetection.getClient(options);
                    }
                }
            }
            return accurateDetector;
        }
    }

    /**
     * Executes the full pipeline in accurate mode (for captured/gallery photos).
     */
    public static void analyzeFaceEmotion(@NonNull Bitmap bitmap, @NonNull EmotionCallback callback) {
        analyzeFaceEmotion(bitmap, false, callback);
    }

    /**
     * Executes the face detection + eye-aligned crop + TFLite CNN + FACS landmark pipeline.
     *
     * @param isLiveFrame When true, uses fast ML Kit mode and skips horizontal-flip TTA for low latency.
     */
    public static void analyzeFaceEmotion(@NonNull Bitmap bitmap, boolean isLiveFrame, @NonNull EmotionCallback callback) {
        try {
            InputImage image = InputImage.fromBitmap(bitmap, 0);
            FaceDetector detector = getDetector(isLiveFrame);

            detector.process(image)
                    .addOnSuccessListener(faces -> {
                        if (faces == null || faces.isEmpty()) {
                            EmotionResult centerCropResult = evaluateCenterCropFallback(bitmap, !isLiveFrame);
                            if (centerCropResult != null) {
                                callback.onEmotionDetected(centerCropResult);
                            } else {
                                callback.onNoFaceDetected();
                            }
                            return;
                        }

                        Face primaryFace = selectPrimaryFace(faces);
                        EmotionResult result = evaluateFaceFeatures(bitmap, primaryFace, !isLiveFrame);
                        callback.onEmotionDetected(result);
                    })
                    .addOnFailureListener(callback::onError);
        } catch (Throwable t) {
            callback.onError(new Exception("Face detection unavailable: " + t.getMessage(), t));
        }
    }

    @NonNull
    private static Face selectPrimaryFace(@NonNull List<Face> faces) {
        Face best = faces.get(0);
        int bestArea = Math.max(0, best.getBoundingBox().width()) * Math.max(0, best.getBoundingBox().height());
        for (int i = 1; i < faces.size(); i++) {
            Face candidate = faces.get(i);
            Rect r = candidate.getBoundingBox();
            int area = Math.max(0, r.width()) * Math.max(0, r.height());
            if (area > bestArea) {
                bestArea = area;
                best = candidate;
            }
        }
        return best;
    }

    public static EmotionResult evaluateFaceFeatures(@NonNull Bitmap fullBitmap, @NonNull Face face) {
        return evaluateFaceFeatures(fullBitmap, face, true);
    }

    /**
     * Dual-Engine Evaluation: Eye-Aligned Deep Neural Network + FACS Landmark Geometry.
     */
    public static EmotionResult evaluateFaceFeatures(@NonNull Bitmap fullBitmap, @NonNull Face face, boolean useTta) {
        Bitmap faceCrop = extractAlignedFaceCrop(fullBitmap, face);

        TFLiteEmotionClassifier.ClassificationResult nnResult = null;
        if (faceCrop != null) {
            try {
                Context ctx = MyEmotionsApp.getInstance();
                if (ctx != null) {
                    TFLiteEmotionClassifier classifier = TFLiteEmotionClassifier.getInstance(ctx);
                    if (classifier.isReady()) {
                        nnResult = classifier.classifyFace(faceCrop, useTta);
                    }
                }
            } finally {
                if (faceCrop != fullBitmap) {
                    faceCrop.recycle();
                }
            }
        }

        LandmarkSignals signals = extractLandmarkSignals(face);
        return fuseSignals(nnResult, signals);
    }

    /**
     * Crops a tight square around the face center (~8% padding per side) and deskews head roll
     * using eye landmark orientation so the 48x48 CNN receives a clean upright facial crop.
     */
    @Nullable
    private static Bitmap extractAlignedFaceCrop(@NonNull Bitmap fullBitmap, @NonNull Face face) {
        try {
            Rect bbox = face.getBoundingBox();
            if (bbox.width() < 20 || bbox.height() < 20) return null;

            float centerX = bbox.exactCenterX();
            float centerY = bbox.exactCenterY();
            float side = Math.max(bbox.width(), bbox.height()) * 1.16f;

            float rollDeg = computeFaceRollDegrees(face);
            boolean needsRotation = Math.abs(rollDeg) >= 3f && Math.abs(rollDeg) <= 45f;

            int sideInt = Math.max(24, Math.round(side));
            int half = sideInt / 2;
            int left = Math.round(centerX) - half;
            int top = Math.round(centerY) - half;

            if (!needsRotation
                    && left >= 0
                    && top >= 0
                    && left + sideInt <= fullBitmap.getWidth()
                    && top + sideInt <= fullBitmap.getHeight()) {
                return Bitmap.createBitmap(fullBitmap, left, top, sideInt, sideInt);
            }

            int targetSide = Math.min(Math.max(48, sideInt), 256);
            float scale = targetSide / side;

            Bitmap aligned = Bitmap.createBitmap(targetSide, targetSide, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(aligned);
            canvas.drawColor(Color.rgb(128, 128, 128));

            Matrix matrix = new Matrix();
            matrix.postTranslate(-centerX, -centerY);
            if (needsRotation) {
                matrix.postRotate(-rollDeg);
            }
            matrix.postScale(scale, scale);
            matrix.postTranslate(targetSide / 2f, targetSide / 2f);

            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(fullBitmap, matrix, paint);
            return aligned;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static float computeFaceRollDegrees(@NonNull Face face) {
        FaceLandmark leftEye = face.getLandmark(FaceLandmark.LEFT_EYE);
        FaceLandmark rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE);
        if (leftEye != null && rightEye != null) {
            PointF a = leftEye.getPosition();
            PointF b = rightEye.getPosition();
            PointF eyeL = (a.x <= b.x) ? a : b;
            PointF eyeR = (a.x <= b.x) ? b : a;
            float dx = eyeR.x - eyeL.x;
            float dy = eyeR.y - eyeL.y;
            if (dx > 5f) {
                return (float) Math.toDegrees(Math.atan2(dy, dx));
            }
        }
        return -face.getHeadEulerAngleZ();
    }

    @NonNull
    public static LandmarkSignals extractLandmarkSignals(@NonNull Face face) {
        float smile = face.getSmilingProbability() != null ? face.getSmilingProbability() : 0.15f;
        float leftEyeOpen = face.getLeftEyeOpenProbability() != null ? face.getLeftEyeOpenProbability() : 0.72f;
        float rightEyeOpen = face.getRightEyeOpenProbability() != null ? face.getRightEyeOpenProbability() : 0.72f;
        float avgEyeOpen = (leftEyeOpen + rightEyeOpen) / 2.0f;
        float pitch = face.getHeadEulerAngleX();

        // Eye axis unit vector (ux, uy) and downward face vector (vx, vy)
        PointF eyeCenterL = resolveEyeCenter(face, FaceLandmark.LEFT_EYE, FaceContour.LEFT_EYE);
        PointF eyeCenterR = resolveEyeCenter(face, FaceLandmark.RIGHT_EYE, FaceContour.RIGHT_EYE);

        float ux = 1f;
        float uy = 0f;
        float iod = Math.max(20f, face.getBoundingBox().width() * 0.42f);

        if (eyeCenterL != null && eyeCenterR != null) {
            PointF leftPt = (eyeCenterL.x <= eyeCenterR.x) ? eyeCenterL : eyeCenterR;
            PointF rightPt = (eyeCenterL.x <= eyeCenterR.x) ? eyeCenterR : eyeCenterL;
            float dx = rightPt.x - leftPt.x;
            float dy = rightPt.y - leftPt.y;
            float dist = (float) Math.hypot(dx, dy);
            if (dist > 10f) {
                iod = dist;
                ux = dx / dist;
                uy = dy / dist;
            }
            eyeCenterL = leftPt;
            eyeCenterR = rightPt;
        }
        float vx = -uy;
        float vy = ux;

        // Mouth geometry using inner lip contours (robust against surprised open jaw)
        float innerMouthOpenRatio = 0f;
        float lipCornerLift = 0f;
        boolean hasMouthContours = false;

        FaceContour upperLipTop = face.getContour(FaceContour.UPPER_LIP_TOP);
        FaceContour upperLipBottom = face.getContour(FaceContour.UPPER_LIP_BOTTOM);
        FaceContour lowerLipTop = face.getContour(FaceContour.LOWER_LIP_TOP);
        FaceContour lowerLipBottom = face.getContour(FaceContour.LOWER_LIP_BOTTOM);

        if (upperLipTop != null && upperLipTop.getPoints().size() >= 5) {
            List<PointF> topPts = upperLipTop.getPoints();
            PointF leftCorner = extremePointAlongAxis(topPts, ux, uy, true);
            PointF rightCorner = extremePointAlongAxis(topPts, ux, uy, false);
            float mouthWidth = (float) Math.hypot(rightCorner.x - leftCorner.x, rightCorner.y - leftCorner.y);

            if (mouthWidth > 12f) {
                hasMouthContours = true;
                PointF cornerMid = new PointF((leftCorner.x + rightCorner.x) / 2f, (leftCorner.y + rightCorner.y) / 2f);

                if (upperLipBottom != null && !upperLipBottom.getPoints().isEmpty()
                        && lowerLipTop != null && !lowerLipTop.getPoints().isEmpty()) {
                    List<PointF> ubPts = upperLipBottom.getPoints();
                    List<PointF> ltPts = lowerLipTop.getPoints();
                    PointF upperInnerCenter = ubPts.get(ubPts.size() / 2);
                    PointF lowerInnerCenter = ltPts.get(ltPts.size() / 2);

                    float innerGap = (float) Math.hypot(
                            lowerInnerCenter.x - upperInnerCenter.x,
                            lowerInnerCenter.y - upperInnerCenter.y
                    );
                    innerMouthOpenRatio = innerGap / mouthWidth;

                    float dyDown = (upperInnerCenter.x - cornerMid.x) * vx + (upperInnerCenter.y - cornerMid.y) * vy;
                    lipCornerLift = dyDown / mouthWidth;
                } else if (lowerLipBottom != null && !lowerLipBottom.getPoints().isEmpty()) {
                    List<PointF> lbPts = lowerLipBottom.getPoints();
                    PointF cupidsBow = topPts.get(topPts.size() / 2);
                    PointF lowerOuterCenter = lbPts.get(lbPts.size() / 2);
                    float outerGap = (float) Math.hypot(
                            lowerOuterCenter.x - cupidsBow.x,
                            lowerOuterCenter.y - cupidsBow.y
                    );
                    innerMouthOpenRatio = Math.max(0f, (outerGap / mouthWidth) - 0.28f);

                    float dyDown = (cupidsBow.x - cornerMid.x) * vx + (cupidsBow.y - cornerMid.y) * vy;
                    lipCornerLift = dyDown / mouthWidth;
                }
            }
        }

        // Eyebrow raise & furrow ratios normalized by inter-ocular distance (IOD)
        float browRaiseRatio = 0.29f;
        float browFurrowRatio = 0.48f;
        boolean hasBrowContours = false;

        FaceContour leftBrow = face.getContour(FaceContour.LEFT_EYEBROW_TOP);
        FaceContour rightBrow = face.getContour(FaceContour.RIGHT_EYEBROW_TOP);
        if (leftBrow != null && rightBrow != null
                && !leftBrow.getPoints().isEmpty() && !rightBrow.getPoints().isEmpty()) {
            List<PointF> lbPts = leftBrow.getPoints();
            List<PointF> rbPts = rightBrow.getPoints();
            PointF lbMid = averagePoints(lbPts);
            PointF rbMid = averagePoints(rbPts);

            // Inner brow tips are the points closest to the midline along eye axis
            PointF innerL = extremePointAlongAxis(lbPts, ux, uy, false);
            PointF innerR = extremePointAlongAxis(rbPts, ux, uy, true);
            if (lbMid.x > rbMid.x) {
                // Swap if left/right brow contour ordering corresponds to mirrored space
                PointF tmp = lbMid; lbMid = rbMid; rbMid = tmp;
                innerL = extremePointAlongAxis(rbPts, ux, uy, false);
                innerR = extremePointAlongAxis(lbPts, ux, uy, true);
            }

            float innerGap = (float) Math.hypot(innerR.x - innerL.x, innerR.y - innerL.y);
            browFurrowRatio = innerGap / iod;

            if (eyeCenterL != null && eyeCenterR != null) {
                float raiseL = -((lbMid.x - eyeCenterL.x) * vx + (lbMid.y - eyeCenterL.y) * vy);
                float raiseR = -((rbMid.x - eyeCenterR.x) * vx + (rbMid.y - eyeCenterR.y) * vy);
                browRaiseRatio = ((raiseL + raiseR) / 2f) / iod;
                hasBrowContours = true;
            }
        }

        // Eye aspect ratio from eye contours
        float earSum = 0f;
        int earCount = 0;
        for (int contourId : new int[]{FaceContour.LEFT_EYE, FaceContour.RIGHT_EYE}) {
            FaceContour eyeContour = face.getContour(contourId);
            if (eyeContour != null && eyeContour.getPoints().size() >= 6) {
                float ear = computeAxisAspectRatio(eyeContour.getPoints(), ux, uy, vx, vy);
                if (ear > 0f) {
                    earSum += ear;
                    earCount++;
                }
            }
        }
        boolean hasEyeContours = earCount > 0;
        float eyeAspectRatio = hasEyeContours ? (earSum / earCount) : 0.25f;

        return new LandmarkSignals(
                smile,
                avgEyeOpen,
                innerMouthOpenRatio,
                lipCornerLift,
                browRaiseRatio,
                browFurrowRatio,
                eyeAspectRatio,
                pitch,
                hasMouthContours,
                hasBrowContours,
                hasEyeContours
        );
    }

    /**
     * Pure-Java multimodal fusion between CNN probabilities and FACS geometric landmark signals.
     */
    @NonNull
    public static EmotionResult fuseSignals(
            @Nullable TFLiteEmotionClassifier.ClassificationResult nnResult,
            @NonNull LandmarkSignals signals
    ) {
        Map<String, Float> landmarkScores = computeLandmarkScores(signals);

        String lmMaxEmotion = "Neutral";
        float lmMaxScore = 0f;
        for (Map.Entry<String, Float> entry : landmarkScores.entrySet()) {
            if (entry.getValue() > lmMaxScore) {
                lmMaxScore = entry.getValue();
                lmMaxEmotion = entry.getKey();
            }
        }

        boolean strongLandmarkNonNeutral = !"Neutral".equals(lmMaxEmotion) && lmMaxScore >= 0.75f;

        Map<String, Float> fusedScores = new HashMap<>();

        if (nnResult != null && nnResult.probabilities != null && !nnResult.probabilities.isEmpty()) {
            String nnTopEmotion = "Neutral";
            float nnTopScore = -1f;
            for (String key : EMOTION_KEYS) {
                float s = nnResult.probabilities.containsKey(key) ? nnResult.probabilities.get(key) : 0f;
                if (s > nnTopScore) {
                    nnTopScore = s;
                    nnTopEmotion = key;
                }
            }

            float nnWeight = (strongLandmarkNonNeutral && "Neutral".equals(nnTopEmotion)) ? 0.48f : 0.68f;
            float lmWeight = 1.0f - nnWeight;

            for (String key : EMOTION_KEYS) {
                float nnScore = nnResult.probabilities.containsKey(key) ? nnResult.probabilities.get(key) : 0f;
                float lmScore = landmarkScores.containsKey(key) ? landmarkScores.get(key) : 0f;

                float combined = (nnScore * nnWeight) + (lmScore * lmWeight);
                if (strongLandmarkNonNeutral && "Neutral".equals(key) && "Neutral".equals(nnTopEmotion)) {
                    combined *= 0.70f;
                }
                if (lmScore >= 0.70f && nnScore >= 0.30f) {
                    combined = Math.min(0.98f, combined * 1.08f);
                }
                fusedScores.put(key, combined);
            }

            // Normalize base 5 emotion probabilities so confidence stays honest and well-calibrated
            float baseSum = 0f;
            for (String key : BASE_KEYS) {
                baseSum += fusedScores.get(key);
            }
            if (baseSum > 0f) {
                for (String key : BASE_KEYS) {
                    fusedScores.put(key, fusedScores.get(key) / baseSum);
                }
                float normExcited = Math.min(0.98f, fusedScores.get("Excited") / Math.max(baseSum, 0.75f));
                fusedScores.put("Excited", normExcited);
            }
        } else {
            fusedScores.putAll(landmarkScores);
        }

        String bestEmotion = "Neutral";
        float highestScore = -1f;
        for (String key : EMOTION_KEYS) {
            float score = fusedScores.containsKey(key) ? fusedScores.get(key) : 0f;
            if (score > highestScore) {
                highestScore = score;
                bestEmotion = key;
            }
        }

        int confidencePercent = Math.min(98, Math.max(48, Math.round(highestScore * 100f)));
        String emoji = getEmojiForEmotion(bestEmotion);
        String rationale = buildVisualRationale(bestEmotion, fusedScores, signals);

        return new EmotionResult(bestEmotion, emoji, confidencePercent, rationale, fusedScores);
    }

    @NonNull
    private static Map<String, Float> computeLandmarkScores(@NonNull LandmarkSignals signals) {
        boolean hasOpenMouth = signals.hasMouthContours && signals.innerMouthOpenRatio >= 0.18f;
        boolean hasWideOpenMouth = signals.hasMouthContours && signals.innerMouthOpenRatio >= 0.26f;
        boolean hasRaisedBrows = signals.hasBrowContours && signals.browRaiseRatio >= 0.36f;
        boolean hasLoweredBrows = signals.hasBrowContours && signals.browRaiseRatio <= 0.24f;
        boolean hasFurrowedBrows = signals.hasBrowContours && signals.browFurrowRatio <= 0.35f;
        boolean hasMouthDroop = signals.hasMouthContours && !hasOpenMouth && signals.lipCornerLift <= -0.025f;
        boolean hasSmileLift = signals.hasMouthContours && !hasOpenMouth && signals.lipCornerLift >= 0.035f;
        boolean hasWideEyes = signals.avgEyeOpen >= 0.85f && (!signals.hasEyeContours || signals.eyeAspectRatio >= 0.27f);

        float lmHappy = 0f;
        float lmExcited = 0f;
        float lmSurprised = 0f;
        float lmAngry = 0f;
        float lmSad = 0f;
        float lmNeutral = 0f;

        if (signals.smileProb >= 0.45f || hasSmileLift) {
            float smileStrength = Math.max(signals.smileProb, hasSmileLift ? 0.65f : signals.smileProb);
            if (smileStrength >= 0.80f && (hasOpenMouth || hasRaisedBrows)) {
                lmExcited = 0.90f;
                lmHappy = 0.65f;
            } else if (smileStrength >= 0.65f) {
                lmHappy = Math.min(0.92f, 0.72f + (smileStrength - 0.65f) * 0.57f);
                lmExcited = (hasOpenMouth || hasRaisedBrows) ? 0.72f : 0.42f;
            } else {
                lmHappy = 0.60f + (smileStrength - 0.45f) * 0.60f;
                lmExcited = 0.28f;
            }
        }

        if (signals.smileProb < 0.45f && (hasOpenMouth || (hasRaisedBrows && hasWideEyes))) {
            if (hasWideOpenMouth || (hasOpenMouth && hasRaisedBrows)) {
                lmSurprised = 0.90f;
            } else if (hasOpenMouth) {
                lmSurprised = 0.78f;
            } else {
                lmSurprised = 0.68f;
            }
        }

        if (signals.smileProb < 0.22f && !hasWideOpenMouth && (hasFurrowedBrows || hasLoweredBrows)) {
            lmAngry = (hasFurrowedBrows && hasLoweredBrows) ? 0.85f : 0.72f;
        }

        if (signals.smileProb < 0.20f && !hasOpenMouth
                && (hasMouthDroop || (signals.smileProb < 0.08f && signals.headPitch < -6.0f))) {
            lmSad = hasMouthDroop ? 0.84f : 0.68f;
        }

        if (signals.smileProb < 0.30f && !hasOpenMouth && !hasMouthDroop
                && !hasFurrowedBrows && !hasRaisedBrows && !hasLoweredBrows
                && lmSurprised == 0f && lmSad == 0f && lmAngry == 0f) {
            lmNeutral = 0.82f;
        } else if (lmSurprised < 0.50f && lmSad < 0.50f && lmAngry < 0.50f
                && lmHappy < 0.50f && lmExcited < 0.50f) {
            lmNeutral = 0.45f;
        }

        Map<String, Float> scores = new HashMap<>();
        scores.put("Happy", lmHappy);
        scores.put("Excited", lmExcited);
        scores.put("Surprised", lmSurprised);
        scores.put("Angry", lmAngry);
        scores.put("Sad", lmSad);
        scores.put("Neutral", lmNeutral);
        return scores;
    }

    @NonNull
    private static String buildVisualRationale(
            @NonNull String bestEmotion,
            @NonNull Map<String, Float> scores,
            @Nullable LandmarkSignals signals
    ) {
        String core;
        switch (bestEmotion) {
            case "Surprised":
                if (signals != null && signals.hasBrowContours && signals.browRaiseRatio >= 0.36f) {
                    core = "Widened eyes, raised brows, and startled open-mouth expression";
                } else {
                    core = "Startled open-mouth posture and heightened facial expression";
                }
                break;
            case "Excited":
                core = "High-energy smile and expressive joyful facial cues";
                break;
            case "Happy":
                core = "Upward smile contours and warm, joyful expression";
                break;
            case "Sad":
                core = "Downward lip corners and subdued facial expression";
                break;
            case "Angry":
                core = "Furrowed brow tension and firm mouth posture";
                break;
            case "Neutral":
            default:
                core = "Balanced, relaxed, and composed facial posture";
                break;
        }

        float topScore = scores.containsKey(bestEmotion) ? scores.get(bestEmotion) : 0f;
        String runnerUp = null;
        float runnerUpScore = 0f;
        for (String key : EMOTION_KEYS) {
            if (key.equals(bestEmotion)) continue;
            float s = scores.containsKey(key) ? scores.get(key) : 0f;
            if (s > runnerUpScore) {
                runnerUpScore = s;
                runnerUp = key;
            }
        }

        if (runnerUp != null && topScore > 0f && runnerUpScore >= topScore * 0.65f) {
            return core + " • Close runner-up: " + runnerUp;
        }
        return core;
    }

    @Nullable
    private static PointF resolveEyeCenter(@NonNull Face face, int landmarkType, int contourType) {
        FaceLandmark lm = face.getLandmark(landmarkType);
        if (lm != null) {
            return lm.getPosition();
        }
        FaceContour contour = face.getContour(contourType);
        if (contour != null && !contour.getPoints().isEmpty()) {
            return averagePoints(contour.getPoints());
        }
        return null;
    }

    @NonNull
    private static PointF extremePointAlongAxis(@NonNull List<PointF> pts, float ux, float uy, boolean findMin) {
        PointF best = pts.get(0);
        float bestProj = best.x * ux + best.y * uy;
        for (int i = 1; i < pts.size(); i++) {
            PointF p = pts.get(i);
            float proj = p.x * ux + p.y * uy;
            if (findMin ? (proj < bestProj) : (proj > bestProj)) {
                bestProj = proj;
                best = p;
            }
        }
        return best;
    }

    @NonNull
    private static PointF averagePoints(@NonNull List<PointF> pts) {
        float sx = 0f;
        float sy = 0f;
        for (PointF p : pts) {
            sx += p.x;
            sy += p.y;
        }
        return new PointF(sx / pts.size(), sy / pts.size());
    }

    private static float computeAxisAspectRatio(
            @NonNull List<PointF> pts,
            float ux,
            float uy,
            float vx,
            float vy
    ) {
        float minU = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;
        for (PointF p : pts) {
            float pu = p.x * ux + p.y * uy;
            float pv = p.x * vx + p.y * vy;
            if (pu < minU) minU = pu;
            if (pu > maxU) maxU = pu;
            if (pv < minV) minV = pv;
            if (pv > maxV) maxV = pv;
        }
        float width = maxU - minU;
        float height = maxV - minV;
        return (width > 2f) ? (height / width) : 0f;
    }

    private static EmotionResult evaluateCenterCropFallback(Bitmap bitmap, boolean useTta) {
        try {
            Context ctx = MyEmotionsApp.getInstance();
            if (ctx == null) return null;

            int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
            int x = (bitmap.getWidth() - size) / 2;
            int y = (bitmap.getHeight() - size) / 2;
            Bitmap center = Bitmap.createBitmap(bitmap, x, y, size, size);

            TFLiteEmotionClassifier.ClassificationResult nnResult =
                    TFLiteEmotionClassifier.getInstance(ctx).classifyFace(center, useTta);

            if (center != bitmap) {
                center.recycle();
            }

            if (nnResult != null && nnResult.confidence >= 60) {
                return new EmotionResult(
                        nnResult.emotion,
                        getEmojiForEmotion(nnResult.emotion),
                        nnResult.confidence,
                        nnResult.rationale,
                        nnResult.probabilities
                );
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static String getEmojiForEmotion(String emotion) {
        if (emotion == null) return "😐";
        switch (emotion.toLowerCase()) {
            case "happy":
                return "😊";
            case "excited":
                return "🤩";
            case "sad":
                return "😢";
            case "surprised":
                return "😲";
            case "angry":
                return "😠";
            case "neutral":
            default:
                return "😐";
        }
    }

    /**
     * Intelligently combines facial analysis with written note sentiment.
     */
    @NonNull
    public static EmotionResult combineWithNote(@Nullable EmotionResult faceResult, @Nullable SentimentAnalyzer.SentimentResult noteResult) {
        if (faceResult == null && noteResult == null) {
            return new EmotionResult("Neutral", "😐", 72, "Select your emotion below", new HashMap<>());
        }

        if (faceResult == null) {
            return new EmotionResult(
                    noteResult.emotion,
                    noteResult.emoji,
                    noteResult.confidence,
                    "Detected from reflection note keyword: \"" + noteResult.matchingKeyword + "\"",
                    new HashMap<>()
            );
        }

        if (noteResult == null || !noteResult.hasStrongSignal()) {
            return faceResult;
        }

        if (faceResult.emotion.equalsIgnoreCase(noteResult.emotion)) {
            int combinedConf = Math.min(99, faceResult.confidence + 6);
            return new EmotionResult(
                    faceResult.emotion,
                    faceResult.emoji,
                    combinedConf,
                    "Harmonized Match: Face & note (\"" + noteResult.matchingKeyword + "\") both indicate " + faceResult.getFormatted(),
                    faceResult.scores
            );
        }

        if ("Neutral".equalsIgnoreCase(faceResult.emotion) && noteResult.hasStrongSignal()) {
            return new EmotionResult(
                    noteResult.emotion,
                    noteResult.emoji,
                    noteResult.confidence,
                    "Context Aware: Reflection note highlights \"" + noteResult.matchingKeyword + "\"",
                    faceResult.scores
            );
        }

        return new EmotionResult(
                faceResult.emotion,
                faceResult.emoji,
                faceResult.confidence,
                faceResult.rationale + " (Note mentions: \"" + noteResult.matchingKeyword + "\")",
                faceResult.scores
        );
    }
}
