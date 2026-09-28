package com.mitaoe.shridhar202401040197.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Intelligent text sentiment & emotion analyzer.
 * Evaluates reflection notes to corroborate or detect emotions even when photo lighting is dim
 * or when photos don't contain a clear frontal face.
 */
public class SentimentAnalyzer {

    private static final Set<String> HAPPY_WORDS = new HashSet<>(Arrays.asList(
            "happy", "joy", "joyful", "glad", "delighted", "cheerful", "pleased", "content",
            "blessed", "grateful", "good", "great", "wonderful", "fantastic", "awesome",
            "lovely", "smile", "smiling", "laughed", "laughing", "fun", "enjoyed", "enjoying",
            "peace", "love", "loved", "positive", "bright", "sunny", "warm", "satisfaction"
    ));

    private static final Set<String> EXCITED_WORDS = new HashSet<>(Arrays.asList(
            "excited", "thrilled", "ecstatic", "hyped", "pumped", "energetic", "celebrate",
            "celebrating", "win", "won", "winning", "hurray", "yay", "cant wait", "amazing",
            "overjoyed", "elated", "triumph", "passionate", "epic", "best day", "proud", "achievement"
    ));

    private static final Set<String> SAD_WORDS = new HashSet<>(Arrays.asList(
            "sad", "unhappy", "depressed", "sorrow", "cried", "crying", "tears", "lonely",
            "alone", "heartbroken", "grief", "down", "gloomy", "miserable", "hurt", "hurting",
            "hopeless", "miss", "missing", "loss", "lost", "bad", "terrible", "awful", "regret"
    ));

    private static final Set<String> ANGRY_WORDS = new HashSet<>(Arrays.asList(
            "angry", "mad", "furious", "annoyed", "annoying", "irritated", "irritating",
            "frustrated", "frustration", "rage", "hate", "hating", "disgusted", "unfair",
            "stress", "stressed", "stressful", "tense", "argue", "argued", "fed up", "pissed"
    ));

    private static final Set<String> SURPRISED_WORDS = new HashSet<>(Arrays.asList(
            "surprised", "surprise", "shocked", "shocking", "stunned", "astonished", "amazed",
            "unbelievable", "unexpected", "wow", "unreal", "speechless", "out of nowhere",
            "sudden", "suddenly", "wonder", "whoa"
    ));

    private static final Set<String> NEUTRAL_WORDS = new HashSet<>(Arrays.asList(
            "normal", "okay", "fine", "calm", "neutral", "peaceful", "routine", "regular",
            "usual", "ordinary", "average", "chill", "relaxed", "working", "study", "studying",
            "quiet", "steady", "nothing special", "simple"
    ));

    public static class SentimentResult {
        public final String emotion;
        public final String emoji;
        public final int confidence;
        public final String matchingKeyword;
        public final Map<String, Integer> emotionHits;

        public SentimentResult(String emotion, String emoji, int confidence, String matchingKeyword, Map<String, Integer> emotionHits) {
            this.emotion = emotion;
            this.emoji = emoji;
            this.confidence = confidence;
            this.matchingKeyword = matchingKeyword;
            this.emotionHits = emotionHits;
        }

        public boolean hasStrongSignal() {
            return confidence >= 70 && matchingKeyword != null;
        }
    }

    @Nullable
    public static SentimentResult analyzeText(@Nullable String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        String cleaned = text.toLowerCase(Locale.getDefault())
                .replaceAll("[^a-zA-Z0-9\\s]", " ");
        String[] tokens = cleaned.split("\\s+");

        Map<String, Integer> hits = new HashMap<>();
        hits.put("Happy", 0);
        hits.put("Excited", 0);
        hits.put("Sad", 0);
        hits.put("Angry", 0);
        hits.put("Surprised", 0);
        hits.put("Neutral", 0);

        String firstHitWord = null;
        String firstHitEmotion = null;

        for (String token : tokens) {
            if (EXCITED_WORDS.contains(token)) {
                hits.put("Excited", hits.get("Excited") + 3);
                if (firstHitWord == null) { firstHitWord = token; firstHitEmotion = "Excited"; }
            }
            if (HAPPY_WORDS.contains(token)) {
                hits.put("Happy", hits.get("Happy") + 2);
                if (firstHitWord == null) { firstHitWord = token; firstHitEmotion = "Happy"; }
            }
            if (SAD_WORDS.contains(token)) {
                hits.put("Sad", hits.get("Sad") + 3);
                if (firstHitWord == null) { firstHitWord = token; firstHitEmotion = "Sad"; }
            }
            if (ANGRY_WORDS.contains(token)) {
                hits.put("Angry", hits.get("Angry") + 3);
                if (firstHitWord == null) { firstHitWord = token; firstHitEmotion = "Angry"; }
            }
            if (SURPRISED_WORDS.contains(token)) {
                hits.put("Surprised", hits.get("Surprised") + 3);
                if (firstHitWord == null) { firstHitWord = token; firstHitEmotion = "Surprised"; }
            }
            if (NEUTRAL_WORDS.contains(token)) {
                hits.put("Neutral", hits.get("Neutral") + 1);
                if (firstHitWord == null) { firstHitWord = token; firstHitEmotion = "Neutral"; }
            }
        }

        String bestEmotion = null;
        int maxHit = 0;
        for (Map.Entry<String, Integer> entry : hits.entrySet()) {
            if (entry.getValue() > maxHit) {
                maxHit = entry.getValue();
                bestEmotion = entry.getKey();
            }
        }

        if (maxHit == 0 || bestEmotion == null) {
            return null;
        }

        String emoji;
        switch (bestEmotion) {
            case "Happy": emoji = "😊"; break;
            case "Excited": emoji = "🤩"; break;
            case "Sad": emoji = "😢"; break;
            case "Angry": emoji = "😠"; break;
            case "Surprised": emoji = "😲"; break;
            case "Neutral":
            default: emoji = "😐"; break;
        }

        int confidence = Math.min(95, 65 + (maxHit * 8));
        return new SentimentResult(bestEmotion, emoji, confidence, firstHitWord, hits);
    }
}
