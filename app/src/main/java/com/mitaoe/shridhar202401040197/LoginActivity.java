package com.mitaoe.shridhar202401040197;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.mitaoe.shridhar202401040197.data.AppDatabase;
import com.mitaoe.shridhar202401040197.data.PasswordUtils;
import com.mitaoe.shridhar202401040197.data.User;
import com.mitaoe.shridhar202401040197.databinding.ActivityLoginBinding;
import com.mitaoe.shridhar202401040197.session.SessionManager;

import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);

        binding.btnLogin.setOnClickListener(v -> attemptLogin());

        binding.tvRegisterLink.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });
    }

    private void attemptLogin() {
        String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString().trim() : "";

        if (email.isEmpty() || password.isEmpty()) {
            Snackbar.make(binding.getRoot(), getString(R.string.err_empty_fields), Snackbar.LENGTH_SHORT).show();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Snackbar.make(binding.getRoot(), getString(R.string.err_invalid_email), Snackbar.LENGTH_SHORT).show();
            return;
        }

        binding.btnLogin.setEnabled(false);

        Executors.newSingleThreadExecutor().execute(() -> {
            User user = AppDatabase.getInstance(getApplicationContext()).userDao().findByEmail(email);

            runOnUiThread(() -> {
                binding.btnLogin.setEnabled(true);

                if (user == null) {
                    Snackbar.make(binding.getRoot(), getString(R.string.err_invalid_credentials), Snackbar.LENGTH_SHORT).show();
                    return;
                }

                String computedHash = PasswordUtils.hashPassword(password, user.getSalt());
                if (computedHash.equals(user.getPasswordHash())) {
                    // Remember login state using SharedPreferences
                    sessionManager.createLoginSession(user.getUserId(), user.getFullName(), user.getEmail());

                    Toast.makeText(LoginActivity.this, "Welcome back, " + user.getFullName() + "!", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                    finish();
                } else {
                    Snackbar.make(binding.getRoot(), getString(R.string.err_invalid_credentials), Snackbar.LENGTH_SHORT).show();
                }
            });
        });
    }
}
