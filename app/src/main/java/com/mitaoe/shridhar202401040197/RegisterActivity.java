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
import com.mitaoe.shridhar202401040197.databinding.ActivityRegisterBinding;
import com.mitaoe.shridhar202401040197.session.SessionManager;

import java.util.concurrent.Executors;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);

        binding.btnRegister.setOnClickListener(v -> attemptRegister());
        binding.tvLoginLink.setOnClickListener(v -> finish());
    }

    private void attemptRegister() {
        String fullName = binding.etFullName.getText() != null ? binding.etFullName.getText().toString().trim() : "";
        String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString().trim() : "";
        String confirmPassword = binding.etConfirmPassword.getText() != null ? binding.etConfirmPassword.getText().toString().trim() : "";

        if (fullName.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Snackbar.make(binding.getRoot(), getString(R.string.err_empty_fields), Snackbar.LENGTH_SHORT).show();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Snackbar.make(binding.getRoot(), getString(R.string.err_invalid_email), Snackbar.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Snackbar.make(binding.getRoot(), getString(R.string.err_password_short), Snackbar.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Snackbar.make(binding.getRoot(), getString(R.string.err_password_mismatch), Snackbar.LENGTH_SHORT).show();
            return;
        }

        binding.btnRegister.setEnabled(false);

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            User existing = db.userDao().findByEmail(email);

            if (existing != null) {
                runOnUiThread(() -> {
                    binding.btnRegister.setEnabled(true);
                    Snackbar.make(binding.getRoot(), getString(R.string.err_user_exists), Snackbar.LENGTH_SHORT).show();
                });
                return;
            }

            String salt = PasswordUtils.generateSalt();
            String passwordHash = PasswordUtils.hashPassword(password, salt);
            User newUser = new User(fullName, email, passwordHash, salt);
            long newUserId = db.userDao().insertUser(newUser);

            runOnUiThread(() -> {
                // Auto-login upon successful registration
                sessionManager.createLoginSession(newUserId, fullName, email);

                Toast.makeText(RegisterActivity.this, "Welcome, " + fullName + "!", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(RegisterActivity.this, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        });
    }
}
