package com.zebra.zwds.developersample;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class DesktopModeActivity extends AppCompatActivity implements DevServiceResponseListener {
    private static final String TAG = "DesktopModeActivity";

    private static final long DELAY_DESKTOP_MODE = 500L;
    private static final long DELAY_START_SCAN = 1000L;
    private static final long DELAY_GET_CALLBACK = 1000L;
    private static final long DELAY_GET_AVAILABLE = 2000L;
    private static final long DELAY_CONNECT_DEVICE = 1000L;
    private static final long DELAY_RETRY_SCAN = 2000L;
    private static final int MAX_SCAN_RETRY_ATTEMPTS = 3;

    // Pending mode value — stored when button is clicked, consumed after disconnect
    private String pendingMode = null;
    private String pendingModeName = null;

    private boolean isModeChangeInProgress = false;
    private boolean isConnectionInProgress = false;
    private int scanRetryCount = 0;

    private Runnable availableDisplaysRunnable;
    private Runnable connectDeviceRunnable;
    private Runnable retryScanRunnable;

    private Button btn_desktop_mode;
    private Button btn_mirror_mode;
    private ProgressBar progressModeChange;
    private TextView tvProgressLabel;

    private final Handler handler = new Handler(Looper.getMainLooper());

    // New fields
    private int     activeFlowId                   = 0;
    private boolean isDesktopModeRequestSent       = false;
    private boolean isCallbackRegistrationInFlight = false;
    private boolean isAvailableRequestInFlight     = false;
    private boolean isRetryScheduled                = false;

    // ========================= LIFECYCLE =========================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_desktop_mode);
        initView();
        onClickListener();
        refreshButtonsForCurrentMode();
    }

    @Override
    protected void onStart() {
        super.onStart();
        MyGlobalDevServiceResponseListener.getInstance().registerListener(this);
        Log.d(TAG, "DesktopModeActivity registered for callbacks");
        refreshButtonsForCurrentMode();
    }

    @Override
    protected void onStop() {
        super.onStop();
        MyGlobalDevServiceResponseListener.getInstance().unregisterListener(this);
        Log.d(TAG, "DesktopModeActivity unregistered from callbacks");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelAllPendingCallbacks();
        resetState();
        Log.d(TAG, "DesktopModeActivity onDestroy");
    }

    // ========================= VIEW INIT =========================

    private void initView() {
        btn_desktop_mode = findViewById(R.id.btn_desktop_mode);
        btn_mirror_mode = findViewById(R.id.btn_mirror_mode);
        progressModeChange = findViewById(R.id.progress_mode_change);
        tvProgressLabel = findViewById(R.id.tv_progress_label);
    }

    private void onClickListener() {
        btn_desktop_mode.setOnClickListener(v -> handleModeChange("ON", "Desktop"));
        btn_mirror_mode.setOnClickListener(v -> handleModeChange("OFF", "Mirror"));
    }

    // ========================= FLOW ENTRY =========================

    private int beginNewFlow() {
        activeFlowId++;
        return activeFlowId;
    }

    /**
     * STEP 1 – Button click
     * Store the target mode and immediately trigger Disconnect.
     */
    // 3) Replace handleModeChange(...) with this version
    private void handleModeChange(String mode, String modeName) {
        if (isModeChangeInProgress) {
            Log.w(TAG, "Mode change already in progress, ignoring " + modeName + " request");
            showToast("Mode change already in progress");
            return;
        }

        String currentMode = getCurrentModeFromSettings();
        if (mode.equals(currentMode)) {
            Log.w(TAG, "Already in " + modeName + " mode, ignoring click");
            showToast("Already in " + modeName + " mode");
            refreshButtonsForCurrentMode();
            return;
        }

        beginNewFlow();
        cancelAllPendingCallbacks();
        isDesktopModeRequestSent = false;
        isCallbackRegistrationInFlight = false;
        isAvailableRequestInFlight = false;
        isRetryScheduled = false;
        isConnectionInProgress = false;
        scanRetryCount = 0;

        Log.d(TAG, "handleModeChange: " + modeName + " (mode=" + mode + ")");
        pendingMode = mode;
        pendingModeName = modeName;
        isModeChangeInProgress = true;
        disableButtons();
        showProgress("Disconnecting...");

        boolean success = DeveloperService.disconnectDevice(this);
        if (!success) {
            Log.e(TAG, "Failed to send disconnect request");
            showToast("Failed to disconnect device");
            resetState();
        } else {
            Log.i(TAG, "Disconnect request sent");
        }
    }

    // ========================= FLOW STEPS (callbacks) =========================

    /**
     * STEP 2 – After Disconnect → set Desktop/Mirror Mode
     */
    private void setDesktopMode() {
        if (!isModeChangeInProgress) {
            Log.w(TAG, "setDesktopMode ignored: no mode change in progress");
            return;
        }
        if (isDesktopModeRequestSent) {
            Log.d(TAG, "setDesktopMode ignored: request already sent");
            return;
        }
        if (pendingMode == null) {
            Log.e(TAG, "setDesktopMode: pendingMode is null");
            resetState();
            return;
        }

        isDesktopModeRequestSent = true;
        Log.d(TAG, "Scheduling desktopModeCheck in " + DELAY_DESKTOP_MODE + "ms (mode=" + pendingMode + ")");
        showProgress("Applying " + pendingModeName + " mode...");

        boolean success = DeveloperService.desktopModeCheck(this, pendingMode);
        if (!success) {
            Log.e(TAG, "Failed to set " + pendingModeName + " mode");
            showToast("Failed to set " + pendingModeName + " mode");
            resetState();
        } else {
            Log.i(TAG, pendingModeName + " mode request sent");
        }
    }

    /**
     * STEP 3 – After Desktop Mode ACK → Start Display Scan
     */
    private void startScan() {
        Log.d(TAG, "Scheduling startDisplayScan in " + DELAY_START_SCAN + "ms");
        showProgress("Starting scan…");

        boolean success = DeveloperService.startDisplayScan(this);
        if (!success) {
            Log.e(TAG, "Failed to start display scan");
            showToast("Failed to start display scan");
            resetState();
        } else {
            Log.i(TAG, "Display scan started");
        }

    }

    /**
     * STEP 4 – After Start Scan ACK → Register Wireless Display Callback
     */
    private void registerWirelessDisplayCallback() {
        if (!isModeChangeInProgress) {
            Log.d(TAG, "registerWirelessDisplayCallback ignored: no mode change in progress");
            return;
        }
        if (isCallbackRegistrationInFlight) {
            Log.d(TAG, "Callback registration already in-flight, skipping");
            return;
        }

        isCallbackRegistrationInFlight = true;
        Log.d(TAG, "Scheduling getWirelessDisplayCallback in " + DELAY_GET_CALLBACK + "ms");
        showProgress("Registering display callback...");

        boolean success = DeveloperService.getWirelessDisplayCallback(this, "ON");
        if (!success) {
            isCallbackRegistrationInFlight = false;
            Log.e(TAG, "Failed to register wireless display callback");
            showToast("Failed to register display callback");
            resetState();
        } else {
            Log.i(TAG, "Wireless display callback registered");
        }
    }

    /**
     * STEP 5 – After CALLBACK ON → Get Available Displays
     */
    private void requestAvailableDisplays() {
        if (!isModeChangeInProgress) {
            Log.d(TAG, "requestAvailableDisplays ignored: no mode change in progress");
            return;
        }
        if (isAvailableRequestInFlight) {
            Log.d(TAG, "Available displays request already in-flight, skipping");
            return;
        }

        isAvailableRequestInFlight = true;
        final int flowId = activeFlowId;

        if (availableDisplaysRunnable != null) {
            handler.removeCallbacks(availableDisplaysRunnable);
        }

        Log.d(TAG, "Scheduling getAvailableDevices in " + DELAY_GET_AVAILABLE + "ms");
        showProgress("Searching for devices...");

        availableDisplaysRunnable = () -> {
            if (flowId != activeFlowId || !isModeChangeInProgress) {
                return;
            }

            boolean success = DeveloperService.getAvailableDevices(this);
            if (!success) {
                isAvailableRequestInFlight = false;
                Log.e(TAG, "Failed to get available displays");
                showToast("Failed to get available displays");
                handleScanRetry();
            } else {
                Log.i(TAG, "Available displays request sent");
            }
        };
        handler.postDelayed(availableDisplaysRunnable, DELAY_GET_AVAILABLE);
    }

    /**
     * STEP 6 – After Available Displays → Connect to target device, then stop scan
     */
    private void connectToDevice(String deviceAddress) {
        if (!isModeChangeInProgress) {
            Log.d(TAG, "connectToDevice ignored: no mode change in progress");
            return;
        }
        if (isConnectionInProgress) {
            Log.w(TAG, "Connection already in progress, skipping");
            return;
        }
        if (deviceAddress == null || deviceAddress.isEmpty()) {
            Log.e(TAG, "No device address provided");
            showToast("No device configured for connection");
            resetState();
            return;
        }

        final int flowId = activeFlowId;
        if (connectDeviceRunnable != null) {
            handler.removeCallbacks(connectDeviceRunnable);
        }

        Log.d(TAG, "Scheduling connectDevice('" + deviceAddress + "') in " + DELAY_CONNECT_DEVICE + "ms");
        showProgress("Connecting...");

        connectDeviceRunnable = () -> {
            if (flowId != activeFlowId || !isModeChangeInProgress) {
                return;
            }

            boolean success = DeveloperService.connectDevice(this, deviceAddress);
            if (!success) {
                Log.e(TAG, "Failed to initiate connection to " + deviceAddress);
                showToast("Failed to connect to device");
                resetState();
            } else {
                Log.i(TAG, "Connection request sent for " + deviceAddress);
                isConnectionInProgress = true;
            }
        };
        handler.postDelayed(connectDeviceRunnable, DELAY_CONNECT_DEVICE);
    }

    // ========================= RETRY LOGIC =========================

    private void handleScanRetry() {
        if (!isModeChangeInProgress) {
            Log.d(TAG, "handleScanRetry ignored: no mode change in progress");
            return;
        }
        if (isRetryScheduled) {
            Log.d(TAG, "Retry already scheduled, skipping duplicate");
            return;
        }
        if (scanRetryCount >= MAX_SCAN_RETRY_ATTEMPTS) {
            Log.e(TAG, "Max scan retry attempts (" + MAX_SCAN_RETRY_ATTEMPTS + ") reached");
            showToast("Failed to find device after " + MAX_SCAN_RETRY_ATTEMPTS + " attempts");
            resetState();
            return;
        }

        scanRetryCount++;
        isRetryScheduled = true;
        final int flowId = activeFlowId;

        if (retryScanRunnable != null) {
            handler.removeCallbacks(retryScanRunnable);
        }

        Log.d(TAG, "Scheduling scan retry " + scanRetryCount + "/" + MAX_SCAN_RETRY_ATTEMPTS
                + " in " + DELAY_RETRY_SCAN + "ms");

        retryScanRunnable = () -> {
            isRetryScheduled = false;

            if (flowId != activeFlowId || !isModeChangeInProgress) {
                return;
            }

            Log.d(TAG, "Executing scan retry attempt " + scanRetryCount);
            boolean success = DeveloperService.startDisplayScan(this);
            if (!success) {
                Log.e(TAG, "Retry scan attempt " + scanRetryCount + " failed");
                handleScanRetry();
            } else {
                Log.i(TAG, "Retry scan attempt " + scanRetryCount + " started");
                isCallbackRegistrationInFlight = false;
                registerWirelessDisplayCallback();
            }
        };
        handler.postDelayed(retryScanRunnable, DELAY_RETRY_SCAN);
    }

    // ========================= CALLBACK HANDLERS =========================

    @Override
    public void onDevServiceResponseReceived(int reqID, int resultCode, String message, String reqType) {
        if (reqType == null) {
            Log.w(TAG, "Received null request type");
            return;
        }
        Log.d(TAG, "Callback → type=" + reqType + "  result=" + resultCode
                + "  msg=" + (message != null ? message : "null"));

        switch (reqType.toUpperCase()) {

            case "DISCONNECT WIRELESS DISPLAY":
                handleDisconnectResponse(resultCode, message);
                break;

            case "DESKTOP MODE":
                handleDesktopModeResponse(resultCode, message);
                break;

            case "START DISPLAY SCAN":
                handleStartDisplayScanResponse(resultCode, message);
                break;

            case "CALLBACK ON":
                handleCallbackOnResponse(resultCode, message);
                break;

            case "AVAILABLE DISPLAYS":
                handleAvailableDisplaysResponse(resultCode, message);
                break;

            case "CONNECT WIRELESS DISPLAY":
                handleConnectResponse(resultCode, message);
                break;

            case "STOP DISPLAY SCAN":
                Log.d(TAG, "Display scan stopped");
                break;

            case "CONNECTION STATE":
                handleConnectionStateResponse(resultCode, message);
                break;

            case "UPDATE UI":
                handleUpdateUIResponse(resultCode, message);
                break;

            default:
                Log.w(TAG, "Unhandled request type: " + reqType);
                break;
        }
    }

    /**
     * STEP 2 trigger
     */
    private void handleDisconnectResponse(int resultCode, String message) {
        Log.i(TAG, "Disconnect response (result=" + resultCode + "): " + message);
    }

    /**
     * STEP 3 trigger
     */
    private void handleDesktopModeResponse(int resultCode, String message) {
        if (!isModeChangeInProgress) {
            Log.d(TAG, "Ignoring DESKTOP MODE callback: no mode change in progress");
            return;
        }
        Log.i(TAG, "Desktop mode response (result=" + resultCode + ")");

        if (resultCode == 0) {
            Log.e(TAG, "Desktop mode apply failed: " + message);
            showToast("Failed to apply " + pendingModeName + " mode");
            resetState();
            return;
        }
        runOnUiThread(this::refreshButtonsForCurrentMode);

        startScan();
    }

    /**
     * STEP 4 trigger
     */
    private void handleStartDisplayScanResponse(int resultCode, String message) {
        if (!isModeChangeInProgress) return;
        Log.i(TAG, "Start display scan response (result=" + resultCode + ")");
        if (resultCode == 0) {
            handleScanRetry();
            return;
        }
        registerWirelessDisplayCallback();
    }

    /**
     * STEP 5 trigger
     */
    private void handleCallbackOnResponse(int resultCode, String message) {
        if (!isModeChangeInProgress) return;
        isCallbackRegistrationInFlight = false;
        Log.i(TAG, "Callback ON response (result=" + resultCode + ")");

        if (resultCode == 0) {
            handleScanRetry();
            return;
        }

        requestAvailableDisplays();
    }

    /**
     * STEP 6 trigger
     */
    private void handleAvailableDisplaysResponse(int resultCode, String message) {
        if (!isModeChangeInProgress) return;
        isAvailableRequestInFlight = false;

        if (message == null || message.isEmpty()) {
            Log.w(TAG, "Empty available displays list");
            showToast("No devices found");
            handleScanRetry();
            return;
        }

        Log.i(TAG, "Available displays: " + message);

        String targetDockName = Utils.getSavedTargetDockName(this);
        if (targetDockName == null || targetDockName.isEmpty()) {
            Log.e(TAG, "No target dock configured");
            showToast("No device configured");
            resetState();
            return;
        }

        String displayAddress = Utils.getDisplayAddress(targetDockName, message);
        if (displayAddress == null || "ERROR".equals(displayAddress)) {
            Log.w(TAG, "Target device not available (address=" + displayAddress + ")");
            showToast("Device not available");
            handleScanRetry();
        } else {
            Log.i(TAG, "Target device found: " + displayAddress);
            scanRetryCount = 0;
            connectToDevice(displayAddress);
        }
    }

    private void handleConnectResponse(int resultCode, String message) {
        if (resultCode == 0) {
            Log.e(TAG, "Connection initiation failed (result=" + resultCode + ")");
            showToast("Failed to connect");
            resetState();
        } else {
            Log.i(TAG, "Connection establishing… (result=" + resultCode + ")");
        }
    }

    private void handleConnectionStateResponse(int resultCode, String message) {
        if (message == null) {
            Log.w(TAG, "Null connection state message");
            return;
        }
        Log.i(TAG, "Connection state: " + message);

        if (message.equals("Maverick CONNECTED")) {
            if (!isModeChangeInProgress) return;
            Log.i(TAG, "Device connected - flow complete");
            showToast("Device connected successfully");
            resetState();
        } else if (message.contains("Maverick DISCONNECTED")) {
            if (!isModeChangeInProgress) {
                Log.d(TAG, "Ignoring DISCONNECTED: no mode change in progress");
                return;
            }
            Log.d(TAG, "Device disconnected (modeChangeInProgress=true)");
            setDesktopMode();
        }
    }

    private void handleUpdateUIResponse(int resultCode, String message) {
        if (message == null || message.isEmpty()) return;
        Log.i(TAG, "UPDATE UI: " + message);

        try {
            if (message.contains("\"isAvailable\":true") && message.contains("\"canConnect\":true")) {
                Log.d(TAG, "Target device is now available");
            } else if (message.contains("\"isAvailable\":false") || message.contains("\"canConnect\":false")) {
                Log.d(TAG, "Target device is now unavailable");
                if (isConnectionInProgress) {
                    Log.w(TAG, "Device became unavailable during connection attempt");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing UPDATE UI message: " + e.getMessage());
        }
    }

    // ========================= HELPERS =========================

    private void cancelAllPendingCallbacks() {
        if (availableDisplaysRunnable != null) handler.removeCallbacks(availableDisplaysRunnable);
        if (connectDeviceRunnable != null) handler.removeCallbacks(connectDeviceRunnable);
        if (retryScanRunnable != null) handler.removeCallbacks(retryScanRunnable);

        availableDisplaysRunnable = null;
        connectDeviceRunnable = null;
        retryScanRunnable = null;
        Log.d(TAG, "All pending callbacks cancelled");
    }

    private void resetState() {
        cancelAllPendingCallbacks();

        pendingMode = null;
        pendingModeName = null;
        isModeChangeInProgress = false;
        isConnectionInProgress = false;
        isDesktopModeRequestSent = false;
        isCallbackRegistrationInFlight = false;
        isAvailableRequestInFlight = false;
        isRetryScheduled = false;
        scanRetryCount = 0;

        enableButtons();
        hideProgress();
        refreshButtonsForCurrentMode();
        Log.d(TAG, "State reset completed");
    }

    private void disableButtons() {
        if (btn_desktop_mode != null) btn_desktop_mode.setEnabled(false);
        if (btn_mirror_mode != null) btn_mirror_mode.setEnabled(false);
    }

    private void enableButtons() {
        if (btn_desktop_mode != null) btn_desktop_mode.setEnabled(true);
        if (btn_mirror_mode != null) btn_mirror_mode.setEnabled(true);
    }

    private void showProgress(String label) {
        if (progressModeChange != null) progressModeChange.setVisibility(View.VISIBLE);
        if (tvProgressLabel != null) {
            tvProgressLabel.setText(label != null ? label : "");
            tvProgressLabel.setVisibility(View.VISIBLE);
        }
    }

    private void hideProgress() {
        if (progressModeChange != null) progressModeChange.setVisibility(View.GONE);
        if (tvProgressLabel != null) tvProgressLabel.setVisibility(View.GONE);
    }

    private void showToast(String message) {
        if (message != null && !isFinishing() && !isDestroyed()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void dismissDialogCallBack() {
        // no-op
    }

    private String getCurrentModeFromSettings() {
        boolean mode = Settings.Global.getInt(
                getContentResolver(),
                "force_desktop_mode_on_external_displays",
                -1 /*default*/
        ) == 1;
        return mode ? "ON" : "OFF";
    }

    /**
     * Disables the button corresponding to the mode we are already in, * and enables the other one. Called on resume and after every flow completes.
     */
    private void refreshButtonsForCurrentMode() {
        if (isModeChangeInProgress) return; // don't override progress-state disabling

        String current = getCurrentModeFromSettings();
        boolean inDesktop = "ON".equals(current);
        boolean inMirror = "OFF".equals(current);

        applyModeButtonStyle(btn_desktop_mode, inDesktop);
        applyModeButtonStyle(btn_mirror_mode, inMirror);

        Log.d(TAG, "Buttons refreshed for current mode: " + current);

    }

    /**
     * Highlights the button if it represents the currently active mode and disables it.
     */
    private void applyModeButtonStyle(Button btn, boolean isActive) {
        if (btn == null) return;
        btn.setEnabled(!isActive);
        Drawable bgColor = ContextCompat.getDrawable(this,
                isActive ? R.drawable.button_without_border : R.drawable.button_with_border);
        int textColor = ContextCompat.getColor(this,
                isActive ? R.color.mode_active_text : R.color.mode_inactive_text);
        btn.setBackground(bgColor);
        btn.setTextColor(textColor);
        // Keep full opacity even when disabled so the highlight stays visible
        btn.setAlpha(1f);
    }
}
