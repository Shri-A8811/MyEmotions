package com.mitaoe.shridhar202401040197;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.mitaoe.shridhar202401040197.data.AppDatabase;
import com.mitaoe.shridhar202401040197.data.Emotion;
import com.mitaoe.shridhar202401040197.databinding.ActivityHomeBinding;
import com.mitaoe.shridhar202401040197.session.SessionManager;

import java.util.concurrent.Executors;

public class HomeActivity extends AppCompatActivity {

    private ActivityHomeBinding binding;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);

        setSupportActionBar(binding.toolbarHome);

        String fullName = sessionManager.getFullName();
        binding.tvWelcomeUser.setText(getString(R.string.home_welcome, fullName));

        binding.cardLogEmotion.setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, LogYourEmotionActivity.class));
        });

        binding.cardMyEmotions.setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, MyEmotionsActivity.class));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadEmotionStats();
    }

    private void loadEmotionStats() {
        long userId = sessionManager.getUserId();
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            int count = db.emotionDao().getEmotionCountForUser(userId);
            Emotion latest = db.emotionDao().getLatestEmotionForUser(userId);

            runOnUiThread(() -> {
                binding.tvTotalLogged.setText(count + (count == 1 ? " reflection" : " reflections"));
                if (latest != null) {
                    binding.tvLatestEmotion.setText(latest.getFormattedEmotion());
                } else {
                    binding.tvLatestEmotion.setText(getString(R.string.stats_none));
                }
            });
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_home, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            sessionManager.clearSession();
            Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
