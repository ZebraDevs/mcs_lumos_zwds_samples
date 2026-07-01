package com.zebra.zwds.developersample;


import static com.zebra.zwds.developersample.DeveloperService.stopDisplayScan;
import static com.zebra.zwds.developersample.DeveloperService.unregisterBTProximity;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.zebra.zwds.developersample.advanced.NewScanConnectActivity;
import com.zebra.zwds.developersample.advanced.NewTapConnectActivity;

import java.util.Objects;

public class HomeActivity extends AppCompatActivity implements DevServiceResponseListener {

    private static final String TAG = "HomeActivity";
    // Delay constants
    private static final long DELAY_DISCONNECT_MS = 500;

    // UI Components
    private Button btnNfcPair;
    private Button btnScanConnect;
    private Button btnProximity;
    private Button btnDisconnect;
    private Button btnDesktopModeSettings;
    private LinearLayout linConnectOptions;
    private TextView txtConnectedDeviceInfo;

    // Handler for delayed operations
    private final Handler handler = new Handler(Looper.getMainLooper());


    // State management flags
    private boolean isConnected = false;
    private boolean isDisplayScanActive = false;
    private boolean isDisplayCallbackRegistered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        Log.d(TAG, "onCreate: Initializing HomeActivity");

        initializeViews();
        setupClickListeners();
        resetState();

