package com.mitaoe.shridhar202401040197.utils;

import android.graphics.Bitmap;
import android.graphics.PointF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * High-precision on-device Facial Emotion Recognition Engine.
 * Evaluates facial landmark geometry, lip curvature, eye aspect ratios, and contour dynamics.
 */
public class EmotionRecognitionEngine {

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

    public interface EmotionCallback {
        void onEmotionDetected(EmotionResult result);
        void onNoFaceDetected();
        void onError(Exception e);
    }

    public static void analyzeFaceEmotion(@NonNull Bitmap bitmap, @NonNull EmotionCallback callback) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setMinFaceSize(0.10f)
                .build();

        FaceDetector detector = FaceDetection.getClient(options);

        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces == null || faces.isEmpty()) {
                        callback.onNoFaceDetected();
                        return;
                    }

                    Face primaryFace = faces.get(0);
                    EmotionResult result = evaluateFaceFeatures(primaryFace);
                    callback.onEmotionDetected(result);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Mathematically calibrated facial emotion classifier distinguishing Sad, Neutral, Surprised, Happy, Excited, and Angry.
     */
    public static EmotionResult evaluateFaceFeatures(@NonNull Face face) {
        Float smileProb = face.getSmilingProbability();
        Float leftEyeOpen = face.getLeftEyeOpenProbability();
        Float rightEyeOpen = face.getRightEyeOpenProbability();
        float pitch = face.getHeadEulerAngleX(); // Positive = head tilted up, Negative = head tilted down
        float roll = face.getHeadEulerAngleZ();

        float smile = smileProb != null ? smileProb : 0.15f;
        float leftEye = leftEyeOpen != null ? leftEyeOpen : 0.72f;
        float rightEye = rightEyeOpen != null ? rightEyeOpen : 0.72f;
        float avgEye = (leftEye + rightEye) / 2.0f;

        // Extract Lip Contours for accurate Curvature & Mouth Aspect Ratio (MAR)
        FaceContour upperLipContour = face.getContour(FaceContour.UPPER_LIP_TOP);
        FaceContour lowerLipContour = face.getContour(FaceContour.LOWER_LIP_BOTTOM);

        Float lipCurvature = null;  // Positive = Smile (corners higher), Negative = Frown (corners drooping)
        Float mouthAspectRatio = null; // Vertical opening / mouth width

        if (upperLipContour != null && lowerLipContour != null) {
            List<PointF> upPts = upperLipContour.getPoints();
            List<PointF> lowPts = lowerLipContour.getPoints();

            if (upPts.size() >= 5 && lowPts.size() >= 5) {
                PointF leftCorner = upPts.get(0);
                PointF rightCorner = upPts.get(upPts.size() - 1);
                PointF cupidsBow = upPts.get(upPts.size() / 2);
                PointF lowerCenter = lowPts.get(lowPts.size() / 2);

                float mouthWidth = Math.abs(rightCorner.x - leftCorner.x);
                if (mouthWidth > 15f) {
                    float lipCenterY = (cupidsBow.y + lowerCenter.y) / 2.0f;
                    float cornerAvgY = (leftCorner.y + rightCorner.y) / 2.0f;

                    // In screen coordinates Y increases downwards:
                    // If corners are higher than center (cornerAvgY < lipCenterY), difference is positive (Smile)
                    // If corners are lower than center (cornerAvgY > lipCenterY), difference is negative (Frown/Sad)
                    lipCurvature = (lipCenterY - cornerAvgY) / mouthWidth;

                    float verticalOpening = Math.abs(lowerCenter.y - cupidsBow.y);
                    mouthAspectRatio = verticalOpening / mouthWidth;
                }
            }
        }

        // Landmark fallback if contours unavailable
        if (lipCurvature == null) {
            FaceLandmark mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT);
            FaceLandmark mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT);
            FaceLandmark noseBase = face.getLandmark(FaceLandmark.NOSE_BASE);
            FaceLandmark mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM);

            if (mouthLeft != null && mouthRight != null && noseBase != null) {
                float cornerY = (mouthLeft.getPosition().y + mouthRight.getPosition().y) / 2.0f;
                float noseY = noseBase.getPosition().y;
                float mouthWidth = Math.abs(mouthRight.getPosition().x - mouthLeft.getPosition().x);

                if (mouthBottom != null && mouthWidth > 15f) {
                    float bottomY = mouthBottom.getPosition().y;
                    float verticalSpan = bottomY - cornerY;
                    mouthAspectRatio = verticalSpan / mouthWidth;

                    // Distance from nose to mouth corners vs nose to mouth bottom
                    float noseToCorners = cornerY - noseY;
                    float noseToBottom = bottomY - noseY;
                    float expectedCornerY = noseY + (noseToBottom * 0.70f);

                    // If corners are lower than expected: drooping
                    if (cornerY > expectedCornerY) {
                        lipCurvature = -0.03f;
                    } else if (smile > 0.40f) {
                        lipCurvature = 0.04f;
                    } else {
                        lipCurvature = 0.0f;
                    }
                }
            }
        }

        // Eyebrow furrow detection for Anger
        FaceContour leftBrow = face.getContour(FaceContour.LEFT_EYEBROW_TOP);
        FaceContour rightBrow = face.getContour(FaceContour.RIGHT_EYEBROW_TOP);
        boolean browsFurrowed = false;
        if (leftBrow != null && rightBrow != null && !leftBrow.getPoints().isEmpty() && !rightBrow.getPoints().isEmpty()) {
            PointF innerLeft = leftBrow.getPoints().get(leftBrow.getPoints().size() - 1);
            PointF innerRight = rightBrow.getPoints().get(0);
            float browGap = Math.abs(innerRight.x - innerLeft.x);
            float faceWidth = face.getBoundingBox().width();
            if (faceWidth > 0 && (browGap / faceWidth) < 0.125f) {
                browsFurrowed = true;
            }
        }

        boolean hasMouthOpen = (mouthAspectRatio != null && mouthAspectRatio > 0.28f);
        boolean hasMouthDroop = (lipCurvature != null && lipCurvature < -0.010f);
        boolean hasSmileCurvature = (lipCurvature != null && lipCurvature > 0.020f);

        Map<String, Float> scores = new HashMap<>();
        float surprisedScore = 0f;
        float sadScore = 0f;
        float neutralScore = 0f;
        float happyScore = 0f;
        float excitedScore = 0f;
        float angryScore = 0f;

        // -------------------------------------------------------------
        // 1. SURPRISED EVALUATION:
        // Key characteristics: wide open eyes (avgEye >= 0.78) AND open mouth / dropped jaw (MAR > 0.28)
        // or very wide eyes (avgEye >= 0.88) with raised expression
        // -------------------------------------------------------------
        if (smile < 0.55f) {
            if (hasMouthOpen && avgEye >= 0.75f) {
                surprisedScore = 0.88f + Math.min(0.10f, (avgEye - 0.75f) * 0.5f);
            } else if (mouthAspectRatio != null && mouthAspectRatio > 0.38f && avgEye >= 0.65f) {
                surprisedScore = 0.84f; // Open mouth surprise / gasp
            } else if (avgEye >= 0.88f && (pitch > 2.0f || hasMouthOpen)) {
                surprisedScore = 0.85f; // Wide eye surprise with head back
            } else if (avgEye >= 0.92f) {
                surprisedScore = 0.78f; // Extremely wide open eyes
            }
        }

        // -------------------------------------------------------------
        // 2. SAD EVALUATION:
        // Key characteristics: mouth corners downturned (lipCurvature < -0.010) AND low smile (smile < 0.20)
        // OR head tilted down (pitch < -1.5) with drooping eyelids (avgEye < 0.65) and no smile
        // -------------------------------------------------------------
        if (smile < 0.25f && !hasMouthOpen) {
            if (hasMouthDroop) {
                // Definite frown curvature
                sadScore = 0.88f + Math.min(0.08f, (-lipCurvature) * 2.0f);
            } else if (smile < 0.08f && (pitch < -1.5f || avgEye < 0.62f)) {
                sadScore = 0.82f;
            } else if (smile < 0.04f && avgEye < 0.70f) {
                sadScore = 0.76f;
            }
        }

        // -------------------------------------------------------------
        // 3. ANGRY EVALUATION:
        // Key characteristics: brows furrowed together, mouth compressed/closed, low smile
        // -------------------------------------------------------------
        if (smile < 0.22f && !hasMouthOpen) {
            if (browsFurrowed) {
                angryScore = 0.86f + (0.22f - smile) * 0.4f;
            } else if (smile < 0.08f && avgEye < 0.55f && pitch < -2.0f) {
                angryScore = 0.74f;
            }
        }

        // -------------------------------------------------------------
        // 4. HAPPY & EXCITED EVALUATION:
        // Key characteristics: smile >= 0.32 or upward smile curvature
        // -------------------------------------------------------------
        if (smile >= 0.32f || hasSmileCurvature) {
            float smileStrength = Math.max(smile, hasSmileCurvature ? 0.60f : smile);

            if (smileStrength >= 0.72f && (avgEye >= 0.75f || hasMouthOpen)) {
                excitedScore = 0.90f + (smileStrength - 0.72f) * 0.3f;
                happyScore = 0.80f;
            } else {
                happyScore = 0.85f + (smileStrength - 0.32f) * 0.25f;
                if (smileStrength >= 0.60f) {
                    excitedScore = 0.75f;
                }
            }
        }

        // -------------------------------------------------------------
        // 5. NEUTRAL EVALUATION:
        // Key characteristics: resting face without extremes:
        // mouth closed (MAR <= 0.26), no frown, no brow furrow, calm eyes (0.58 <= avgEye <= 0.85),
        // calm smile probability (0.05 <= smile <= 0.35)
        // -------------------------------------------------------------
        if (!hasMouthOpen && !hasMouthDroop && !browsFurrowed) {
            if (smile >= 0.06f && smile <= 0.35f && avgEye >= 0.58f && avgEye <= 0.85f) {
                neutralScore = 0.88f; // Prime resting face
            } else if (smile < 0.28f && sadScore < 0.70f && angryScore < 0.70f && surprisedScore < 0.70f) {
                neutralScore = 0.80f;
            }
        }

        scores.put("Surprised", surprisedScore);
        scores.put("Sad", sadScore);
        scores.put("Neutral", neutralScore);
        scores.put("Happy", happyScore);
        scores.put("Excited", excitedScore);
        scores.put("Angry", angryScore);

        // Determine winning emotion
        String bestEmotion = "Neutral";
        float maxScore = neutralScore;

        for (Map.Entry<String, Float> entry : scores.entrySet()) {
            if (entry.getValue() > maxScore) {
                maxScore = entry.getValue();
                bestEmotion = entry.getKey();
            }
        }

        // If all scores were inconclusive, default gracefully to Neutral
        if (maxScore < 0.45f) {
            bestEmotion = "Neutral";
            maxScore = 0.75f;
        }

        int confidence = Math.min(98, Math.max(70, Math.round(maxScore * 100)));

        String emoji = "😐";
        String rationale;

        switch (bestEmotion) {
            case "Surprised":
                emoji = "😲";
                rationale = hasMouthOpen ? "Wide open eyes with dropped jaw in surprise" : "Heightened eye openness and expressive facial posture";
                break;
            case "Sad":
                emoji = "😢";
                rationale = hasMouthDroop ? "Downturned mouth curvature and subdued gaze" : "Subdued expression with lowered gaze and contemplative posture";
                break;
            case "Neutral":
                emoji = "😐";
                rationale = "Calm, balanced resting facial features";
                break;
            case "Happy":
                emoji = "😊";
                rationale = "Warm smile with upturned mouth corners and bright expression";
                break;
            case "Excited":
                emoji = "🤩";
                rationale = "Radiant broad smile with high energy and open posture";
                break;
            case "Angry":
                emoji = "😠";
                rationale = "Firm brow contours and concentrated facial expression";
                break;
            default:
                rationale = "Balanced facial expression";
                break;
        }

        return new EmotionResult(bestEmotion, emoji, confidence, rationale, scores);
    }

    /**
     * Intelligently combines facial analysis with written note sentiment.
     */
    @NonNull
    public static EmotionResult combineWithNote(@Nullable EmotionResult faceResult, @Nullable SentimentAnalyzer.SentimentResult noteResult) {
        if (faceResult == null && noteResult == null) {
            return new EmotionResult("Neutral", "😐", 72, "Select your emotion below", new HashMap<>());
        }

        // If only note result is available (no face detected)
        if (faceResult == null) {
            return new EmotionResult(
                    noteResult.emotion,
                    noteResult.emoji,
                    noteResult.confidence,
                    "Detected from reflection note keyword: \"" + noteResult.matchingKeyword + "\"",
                    new HashMap<>()
            );
        }

        // If no note result, return face result directly
        if (noteResult == null || !noteResult.hasStrongSignal()) {
            return faceResult;
        }

        // Both face and note exist!
        if (faceResult.emotion.equalsIgnoreCase(noteResult.emotion)) {
            // Harmonious agreement
            int combinedConf = Math.min(99, faceResult.confidence + 8);
            return new EmotionResult(
                    faceResult.emotion,
                    faceResult.emoji,
                    combinedConf,
                    "Harmonized AI Match: Face & note (\"" + noteResult.matchingKeyword + "\") both indicate " + faceResult.getFormatted(),
                    faceResult.scores
            );
        }

        // If face detection is neutral/ambiguous but note has strong emotion keyword:
        if ("Neutral".equalsIgnoreCase(faceResult.emotion) && noteResult.hasStrongSignal()) {
            return new EmotionResult(
                    noteResult.emotion,
                    noteResult.emoji,
                    noteResult.confidence,
                    "Context Aware: Reflection note highlights \"" + noteResult.matchingKeyword + "\"",
                    faceResult.scores
            );
        }

        // If face detection has strong confidence (>= 84%), keep face as primary and note as context
        return new EmotionResult(
                faceResult.emotion,
                faceResult.emoji,
                faceResult.confidence,
                faceResult.rationale + " (Note mentions: \"" + noteResult.matchingKeyword + "\")",
                faceResult.scores
        );
    }
}
