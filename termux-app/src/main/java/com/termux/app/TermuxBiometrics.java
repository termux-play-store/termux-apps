package com.termux.app;


import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.CancellationSignal;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.concurrent.Executor;

/**
 * WIP: See <a href="https://developer.android.com/identity/sign-in/biometric-auth">Show a biometric authentication dialog</a>.
 */
public final class TermuxBiometrics {
    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private final TermuxActivity activity;

    public TermuxBiometrics(TermuxActivity context) {
        this.activity = context;
        var biometricManager = context.getSystemService(BiometricManager.class);
        var authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
        switch (biometricManager.canAuthenticate(authenticators)) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                Log.d("MY_APP_TAG", "App can authenticate using biometrics.");
                break;
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                Log.e("MY_APP_TAG", "No biometric features available on this device.");
                break;
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                Log.e("MY_APP_TAG", "Biometric features are currently unavailable.");
                break;
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                // Prompts the user to create credentials that your app accepts.
                var enrollIntent = new Intent(Settings.ACTION_BIOMETRIC_ENROLL);
                enrollIntent.putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, authenticators);
                context.startActivityForResult(enrollIntent, 132);
                break;
            case BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED:
                break;
        }

    }

    void showPrompt() {
        executor = ContextCompat.getMainExecutor(activity);
        var authenticationCallback = new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Toast.makeText(activity, "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                Toast.makeText(activity, "Authentication succeeded!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(activity, "Authentication failed", Toast.LENGTH_SHORT).show();
            }
        };

        biometricPrompt = new BiometricPrompt.Builder(activity)
            .setTitle("Biometric login for my app")
            .setSubtitle("Log in using your biometric credential")
            .setNegativeButton("Cancel", executor, (dialog, which) -> Log.e(TermuxConstants.LOG_TAG, "Biometrics authentication aborted"))
            .build();

        var cancellationSignal = new CancellationSignal();
        cancellationSignal.setOnCancelListener(() -> Log.e(TermuxConstants.LOG_TAG, "Biometrics authentication cancelled"));

        biometricPrompt.authenticate(cancellationSignal, executor, authenticationCallback);
    }

}
