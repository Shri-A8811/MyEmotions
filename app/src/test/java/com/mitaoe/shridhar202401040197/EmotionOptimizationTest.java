package com.mitaoe.shridhar202401040197;

import com.mitaoe.shridhar202401040197.ml.TFLiteEmotionClassifier;
import com.mitaoe.shridhar202401040197.utils.EmotionRecognitionEngine;
import com.mitaoe.shridhar202401040197.utils.SentimentAnalyzer;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class EmotionOptimizationTest {

    private static TFLiteEmotionClassifier.ClassificationResult mockNnResult(
            String topEmotion,
            float neutral,
            float happy,
            float surprised,
            float sad,
            float angry,
            float excited
    ) {
        Map<String, Float> probs = new HashMap<>();
        probs.put("Neutral", neutral);
        probs.put("Happy", happy);
        probs.put("Surprised", surprised);
        probs.put("Sad", sad);
        probs.put("Angry", angry);
        probs.put("Excited", excited);
        return new TFLiteEmotionClassifier.ClassificationResult(
                topEmotion,
                Math.round((probs.get(topEmotion) != null ? probs.get(topEmotion) : 0.5f) * 100f),
                probs,
                "Neural network detected expression."
        );
    }

    @Test
    public void testMapAndCalibrateLogits_reducesNeutralBiasAndMapsFearDisgust() {
        // Slightly higher raw neutral logit (+0.10) should yield Surprised after 0.25 neutral calibration
        float[] logits = new float[]{1.10f, -2.0f, 1.00f, -2.0f, -2.0f, -3.0f, -2.0f, -3.0f};
        Map<String, Float> mapped = TFLiteEmotionClassifier.mapAndCalibrateLogits(logits);

        assertTrue("Calibrated Surprised should exceed Neutral",
                mapped.get("Surprised") > mapped.get("Neutral"));

        float baseSum = mapped.get("Neutral") + mapped.get("Happy")
                + mapped.get("Surprised") + mapped.get("Sad") + mapped.get("Angry");
        assertEquals("Base probabilities should sum to 1.0", 1.0f, baseSum, 0.01f);

        // High fear logit should strongly map into Surprised
        float[] fearLogits = new float[]{-1.0f, -2.0f, 0.2f, -2.0f, -2.0f, -3.0f, 2.5f, -3.0f};
        Map<String, Float> fearMapped = TFLiteEmotionClassifier.mapAndCalibrateLogits(fearLogits);
        assertTrue("Fear should boost Surprised", fearMapped.get("Surprised") > 0.65f);

        // High disgust logit should strongly map into Angry
        float[] disgustLogits = new float[]{-1.0f, -2.0f, -2.0f, -2.0f, 0.2f, 2.5f, -3.0f, -3.0f};
        Map<String, Float> disgustMapped = TFLiteEmotionClassifier.mapAndCalibrateLogits(disgustLogits);
        assertTrue("Disgust should boost Angry", disgustMapped.get("Angry") > 0.65f);
    }

    @Test
    public void testFuseSignals_surprisedOpenMouthBeatsNeutralBiasedNn() {
        TFLiteEmotionClassifier.ClassificationResult nn =
                mockNnResult("Neutral", 0.55f, 0.05f, 0.30f, 0.05f, 0.05f, 0.02f);

        EmotionRecognitionEngine.LandmarkSignals surprisedSignals =
                new EmotionRecognitionEngine.LandmarkSignals(
                        0.05f, // smileProb
                        0.92f, // avgEyeOpen
                        0.32f, // innerMouthOpenRatio (wide open)
                        0.01f, // lipCornerLift (neutral corners)
                        0.41f, // browRaiseRatio (raised brows)
                        0.48f, // browFurrowRatio
                        0.31f, // eyeAspectRatio
                        0f,    // headPitch
                        true, true, true
                );

        EmotionRecognitionEngine.EmotionResult result =
                EmotionRecognitionEngine.fuseSignals(nn, surprisedSignals);

        assertEquals("Surprised", result.emotion);
        assertFalse("Rationale must not duplicate confidence percentages",
                result.rationale.contains("% confidence"));
    }

    @Test
    public void testFuseSignals_normalAwakeEyesStayNeutral() {
        TFLiteEmotionClassifier.ClassificationResult nn =
                mockNnResult("Neutral", 0.78f, 0.05f, 0.07f, 0.05f, 0.05f, 0.02f);

        // Regression test for old avgEye >= 0.78f false-positive Surprised bug
        EmotionRecognitionEngine.LandmarkSignals neutralSignals =
                new EmotionRecognitionEngine.LandmarkSignals(
                        0.12f, // smileProb
                        0.95f, // avgEyeOpen (normal awake eyes)
                        0.02f, // innerMouthOpenRatio (closed mouth)
                        0.00f, // lipCornerLift
                        0.30f, // browRaiseRatio (relaxed brows)
                        0.48f, // browFurrowRatio
                        0.25f, // eyeAspectRatio
                        0f,
                        true, true, true
                );

        EmotionRecognitionEngine.EmotionResult result =
                EmotionRecognitionEngine.fuseSignals(nn, neutralSignals);

        assertEquals("Neutral", result.emotion);
    }

    @Test
    public void testFuseSignals_excitedHappyAngrySad() {
        // Excited: strong smile + open mouth
        EmotionRecognitionEngine.LandmarkSignals excitedSignals =
                new EmotionRecognitionEngine.LandmarkSignals(
                        0.90f, 0.85f, 0.22f, 0.08f, 0.37f, 0.48f, 0.26f, 0f, true, true, true);
        TFLiteEmotionClassifier.ClassificationResult excitedNn =
                mockNnResult("Happy", 0.05f, 0.75f, 0.15f, 0.02f, 0.03f, 0.82f);
        assertEquals("Excited", EmotionRecognitionEngine.fuseSignals(excitedNn, excitedSignals).emotion);

        // Happy: closed smile
        EmotionRecognitionEngine.LandmarkSignals happySignals =
                new EmotionRecognitionEngine.LandmarkSignals(
                        0.82f, 0.80f, 0.03f, 0.07f, 0.30f, 0.48f, 0.24f, 0f, true, true, true);
        TFLiteEmotionClassifier.ClassificationResult happyNn =
                mockNnResult("Happy", 0.10f, 0.80f, 0.04f, 0.03f, 0.03f, 0.36f);
        assertEquals("Happy", EmotionRecognitionEngine.fuseSignals(happyNn, happySignals).emotion);

        // Angry: furrowed + lowered brows
        EmotionRecognitionEngine.LandmarkSignals angrySignals =
                new EmotionRecognitionEngine.LandmarkSignals(
                        0.04f, 0.78f, 0.02f, -0.01f, 0.20f, 0.28f, 0.22f, 0f, true, true, true);
        TFLiteEmotionClassifier.ClassificationResult angryNn =
                mockNnResult("Angry", 0.25f, 0.02f, 0.05f, 0.10f, 0.58f, 0.01f);
        assertEquals("Angry", EmotionRecognitionEngine.fuseSignals(angryNn, angrySignals).emotion);

        // Sad: drooped mouth corners
        EmotionRecognitionEngine.LandmarkSignals sadSignals =
                new EmotionRecognitionEngine.LandmarkSignals(
                        0.03f, 0.68f, 0.02f, -0.05f, 0.29f, 0.46f, 0.22f, -7f, true, true, true);
        TFLiteEmotionClassifier.ClassificationResult sadNn =
                mockNnResult("Sad", 0.25f, 0.02f, 0.03f, 0.62f, 0.08f, 0.01f);
        assertEquals("Sad", EmotionRecognitionEngine.fuseSignals(sadNn, sadSignals).emotion);
    }

    @Test
    public void testTemporalEmotionSmoother_dampensSingleFrameSpikeAndResets() {
        EmotionRecognitionEngine.TemporalEmotionSmoother smoother =
                new EmotionRecognitionEngine.TemporalEmotionSmoother();

        Map<String, Float> surprisedScores = new HashMap<>();
        surprisedScores.put("Surprised", 0.85f);
        surprisedScores.put("Neutral", 0.10f);
        surprisedScores.put("Happy", 0.05f);

        Map<String, Float> happySpike = new HashMap<>();
        happySpike.put("Happy", 0.70f);
        happySpike.put("Surprised", 0.20f);
        happySpike.put("Neutral", 0.10f);

        EmotionRecognitionEngine.EmotionResult r1 = new EmotionRecognitionEngine.EmotionResult(
                "Surprised", "😲", 85, "Startled open-mouth posture", surprisedScores);
        EmotionRecognitionEngine.EmotionResult r2 = new EmotionRecognitionEngine.EmotionResult(
                "Happy", "😊", 70, "Smile contours", happySpike);

        assertEquals("Surprised", smoother.smooth(r1).emotion);
        assertEquals("Surprised", smoother.smooth(r1).emotion);
        // Single noisy frame should not flip from Surprised to Happy
        assertEquals("Surprised", smoother.smooth(r2).emotion);

        smoother.reset();
        assertEquals("Happy", smoother.smooth(r2).emotion);
    }

    @Test
    public void testSentimentAnalyzer_phrasesNegationsAndWinningKeyword() {
        SentimentAnalyzer.SentimentResult fedUp =
                SentimentAnalyzer.analyzeText("I am completely fed up with this delay");
        assertNotNull(fedUp);
        assertEquals("Angry", fedUp.emotion);
        assertEquals("fed up", fedUp.matchingKeyword);

        SentimentAnalyzer.SentimentResult outOfNowhere =
                SentimentAnalyzer.analyzeText("That news came out of nowhere");
        assertNotNull(outOfNowhere);
        assertEquals("Surprised", outOfNowhere.emotion);
        assertEquals("out of nowhere", outOfNowhere.matchingKeyword);

        SentimentAnalyzer.SentimentResult bestDay =
                SentimentAnalyzer.analyzeText("Today was the best day ever");
        assertNotNull(bestDay);
        assertEquals("Excited", bestDay.emotion);
        assertEquals("best day", bestDay.matchingKeyword);

        SentimentAnalyzer.SentimentResult notHappy =
                SentimentAnalyzer.analyzeText("I am not happy with how it went");
        assertNotNull(notHappy);
        assertEquals("Sad", notHappy.emotion);

        SentimentAnalyzer.SentimentResult notAngry =
                SentimentAnalyzer.analyzeText("I am not angry at all");
        assertNull(notAngry);

        SentimentAnalyzer.SentimentResult winningKeyword =
                SentimentAnalyzer.analyzeText("Today was fine until I got furious and annoyed");
        assertNotNull(winningKeyword);
        assertEquals("Angry", winningKeyword.emotion);
        assertEquals("furious", winningKeyword.matchingKeyword);
    }
}
