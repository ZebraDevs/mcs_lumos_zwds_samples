package com.zebra.zwds.developersample;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.zebra.zwds.developersample.advanced.NewHomeActivity;

public class SecureIntentActivity extends AppCompatActivity {

    private static final String TAG = "SecureIntentActivity";
    public static boolean hasSecureModeEnabled = false;
    public static final String DEVELOPER_SERVICE_IDENTIFIER = "delegation-zebra-zwds-api-intent-secure";

    private AlertDialog alertDialog; // store reference to prevent window leak

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Utils.acquireToken(DEVELOPER_SERVICE_IDENTIFIER, this);
        alertDialog = new AlertDialog.Builder(this)
                .setTitle("Secure Intent")
                .setMessage("Would you like to enable Secure Intent?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    setSecureModeEnabled(true);
                    serviceInitialze();
                    startActivity(new Intent(this, NewHomeActivity.class));
                    finish();
                })
                .setNegativeButton("No", (dialog, which) -> {
                    setSecureModeEnabled(false);
                    serviceInitialze();
                    startActivity(new Intent(this, NewHomeActivity.class));
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void serviceInitialze() {
        DevResponseReceiver.setGlobalListener(MyGlobalDevServiceResponseListener.getInstance());

        // Initialize the developer service once for the entire application
        boolean success = DeveloperService.initializeService(this);
        if (!success) {
            Log.e(TAG, "Failed to initialize developer service");
        } else {
            Log.d(TAG, "Developer service initialized successfully");
        }
    }

    @Override
    protected void onDestroy() {
        if (alertDialog != null && alertDialog.isShowing()) {
            alertDialog.dismiss();  // prevent WindowLeaked when activity is destroyed
        }
        super.onDestroy();
    }

    public static void setSecureModeEnabled(boolean value) {
        hasSecureModeEnabled = value;
    }

    public static boolean isSecureModeEnabled() {
        return hasSecureModeEnabled;
    }
}
