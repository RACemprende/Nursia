package com.fir.simulacro;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public final class CloudSyncManager {
    public interface ResultCallback {
        void onSuccess(String message);
        void onError(Exception e);
    }

    private static final String USERS_COLLECTION = "user_sync";
    private static final String STATE_DOC = "state";

    private CloudSyncManager() {
    }

    public static boolean isAvailable(Context context) {
        return FirebaseInitializer.ensureInitialized(context);
    }

    public static FirebaseAuth getAuth(Context context) {
        FirebaseInitializer.ensureInitialized(context);
        return FirebaseAuth.getInstance();
    }

    public static FirebaseFirestore getFirestore(Context context) {
        FirebaseInitializer.ensureInitialized(context);
        return FirebaseFirestore.getInstance();
    }

    public static GoogleSignInOptions buildGoogleSignInOptions(Context context) {
        String webClientId = FirebaseConfig.getWebClientId(context);
        return new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();
    }

    public static boolean hasSignedInUser(Context context) {
        return FirebaseInitializer.ensureInitialized(context) && getAuth(context).getCurrentUser() != null;
    }

    public static String getSignedInLabel(Context context) {
        FirebaseUser user = hasSignedInUser(context) ? getAuth(context).getCurrentUser() : null;
        if (user == null) {
            return "Sin sesión";
        }
        if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
            return user.getDisplayName();
        }
        if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
            return user.getEmail();
        }
        return user.getUid();
    }

    public static void signInWithEmail(Context context, String email, String password, ResultCallback callback) {
        if (!FirebaseInitializer.ensureInitialized(context)) {
            callback.onError(new IllegalStateException("Falta configurar Firebase"));
            return;
        }
        getAuth(context).signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> syncAfterAuth(context, callback))
                .addOnFailureListener(callback::onError);
    }

    public static void registerWithEmail(Context context, String email, String password, ResultCallback callback) {
        if (!FirebaseInitializer.ensureInitialized(context)) {
            callback.onError(new IllegalStateException("Falta configurar Firebase"));
            return;
        }
        getAuth(context).createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> syncAfterAuth(context, callback))
                .addOnFailureListener(callback::onError);
    }

    public static void signInWithGoogleAccount(Context context, GoogleSignInAccount account, ResultCallback callback) {
        if (!FirebaseInitializer.ensureInitialized(context)) {
            callback.onError(new IllegalStateException("Falta configurar Firebase"));
            return;
        }
        getAuth(context).signInWithCredential(GoogleAuthProvider.getCredential(account.getIdToken(), null))
                .addOnSuccessListener(authResult -> syncAfterAuth(context, callback))
                .addOnFailureListener(callback::onError);
    }

    public static void signOut(Context context) {
        if (!FirebaseInitializer.ensureInitialized(context)) {
            return;
        }
        getAuth(context).signOut();
    }

    public static void syncNow(Context context, ResultCallback callback) {
        if (!FirebaseInitializer.ensureInitialized(context)) {
            callback.onError(new IllegalStateException("Falta configurar Firebase"));
            return;
        }
        FirebaseUser user = getAuth(context).getCurrentUser();
        if (user == null) {
            callback.onError(new IllegalStateException("No has iniciado sesión"));
            return;
        }

        AppDatabaseHelper helper = new AppDatabaseHelper(context);
        long localUpdatedAt = helper.getCloudLastModifiedMs();
        DocumentReference doc = getFirestore(context)
                .collection(USERS_COLLECTION)
                .document(user.getUid())
                .collection("snapshots")
                .document(STATE_DOC);

        doc.get()
                .addOnSuccessListener(snapshot -> handleSnapshot(context, helper, doc, snapshot, localUpdatedAt, callback))
                .addOnFailureListener(callback::onError);
    }

    public static void syncSilently(Context context) {
        if (!hasSignedInUser(context)) {
            return;
        }
        syncNow(context, new ResultCallback() {
            @Override
            public void onSuccess(String message) {
            }

            @Override
            public void onError(Exception e) {
            }
        });
    }

    private static void syncAfterAuth(Context context, ResultCallback callback) {
        syncNow(context, callback);
    }

    private static void handleSnapshot(Context context, AppDatabaseHelper helper, DocumentReference doc, DocumentSnapshot snapshot, long localUpdatedAt, ResultCallback callback) {
        try {
            if (!snapshot.exists()) {
                pushLocalSnapshot(context, helper, doc, callback);
                return;
            }

            Long remoteUpdatedAt = snapshot.getLong("updatedAtMs");
            long safeRemoteUpdatedAt = remoteUpdatedAt == null ? 0L : remoteUpdatedAt;
            String remoteStateJson = snapshot.getString("stateJson");

            if (safeRemoteUpdatedAt > localUpdatedAt && remoteStateJson != null && !remoteStateJson.trim().isEmpty()) {
                JSONObject remoteState = new JSONObject(remoteStateJson);
                helper.importCloudSnapshot(remoteState, true);
                callback.onSuccess("Progreso descargado desde la nube");
                return;
            }

            pushLocalSnapshot(context, helper, doc, callback);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    private static void pushLocalSnapshot(Context context, AppDatabaseHelper helper, DocumentReference doc, ResultCallback callback) {
        try {
            JSONObject state = helper.exportCloudSnapshot();
            Map<String, Object> payload = new HashMap<>();
            payload.put("stateJson", state.toString());
            payload.put("updatedAtMs", state.optLong("updatedAtMs", System.currentTimeMillis()));
            FirebaseUser user = getAuth(context).getCurrentUser();
            if (user != null) {
                payload.put("email", user.getEmail());
                payload.put("displayName", user.getDisplayName());
            }

            doc.set(payload, SetOptions.merge())
                    .addOnSuccessListener(unused -> callback.onSuccess("Progreso sincronizado"))
                    .addOnFailureListener(callback::onError);
        } catch (Exception e) {
            callback.onError(e);
        }
    }
}
