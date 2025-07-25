package org.smart.attendance_beta;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY = 2000; // 2 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Initialize views
        TextView tvAppName = findViewById(R.id.tv_app_name);
        TextView tvTagline = findViewById(R.id.tv_tagline);

        if (tvAppName != null) {
            tvAppName.setText("TimeTracker Pro");
        }
        if (tvTagline != null) {
            tvTagline.setText("Smart Attendance Management");
        }

        // Check authentication status after splash delay
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                checkAuthenticationAndRedirect();
            }
        }, SPLASH_DELAY);
    }

    private void checkAuthenticationAndRedirect() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null && currentUser.isEmailVerified()) {
            // User is logged in and email is verified, redirect to appropriate dashboard
            // We'll check their role in the login activity
            Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
            intent.putExtra("auto_login", true);
            startActivity(intent);
        } else {
            // User not logged in or email not verified, go to login
            startActivity(new Intent(SplashActivity.this, LoginActivity.class));
        }

        finish();
    }
}