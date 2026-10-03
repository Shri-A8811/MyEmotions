package com.mitaoe.shridhar202401040197;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.mitaoe.shridhar202401040197.data.AppDatabase;
import com.mitaoe.shridhar202401040197.data.Emotion;
import com.mitaoe.shridhar202401040197.data.PasswordUtils;
import com.mitaoe.shridhar202401040197.data.User;
import com.mitaoe.shridhar202401040197.session.SessionManager;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AppFlowTest {

    @Test
    public void testDatabaseAndSession() {
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(context);

        String salt = PasswordUtils.generateSalt();
        String hash = PasswordUtils.hashPassword("password123", salt);
        User user = new User("Shridhar Test", "shridhar@test.com", hash, salt);

        long userId = db.userDao().insertUser(user);
        assertTrue(userId > 0);

        User retrieved = db.userDao().findByEmail("shridhar@test.com");
        assertNotNull(retrieved);
        assertEquals("Shridhar Test", retrieved.getFullName());

        Emotion emotion = new Emotion(userId, "/dummy/path.jpg", "Feeling great today!", "2026-10-03", "10:00 AM", "Happy");
        long emotionId = db.emotionDao().insertEmotion(emotion);
        assertTrue(emotionId > 0);

        int count = db.emotionDao().getEmotionCountForUser(userId);
        assertEquals(1, count);

        SessionManager session = new SessionManager(context);
        session.createLoginSession(userId, retrieved.getFullName(), retrieved.getEmail());
        assertTrue(session.isLoggedIn());
        assertEquals(userId, session.getUserId());
    }

    @Test
    public void testTFLiteEmotionClassifier() {
        Context context = ApplicationProvider.getApplicationContext();
        com.mitaoe.shridhar202401040197.ml.TFLiteEmotionClassifier classifier =
                com.mitaoe.shridhar202401040197.ml.TFLiteEmotionClassifier.getInstance(context);

        assertTrue("TFLite classifier should be ready", classifier.isReady());

        // Create a test synthetic face bitmap (48x48)
        android.graphics.Bitmap testBmp = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888);
        com.mitaoe.shridhar202401040197.ml.TFLiteEmotionClassifier.ClassificationResult result =
                classifier.classifyFace(testBmp);

        assertNotNull("Classification result should not be null", result);
        assertNotNull("Detected emotion should not be null", result.emotion);
        assertTrue("Confidence should be positive", result.confidence > 0);
        assertNotNull("Rationale should not be null", result.rationale);
        assertTrue("Rationale should describe detection", result.rationale.contains("Neural network"));
    }
}
