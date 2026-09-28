package com.mitaoe.shridhar202401040197.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "emotion_table",
    foreignKeys = @ForeignKey(
        entity = User.class,
        parentColumns = "userId",
        childColumns = "userId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("userId")}
)
public class Emotion {

    @PrimaryKey(autoGenerate = true)
    private long emotionId;

    @ColumnInfo(name = "userId")
    private long userId;

    @ColumnInfo(name = "photo_path")
    private String photoPath;

    @ColumnInfo(name = "note")
    private String note;

    @ColumnInfo(name = "date")
    private String date;

    @ColumnInfo(name = "time")
    private String time;

    @ColumnInfo(name = "emotion_type", defaultValue = "Happy")
    private String emotionType;

    public Emotion(long userId, String photoPath, String note, String date, String time, String emotionType) {
        this.userId = userId;
        this.photoPath = photoPath;
        this.note = note;
        this.date = date;
        this.time = time;
        this.emotionType = (emotionType != null && !emotionType.isEmpty()) ? emotionType : "Happy";
    }

    public long getEmotionId() {
        return emotionId;
    }

    public void setEmotionId(long emotionId) {
        this.emotionId = emotionId;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public void setPhotoPath(String photoPath) {
        this.photoPath = photoPath;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getEmotionType() {
        return emotionType != null ? emotionType : "Happy";
    }

    public void setEmotionType(String emotionType) {
        this.emotionType = emotionType;
    }

    public String getFormattedEmotion() {
        String type = getEmotionType();
        switch (type.toLowerCase()) {
            case "happy":
                return "😊 Happy";
            case "sad":
                return "😢 Sad";
            case "surprised":
                return "😲 Surprised";
            case "angry":
                return "😠 Angry";
            case "excited":
                return "🤩 Excited";
            default:
                return "😐 Neutral";
        }
    }
}
