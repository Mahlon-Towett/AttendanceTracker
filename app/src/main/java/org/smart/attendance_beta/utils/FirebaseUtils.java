package org.smart.attendance_beta.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class FirebaseUtils {
    private static final String TAG = "FirebaseUtils";

    public interface UserRoleCallback {
        void onRoleRetrieved(String role, boolean isActive);
        void onError(String error);
    }

    public interface UserDataCallback {
        void onUserDataRetrieved(DocumentSnapshot userDocument);
        void onError(String error);
    }

    /**
     * Get current user's role from Firestore
     */
    public static void getCurrentUserRole(UserRoleCallback callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            callback.onError("No authenticated user");
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(currentUser.getUid())
                .get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document.exists()) {
                                String role = document.getString("role");
                                Boolean isActive = document.getBoolean("isActive");
                                callback.onRoleRetrieved(role != null ? role : "employee",
                                        isActive != null ? isActive : true);
                            } else {
                                callback.onError("User document not found");
                            }
                        } else {
                            callback.onError(task.getException().getMessage());
                        }
                    }
                });
    }

    /**
     * Get current user's complete data from Firestore
     */
    public static void getCurrentUserData(UserDataCallback callback) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            callback.onError("No authenticated user");
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(currentUser.getUid())
                .get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document.exists()) {
                                callback.onUserDataRetrieved(document);
                            } else {
                                callback.onError("User document not found");
                            }
                        } else {
                            callback.onError(task.getException().getMessage());
                        }
                    }
                });
    }

    /**
     * Check if current user is admin
     */
    public static void isCurrentUserAdmin(UserRoleCallback callback) {
        getCurrentUserRole(new UserRoleCallback() {
            @Override
            public void onRoleRetrieved(String role, boolean isActive) {
                callback.onRoleRetrieved(role, isActive && "admin".equals(role));
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    /**
     * Get current authenticated user
     */
    public static FirebaseUser getCurrentUser() {
        return FirebaseAuth.getInstance().getCurrentUser();
    }

    /**
     * Check if user is authenticated
     */
    public static boolean isUserAuthenticated() {
        return FirebaseAuth.getInstance().getCurrentUser() != null;
    }

    /**
     * Sign out current user
     */
    public static void signOut() {
        FirebaseAuth.getInstance().signOut();
        Log.d(TAG, "User signed out");
    }
}