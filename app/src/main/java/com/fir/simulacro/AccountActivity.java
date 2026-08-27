package com.fir.simulacro;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.Task;

public class AccountActivity extends AppCompatActivity {
    private TextView accountStatusText;
    private EditText emailInput;
    private EditText passwordInput;
    private Button googleSignInButton;
    private Button emailLoginButton;
    private Button emailRegisterButton;
    private Button syncNowButton;
    private Button signOutButton;
    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);
        try {
            
        startBackgroundAnimation();

            bindViews();
            setupFirebase();
            setupListeners();
            refreshUi();
        } catch (Exception e) {
            android.util.Log.e("AccountActivity", "Error en onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Error al cargar la actividad", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    private void bindViews() {
        try {
            accountStatusText = findViewById(R.id.accountStatusText);
            emailInput = findViewById(R.id.emailInput);
            passwordInput = findViewById(R.id.passwordInput);
            googleSignInButton = findViewById(R.id.googleSignInButton);
            emailLoginButton = findViewById(R.id.emailLoginButton);
            emailRegisterButton = findViewById(R.id.emailRegisterButton);
            syncNowButton = findViewById(R.id.syncNowButton);
            signOutButton = findViewById(R.id.signOutButton);
            Button backButton = findViewById(R.id.accountBackButton);
            if (backButton != null) {
                backButton.setOnClickListener(v -> finish());
            }
        } catch (Exception e) {
            android.util.Log.e("AccountActivity", "Error en bindViews: " + e.getMessage(), e);
            throw e;
        }
    }

    private void setupFirebase() {
        boolean configured = FirebaseInitializer.ensureInitialized(this);
        if (!configured) {
            accountStatusText.setText("Falta configurar Firebase. Añade los datos del proyecto para activar el acceso.");
            googleSignInButton.setEnabled(false);
            emailLoginButton.setEnabled(false);
            emailRegisterButton.setEnabled(false);
            syncNowButton.setEnabled(false);
            signOutButton.setEnabled(false);
            return;
        }

        GoogleSignInOptions options = CloudSyncManager.buildGoogleSignInOptions(this);
        googleSignInClient = GoogleSignIn.getClient(this, options);
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(Exception.class);
                        CloudSyncManager.signInWithGoogleAccount(
                                this,
                                account,
                                new SyncToastCallback("Google")
                        );
                    } catch (Exception e) {
                        Toast.makeText(this, "No se pudo iniciar con Google: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void setupListeners() {
        googleSignInButton.setOnClickListener(v -> startGoogleSignIn());
        emailLoginButton.setOnClickListener(v -> signInWithEmail(false));
        emailRegisterButton.setOnClickListener(v -> signInWithEmail(true));
        syncNowButton.setOnClickListener(v -> manualSync());
        signOutButton.setOnClickListener(v -> {
            CloudSyncManager.signOut(this);
            Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show();
            refreshUi();
        });
    }

    private void startGoogleSignIn() {
        if (googleSignInClient == null) {
            Toast.makeText(this, "Google no está configurado", Toast.LENGTH_SHORT).show();
            return;
        }
        googleSignInLauncher.launch(googleSignInClient.getSignInIntent());
    }

    private void signInWithEmail(boolean register) {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Escribe correo y contraseña", Toast.LENGTH_SHORT).show();
            return;
        }

        CloudSyncManager.ResultCallback callback = new SyncToastCallback(register ? "registro" : "correo");
        if (register) {
            CloudSyncManager.registerWithEmail(this, email, password, callback);
        } else {
            CloudSyncManager.signInWithEmail(this, email, password, callback);
        }
    }

    private void manualSync() {
        CloudSyncManager.syncNow(this, new SyncToastCallback("sincronización"));
    }

    private void refreshUi() {
        if (!FirebaseInitializer.ensureInitialized(this)) {
            return;
        }
        accountStatusText.setText("Sesión: " + CloudSyncManager.getSignedInLabel(this));
        boolean signedIn = CloudSyncManager.hasSignedInUser(this);
        syncNowButton.setEnabled(signedIn);
        signOutButton.setEnabled(signedIn);
        googleSignInButton.setEnabled(true);
        emailLoginButton.setEnabled(true);
        emailRegisterButton.setEnabled(true);
    }

    private class SyncToastCallback implements CloudSyncManager.ResultCallback {
        private final String label;

        SyncToastCallback(String label) {
            this.label = label;
        }

        @Override
        public void onSuccess(String message) {
            Toast.makeText(AccountActivity.this, message, Toast.LENGTH_SHORT).show();
            refreshUi();
        }

        @Override
        public void onError(Exception e) {
            Toast.makeText(AccountActivity.this, "Error en " + label + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
            refreshUi();
        }
    }
    private void startBackgroundAnimation() {
        android.view.ViewGroup root = findViewById(android.R.id.content);
        if (root == null || root.getChildCount() == 0) {
            return;
        }
        android.view.View content = root.getChildAt(0);
        if (content != null && content.getBackground() instanceof android.graphics.drawable.AnimationDrawable) {
            android.graphics.drawable.AnimationDrawable animated = (android.graphics.drawable.AnimationDrawable) content.getBackground();
            animated.setEnterFadeDuration(2000);
            animated.setExitFadeDuration(2000);
            animated.start();
        }
    }
}