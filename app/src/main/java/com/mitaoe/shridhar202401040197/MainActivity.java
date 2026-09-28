package com.mitaoe.shridhar202401040197;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.mitaoe.shridhar202401040197.session.SessionManager;

/**
 * MainActivity acts as the decision maker using SharedPreferences.
 * If user is already logged in -> navigates to HomeActivity.
 * If not logged in -> navigates to LoginActivity.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SessionManager sessionManager = new SessionManager(this);

        if (sessionManager.isLoggedIn()) {
            startActivity(new Intent(MainActivity.this, HomeActivity.class));
        } else {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
        }
        finish();
    }
}
