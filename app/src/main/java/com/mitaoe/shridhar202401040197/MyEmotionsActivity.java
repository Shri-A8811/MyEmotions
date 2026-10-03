package com.mitaoe.shridhar202401040197;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.mitaoe.shridhar202401040197.adapter.EmotionAdapter;
import com.mitaoe.shridhar202401040197.data.AppDatabase;
import com.mitaoe.shridhar202401040197.data.Emotion;
import com.mitaoe.shridhar202401040197.databinding.ActivityMyEmotionsBinding;
import com.mitaoe.shridhar202401040197.databinding.DialogEmotionDetailBinding;
import com.mitaoe.shridhar202401040197.session.SessionManager;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executors;

public class MyEmotionsActivity extends AppCompatActivity {

    private ActivityMyEmotionsBinding binding;
    private SessionManager sessionManager;
    private EmotionAdapter adapter;
    private String currentSelectedFilter = "ALL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMyEmotionsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);

        setSupportActionBar(binding.toolbarMyEmotions);
        binding.toolbarMyEmotions.setNavigationOnClickListener(v -> finish());

        setupRecyclerView();
        setupFilters();
        loadUserEmotions();
    }

    private void setupRecyclerView() {
        adapter = new EmotionAdapter();
        binding.rvEmotions.setLayoutManager(new GridLayoutManager(this, 2));
        binding.rvEmotions.setAdapter(adapter);

        adapter.setOnEmotionClickListener(this::showEmotionDetailBottomSheet);
    }

    private void setupFilters() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);

            if (checkedId == R.id.chipFilterHappy) {
                currentSelectedFilter = "Happy";
            } else if (checkedId == R.id.chipFilterExcited) {
                currentSelectedFilter = "Excited";
            } else if (checkedId == R.id.chipFilterSad) {
                currentSelectedFilter = "Sad";
            } else if (checkedId == R.id.chipFilterNeutral) {
                currentSelectedFilter = "Neutral";
            } else if (checkedId == R.id.chipFilterSurprised) {
                currentSelectedFilter = "Surprised";
            } else if (checkedId == R.id.chipFilterAngry) {
                currentSelectedFilter = "Angry";
            } else {
                currentSelectedFilter = "ALL";
            }

            loadUserEmotions();
        });
    }

    private void loadUserEmotions() {
        long userId = sessionManager.getUserId();

        Executors.newSingleThreadExecutor().execute(() -> {
            List<Emotion> emotions;
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());

            if ("ALL".equalsIgnoreCase(currentSelectedFilter)) {
                emotions = db.emotionDao().getEmotionsForUser(userId);
            } else {
                emotions = db.emotionDao().getEmotionsForUserByType(userId, currentSelectedFilter);
            }

            runOnUiThread(() -> {
                if (emotions == null || emotions.isEmpty()) {
                    binding.rvEmotions.setVisibility(View.GONE);
                    binding.tvEmptyState.setVisibility(View.VISIBLE);
                } else {
                    binding.rvEmotions.setVisibility(View.VISIBLE);
                    binding.tvEmptyState.setVisibility(View.GONE);
                    adapter.setEmotions(emotions);
                }
            });
        });
    }

    private void showEmotionDetailBottomSheet(Emotion emotion) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        DialogEmotionDetailBinding dialogBinding = DialogEmotionDetailBinding.inflate(LayoutInflater.from(this));
        bottomSheetDialog.setContentView(dialogBinding.getRoot());

        dialogBinding.tvDetailNote.setText(emotion.getNote());
        String dateTimeStr = "📅 " + emotion.getDate() + " • " + emotion.getTime();
        dialogBinding.tvDetailDateTime.setText(dateTimeStr);
        dialogBinding.tvDetailEmotionBadge.setText(emotion.getFormattedEmotion());

        if (emotion.getPhotoPath() != null) {
            File file = new File(emotion.getPhotoPath());
            if (file.exists()) {
                try {
                    Bitmap bitmap = LogYourEmotionActivity.decodeSampledBitmap(file.getAbsolutePath(), 1200, 1200);
                    if (bitmap != null) {
                        dialogBinding.ivDetailPhoto.setImageBitmap(bitmap);
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        dialogBinding.btnClose.setOnClickListener(v -> bottomSheetDialog.dismiss());

        dialogBinding.btnDelete.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            confirmAndDeleteEmotion(emotion);
        });

        bottomSheetDialog.show();
    }

    private void confirmAndDeleteEmotion(Emotion emotion) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_title)
                .setMessage(R.string.confirm_delete_msg)
                .setPositiveButton(R.string.btn_delete_entry, (dialog, which) -> deleteEmotion(emotion))
                .setNegativeButton(R.string.btn_close, null)
                .show();
    }

    private void deleteEmotion(Emotion emotion) {
        Executors.newSingleThreadExecutor().execute(() -> {
            // Delete record from Room DB
            AppDatabase.getInstance(getApplicationContext()).emotionDao().deleteEmotion(emotion);

            // Delete private image file from disk
            if (emotion.getPhotoPath() != null) {
                File photoFile = new File(emotion.getPhotoPath());
                if (photoFile.exists()) {
                    photoFile.delete();
                }
            }

            runOnUiThread(() -> {
                Toast.makeText(MyEmotionsActivity.this, getString(R.string.msg_emotion_deleted), Toast.LENGTH_SHORT).show();
                loadUserEmotions();
            });
        });
    }
}
