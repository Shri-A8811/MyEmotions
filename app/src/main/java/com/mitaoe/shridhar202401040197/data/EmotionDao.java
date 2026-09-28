package com.mitaoe.shridhar202401040197.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface EmotionDao {

    @Insert
    long insertEmotion(Emotion emotion);

    @Delete
    void deleteEmotion(Emotion emotion);

    @Query("SELECT * FROM emotion_table WHERE userId = :userId ORDER BY emotionId DESC")
    List<Emotion> getEmotionsForUser(long userId);

    @Query("SELECT * FROM emotion_table WHERE userId = :userId AND LOWER(emotion_type) = LOWER(:emotionType) ORDER BY emotionId DESC")
    List<Emotion> getEmotionsForUserByType(long userId, String emotionType);

    @Query("SELECT COUNT(*) FROM emotion_table WHERE userId = :userId")
    int getEmotionCountForUser(long userId);

    @Query("SELECT * FROM emotion_table WHERE userId = :userId ORDER BY emotionId DESC LIMIT 1")
    Emotion getLatestEmotionForUser(long userId);
}