        registerDisplayCallback();
    }


    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart: Activity restarted");

        // Refresh connection status
        boolean success = DeveloperService.getConnectionStatus(this);
        if (!success) {
            Log.e(TAG, "Failed to get connection status on restart");
            showToast("Failed to check connection status");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart: Registering callback listener");
        MyGlobalDevServiceResponseListener.getInstance().registerListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Activity resumed");
        //startDisplayScan();

        // Check connection status to ensure UI is in sync
        handler.postDelayed(() -> {
            boolean success = DeveloperService.getConnectionStatus(this);
            if (!success) {
                Log.w(TAG, "Failed to get connection status on resume");
            }
        }, 300); // Small delay to ensure service is ready
    }


    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: Activity paused");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop: Unregistering callback listener");
        MyGlobalDevServiceResponseListener.getInstance().unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy: Cleaning up resources");
        //unregisterDisplayCallback();
        //handleDisableWirelessDisplay();
        //unregisterBluetooth();
        resetState();
        cleanupResources();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "onConfigurationChanged: Handling config change without recreate");
        // Re-check connection status to keep UI in sync after layout change
        handler.postDelayed(() -> {
            boolean success = DeveloperService.getConnectionStatus(this);
            if (!success) {
                Log.w(TAG, "Failed to get connection status after config change");
            }
        }, 300);
    }

    private void initializeViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Get app title from manifest
        TextView rightView = toolbar.findViewById(R.id.toolbar_right);
        if (rightView != null) {
            String securityStatus = SecureIntentActivity.isSecureModeEnabled()
                    ? "Service running securely"
                    : "Service running insecurely";
            rightView.setText(securityStatus);
        }

        // Initialize buttons
        btnNfcPair = findViewById(R.id.btn_Nfc_tap);
        btnScanConnect = findViewById(R.id.btn_Scan_QR);
        btnProximity = findViewById(R.id.btn_proximity);
        btnDisconnect = findViewById(R.id.btn_disconnect_device);

        // Initialize layouts and text views
        btnDesktopModeSettings = findViewById(R.id.btn_desktop_mode_settings);
        linConnectOptions = findViewById(R.id.lin_connection_options);
        txtConnectedDeviceInfo = findViewById(R.id.txt_connection_status);

    }


    /**
     * Setup click listeners for all buttons
     */
    private void setupClickListeners() {
        if (btnNfcPair != null) {
            btnNfcPair.setOnClickListener(v -> navigateToNfcPair());
        }

        if (btnScanConnect != null) {
            btnScanConnect.setOnClickListener(v -> navigateToScanConnect());
        }

        if (btnProximity != null) {
            btnProximity.setOnClickListener(v -> navigateToProximity());
        }

        if (btnDisconnect != null) {
            btnDisconnect.setOnClickListener(v -> handleDisconnect());
        }

        if (btnDesktopModeSettings != null) {
            btnDesktopModeSettings.setOnClickListener(v -> navigateToDesktopMode());
        }
    }

    // ========================= NAVIGATION METHODS =========================

    private void navigateToNfcPair() {
        Log.d(TAG, "Navigating to NFC Pair");
        Intent intent = new Intent(this, NewTapConnectActivity.class);
        startActivity(intent);
    }

    private void navigateToScanConnect() {
        Log.d(TAG, "Navigating to Scan Connect");
        Intent intent = new Intent(this, NewScanConnectActivity.class);
        startActivity(intent);
    }

    private void navigateToProximity() {
        Log.d(TAG, "Navigating to Proximity");
        Intent intent = new Intent(this, ProximityActivity.class);
        startActivity(intent);
    }

    private void navigateToDesktopMode() {
        Log.d(TAG, "Navigating to Desktop Mode");
        Intent intent = new Intent(this, DesktopModeActivity.class);
        startActivity(intent);
    }

    // ========================= CONNECTION MANAGEMENT =========================

    /**
     * Handle disconnect button click
     */
    private void handleDisconnect() {
        Log.d(TAG, "Disconnect requested");
        // Add delay before disconnect to allow scan stop to complete
        handler.postDelayed(() -> {
            stopDisplayScan();
            disconnectDevice();
            updateUIForDisconnected();
        }, DELAY_DISCONNECT_MS);
    }

    private void stopDisplayScan() {
        boolean success = DeveloperService.stopDisplayScan(this);
        if (success) {
            Log.d(TAG, "Display scan stopped successfully");
        } else {
            Log.e(TAG, "Failed to stopped display scan");
        }
    }

    /**
     * Disconnect the wireless display device
     */
    private void disconnectDevice() {
        boolean success = DeveloperService.disconnectDevice(this);
        if (!success) {
            Log.e(TAG, "Failed to disconnect device");
            showToast("Failed to disconnect device");
        } else {
            Log.d(TAG, "Device disconnect initiated");
        }
    }

    /**
     * Register display callback
     */
    private void registerDisplayCallback() {
        boolean success = DeveloperService.getWirelessDisplayCallback(this, "ON");
        if (!success) {
            Log.e(TAG, "Failed to register display callback");
        }
    }

    /**
     * Start display scan
     */
    private void startDisplayScan() {
        if (isDisplayScanActive) {
            Log.d(TAG, "Display scan already active, skipping");
            return;
        }
        boolean success = DeveloperService.startDisplayScan(this);
        if (success) {
            isDisplayScanActive = true;
            Log.d(TAG, "Display scan started successfully");
        } else {
            Log.e(TAG, "Failed to start display scan");
        }
    }
    // ========================= UI UPDATE METHODS =========================

    /**
     * Update UI for connected state
     */
    private void updateUIForConnected(@NonNull String deviceInfo) {
        if (linConnectOptions != null) {
            linConnectOptions.setVisibility(View.GONE);
        }
        if (txtConnectedDeviceInfo != null) {
            txtConnectedDeviceInfo.setVisibility(View.VISIBLE);
            btnDesktopModeSettings.setVisibility(View.VISIBLE);
            // Use String.format for better localization support
            txtConnectedDeviceInfo.setText(String.format("Connected to: %s", deviceInfo));
        }
    }

    /**
     * Update UI for disconnected state
     */
    private void updateUIForDisconnected() {
        if (linConnectOptions != null) {
            linConnectOptions.setVisibility(View.VISIBLE);
        }
        if (txtConnectedDeviceInfo != null) {
            txtConnectedDeviceInfo.setVisibility(View.GONE);
            btnDesktopModeSettings.setVisibility(View.GONE);
        }
    }

    // ========================= CALLBACK HANDLING =========================

    @Override
    public void onDevServiceResponseReceived(int reqID, int resultCode, String message, String reqType) {
        Log.d(TAG, "Callback received - Type: " + reqType + ", Result: " + resultCode + ", Message: " + message);

        if (reqType == null) {
            Log.w(TAG, "Received null request type");
            return;
        }

        switch (reqType) {
            case "INIT SERVICE":
                // FIX: Handle response from MyApp-level initializeService()
                Log.i(TAG, "Service initialized: " + message);
                break;
            case "GET STATUS":
                showToast(message);
                handleGetStatus(message);
                break;

            case "BT PROXIMITY STATE":
                handleBtProximityState(message);
                break;

            case "START DISPLAY SCAN":
                handleStartDisplayScan();
                break;

            case "STOP DISPLAY SCAN":
                handleStopDisplayScan();
                break;

            case "AVAILABLE DISPLAYS":
                handleAvailableDisplays(message);
                break;

            case "CONNECTION STATE":
                handleConnectionState(message);
                break;

            case "ENABLE WIRELESS":
                Log.i(TAG, "Wireless display enabled");
                break;

            case "CALLBACK ON":
                handleCallbackOn(message);
                break;

            case "CALLBACK OFF":
                handleCallbackOff(message);
                break;

            case "UPDATE UI":
                handleUpdateUI(message);
                break;

            case "DISCONNECT WIRELESS DISPLAY":
                Log.i(TAG, "Device disconnect response received");
                break;

            default:
                Log.w(TAG, "Unhandled request type: " + reqType);
                break;
        }
    }

    /**
     * Handle GET STATUS response
     */
    private void handleGetStatus(String message) {
        if (message == null) {
            Log.w(TAG, "Received null message in GET STATUS");
            return;
        }
        Log.d(TAG, "Get status result: " + message);
        if (message.contains("DISCONNECTED") || message.contains("UNKNOWN")) {
            updateUIForDisconnected();

        } else if (message.contains("CONNECTED")) {
            updateUIForConnected(message);

           /* // Stop display scan when connected
            if (isDisplayScanActive) {
                Log.d(TAG, "Stopping display scan - device already connected");
                stopDisplayScan();
            }*/
        }
    }

    /**
     * Handle BT PROXIMITY STATE response
     */
    private void handleBtProximityState(String message) {
        Log.i(TAG, "BT Proximity state received: " + message);

        if (message != null && message.contains("DEVICE CONNECTED")) {
            isConnected = true;
            // Update UI to hide connection buttons when proximity connects
            updateUIForConnected("Device (via Proximity)");
        } else if (message != null && message.contains("DEVICE DISCONNECTED")) {
            isConnected = false;
            updateUIForDisconnected();
        }

        DevServiceUtils.handleBtProximityState(this, message, handler);
    }

    /**
     * Handle START DISPLAY SCAN response
     */
    private void handleStartDisplayScan() {
        Log.i(TAG, "Display scan started");
        isDisplayScanActive = true;
        if (isConnected) {
            DevServiceUtils.callAvailDisplay(this, handler);
        }
    }

    /**
     * Handle STOP DISPLAY SCAN response
     */
    private void handleStopDisplayScan() {
        Log.i(TAG, "Display scan stopped");
        isDisplayScanActive = false;

        if (isConnected) {
            Log.d(TAG, "Enabling wireless display after scan stop");
            DeveloperService.enableWirelessDisplay(this, "ON");
        }
    }

    /**
     * Handle AVAILABLE DISPLAYS response
     */
    private void handleAvailableDisplays(String message) {
        DevServiceUtils.handleAvailableDisplays(this, message, handler);
    }

    /**
     * Handle CONNECTION STATE response
     */
    private void handleConnectionState(String message) {
        Log.i(TAG, "Connection state: " + message);

        if (message == null) {
            Log.w(TAG, "Received null message in CONNECTION STATE");
            return;
        }
        Log.i(TAG, "Connection state: " + message);

        if (Objects.equals(message, "Maverick CONNECTED")) {
            isConnected = true;
            Utils.saveProximityState(this, true);
            //stopDisplayScan();
            updateUIForConnected(message);
            showToast("Connected"); // FIX: Toast only on real state change
        } else if (message.contains("Maverick DISCONNECTED")) {
            isConnected = false;
            updateUIForDisconnected();
            showToast("Disconnected"); // FIX: Toast only on real state change
        } else {
            // Unknown state - log it
            Log.d(TAG, "Unknown connection state: " + message);
        }
    }

    /**
     * Handle CALLBACK ON response
     */
    private void handleCallbackOn(String message) {
        Log.i(TAG, "Display callback registered: " + message);
        isDisplayCallbackRegistered = true;
        /*if (!isDisplayScanActive) {
            startDisplayScan();
        }*/
    }

    /**
     * Handle CALLBACK OFF response
     */
    private void handleCallbackOff(String message) {
        Log.i(TAG, "Display callback unregistered: " + message);
        isDisplayCallbackRegistered = false;
        isDisplayScanActive = false;
    }

    /**
     * Handle UPDATE UI response
     */
    private void handleUpdateUI(String message) {
        if (message != null && !message.isEmpty()) {
            Log.i(TAG, "UI update received: " + message);
        }
    }

    private void handleDisableWirelessDisplay() {
        DeveloperService.enableWirelessDisplay(this, "OFF");
    }

    // ========================= CLEANUP METHODS =========================

    /**
     * Unregister display callback
     */
    private void unregisterDisplayCallback() {
        if (isDisplayCallbackRegistered) {
            boolean success = DeveloperService.getWirelessDisplayCallback(this, "OFF");
            if (!success) {
                Log.e(TAG, "Failed to unregister display callback");
            } else {
                isDisplayCallbackRegistered = false;
                isDisplayScanActive = false;
                Log.d(TAG, "Display callback unregistered successfully");
            }
        }
    }

    /**
     * Unregister bluetooth proximity
     */
    private void unregisterBluetooth() {
        boolean success = unregisterBTProximity(this);
        if (!success) {
            Log.e(TAG, "Failed to unregister bluetooth service");
        } else {
            Log.d(TAG, "Bluetooth service unregistered successfully");
        }
    }

    /**
     * Clean up handler callbacks
     */
    private void cleanupResources() {
        handler.removeCallbacksAndMessages(null);
        Log.d(TAG, "All handler callbacks removed");
    }

    /**
     * Reset all state flags
     */
    private void resetState() {
        isConnected = false;                  // FIX: was missing
        isDisplayScanActive = false;
        isDisplayCallbackRegistered = false;  // FIX: was missing
        Log.d(TAG, "State reset completed");
    }

    // ========================= UTILITY METHODS =========================

    /**
     * Show toast message with null safety
     */
    private void showToast(String message) {
        if (message != null && !isFinishing()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    public void dismissDialogCallBack() {
        // No dialog to dismiss in HomeActivity
    }

}
