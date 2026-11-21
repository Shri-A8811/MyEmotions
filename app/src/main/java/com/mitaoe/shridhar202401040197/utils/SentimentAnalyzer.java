package com.mitaoe.shridhar202401040197.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Intelligent text sentiment & emotion analyzer.
 * Evaluates reflection notes with multi-word phrase matching, negation handling,
 * and winning-emotion keyword attribution.
 */
public class SentimentAnalyzer {

    private static final Set<String> NEGATION_WORDS = new HashSet<>(Arrays.asList(
            "not", "no", "never", "dont", "don", "didnt", "didn", "wasnt", "wasn",
            "isnt", "isn", "arent", "aren", "cant", "cannot", "hardly"
    ));

    private static final Set<String> HAPPY_WORDS = new HashSet<>(Arrays.asList(
            "happy", "joy", "joyful", "glad", "delighted", "cheerful", "pleased", "content",
            "blessed", "grateful", "good", "great", "wonderful", "fantastic", "awesome",
            "lovely", "smile", "smiling", "laughed", "laughing", "fun", "enjoyed", "enjoying",
            "peace", "love", "loved", "positive", "bright", "sunny", "warm", "satisfaction"
    ));

    private static final Set<String> EXCITED_WORDS = new HashSet<>(Arrays.asList(
            "excited", "thrilled", "ecstatic", "hyped", "pumped", "energetic", "celebrate",
            "celebrating", "win", "won", "winning", "hurray", "yay", "amazing",
            "overjoyed", "elated", "triumph", "passionate", "epic", "proud", "achievement"
    ));

    private static final Set<String> SAD_WORDS = new HashSet<>(Arrays.asList(
            "sad", "unhappy", "depressed", "sorrow", "cried", "crying", "tears", "lonely",
            "alone", "heartbroken", "grief", "down", "gloomy", "miserable", "hurt", "hurting",
            "hopeless", "miss", "missing", "loss", "lost", "bad", "terrible", "awful", "regret"
    ));

    private static final Set<String> ANGRY_WORDS = new HashSet<>(Arrays.asList(
            "angry", "mad", "furious", "annoyed", "annoying", "irritated", "irritating",
            "frustrated", "frustration", "rage", "hate", "hating", "disgusted", "unfair",
            "stress", "stressed", "stressful", "tense", "argue", "argued", "pissed"
    ));

    private static final Set<String> SURPRISED_WORDS = new HashSet<>(Arrays.asList(
            "surprised", "surprise", "shocked", "shocking", "stunned", "astonished", "amazed",
            "unbelievable", "unexpected", "wow", "unreal", "speechless",
            "sudden", "suddenly", "wonder", "whoa"
    ));

    private static final Set<String> NEUTRAL_WORDS = new HashSet<>(Arrays.asList(
            "normal", "okay", "fine", "calm", "neutral", "peaceful", "routine", "regular",
            "usual", "ordinary", "average", "chill", "relaxed", "working", "study", "studying",
            "quiet", "steady", "simple"
    ));

    private static final Map<String, String> PHRASE_TO_EMOTION = new LinkedHashMap<>();
    static {
        PHRASE_TO_EMOTION.put("cant wait", "Excited");
        PHRASE_TO_EMOTION.put("can t wait", "Excited");
        PHRASE_TO_EMOTION.put("best day", "Excited");
        PHRASE_TO_EMOTION.put("so happy", "Happy");
        PHRASE_TO_EMOTION.put("fed up", "Angry");
        PHRASE_TO_EMOTION.put("pissed off", "Angry");
        PHRASE_TO_EMOTION.put("out of nowhere", "Surprised");
        PHRASE_TO_EMOTION.put("freaked out", "Surprised");
        PHRASE_TO_EMOTION.put("blown away", "Surprised");
        PHRASE_TO_EMOTION.put("nothing special", "Neutral");
    }

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
                .replaceAll("[^a-zA-Z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (cleaned.isEmpty()) {
            return null;
        }

        Map<String, Integer> hits = new HashMap<>();
        hits.put("Happy", 0);
        hits.put("Excited", 0);
        hits.put("Sad", 0);
        hits.put("Angry", 0);
        hits.put("Surprised", 0);
        hits.put("Neutral", 0);

        Map<String, String> emotionKeyword = new HashMap<>();

        String padded = " " + cleaned + " ";
        for (Map.Entry<String, String> entry : PHRASE_TO_EMOTION.entrySet()) {
            String phrase = entry.getKey();
            if (padded.contains(" " + phrase + " ")) {
                String emotion = entry.getValue();
                int weight = "Neutral".equals(emotion) ? 2 : 4;
                hits.put(emotion, hits.get(emotion) + weight);
                emotionKeyword.putIfAbsent(emotion, phrase);
            }
        }

        String[] tokens = cleaned.split("\\s+");
        int negationWindow = 0;

        for (String token : tokens) {
            if (NEGATION_WORDS.contains(token)) {
                negationWindow = 2;
                continue;
            }

            boolean isNegated = negationWindow > 0;
            if (negationWindow > 0) {
                negationWindow--;
            }

            if (EXCITED_WORDS.contains(token)) {
                if (isNegated) {
                    hits.put("Sad", hits.get("Sad") + 2);
                    emotionKeyword.putIfAbsent("Sad", "not " + token);
                } else {
                    hits.put("Excited", hits.get("Excited") + 3);
                    emotionKeyword.putIfAbsent("Excited", token);
                }
            }
            if (HAPPY_WORDS.contains(token)) {
                if (isNegated) {
                    hits.put("Sad", hits.get("Sad") + 2);
                    emotionKeyword.putIfAbsent("Sad", "not " + token);
                } else {
                    hits.put("Happy", hits.get("Happy") + 2);
                    emotionKeyword.putIfAbsent("Happy", token);
                }
            }
            if (SAD_WORDS.contains(token)) {
                if (!isNegated) {
                    hits.put("Sad", hits.get("Sad") + 3);
                    emotionKeyword.putIfAbsent("Sad", token);
                }
            }
            if (ANGRY_WORDS.contains(token)) {
                if (!isNegated) {
                    hits.put("Angry", hits.get("Angry") + 3);
                    emotionKeyword.putIfAbsent("Angry", token);
                }
            }
            if (SURPRISED_WORDS.contains(token)) {
                if (!isNegated) {
                    hits.put("Surprised", hits.get("Surprised") + 3);
                    emotionKeyword.putIfAbsent("Surprised", token);
                }
            }
            if (NEUTRAL_WORDS.contains(token)) {
                if (!isNegated) {
                    hits.put("Neutral", hits.get("Neutral") + 1);
                    emotionKeyword.putIfAbsent("Neutral", token);
                }
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
        String winningKeyword = emotionKeyword.get(bestEmotion);
        return new SentimentResult(bestEmotion, emoji, confidence, winningKeyword, hits);
    }
}
