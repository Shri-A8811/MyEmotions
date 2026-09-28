package com.mitaoe.shridhar202401040197.adapter;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mitaoe.shridhar202401040197.data.Emotion;
import com.mitaoe.shridhar202401040197.databinding.ItemEmotionBinding;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class EmotionAdapter extends RecyclerView.Adapter<EmotionAdapter.EmotionViewHolder> {

    public interface OnEmotionClickListener {
        void onEmotionClick(Emotion emotion);
    }

    private List<Emotion> emotionList = new ArrayList<>();
    private OnEmotionClickListener listener;

    public void setOnEmotionClickListener(OnEmotionClickListener listener) {
        this.listener = listener;
    }

    public void setEmotions(List<Emotion> emotions) {
        this.emotionList = emotions != null ? emotions : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EmotionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemEmotionBinding binding = ItemEmotionBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new EmotionViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull EmotionViewHolder holder, int position) {
        holder.bind(emotionList.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return emotionList.size();
    }

    static class EmotionViewHolder extends RecyclerView.ViewHolder {

        private final ItemEmotionBinding binding;

        public EmotionViewHolder(@NonNull ItemEmotionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Emotion emotion, OnEmotionClickListener listener) {
            binding.tvItemNote.setText(emotion.getNote());
            String dateTimeStr = "📅 " + emotion.getDate() + " • " + emotion.getTime();
            binding.tvItemDateTime.setText(dateTimeStr);
            binding.tvItemEmotionBadge.setText(emotion.getFormattedEmotion());

            // Set mood color styling
            applyMoodColor(emotion.getEmotionType());

            if (emotion.getPhotoPath() != null) {
                File file = new File(emotion.getPhotoPath());
                if (file.exists()) {
                    try {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = 2; // scale down for grid preview efficiency
                        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                        if (bitmap != null) {
                            binding.ivItemPhoto.setImageBitmap(bitmap);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEmotionClick(emotion);
                }
            });
        }

        private void applyMoodColor(String emotionType) {
            if (emotionType == null) emotionType = "Neutral";
            int bgColor;
            int textColor;

            switch (emotionType.toLowerCase()) {
                case "happy":
                    bgColor = Color.parseColor("#DCFCE7");
                    textColor = Color.parseColor("#15803D");
                    break;
                case "excited":
                    bgColor = Color.parseColor("#FEF3C7");
                    textColor = Color.parseColor("#B45309");
                    break;
                case "sad":
                    bgColor = Color.parseColor("#DBEAFE");
                    textColor = Color.parseColor("#1D4ED8");
                    break;
                case "surprised":
                    bgColor = Color.parseColor("#EDE9FE");
                    textColor = Color.parseColor("#6D28D9");
                    break;
                case "angry":
                    bgColor = Color.parseColor("#FFE4E6");
                    textColor = Color.parseColor("#BE123C");
                    break;
                case "neutral":
                default:
                    bgColor = Color.parseColor("#F1F5F9");
                    textColor = Color.parseColor("#475569");
                    break;
            }

            binding.tvItemEmotionBadge.setBackgroundTintList(ColorStateList.valueOf(bgColor));
            binding.tvItemEmotionBadge.setTextColor(textColor);
        }
    }
}
