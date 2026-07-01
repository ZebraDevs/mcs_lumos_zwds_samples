package com.zebra.zwds.developersample;

import static com.zebra.zwds.developersample.DeveloperService.unregisterBTProximity;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

/**
 * Application class to initialize global components
 */
public class MyApp extends Application {

    private static final String TAG = "MyApp";

    private final Handler handler = new Handler(Looper.getMainLooper());

    boolean canCall = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Application onCreate - Setting up global listener");
        // Set the global listener once for the entire application

        // Example usage of BT PROXIMITY STATE handling
        MyGlobalDevServiceResponseListener.getInstance().registerListener(new DevServiceResponseListener() {
            @Override
            public void onDevServiceResponseReceived(int reqID, int resultCode, String message, String reqType) {
                if ("BT PROXIMITY STATE".equals(reqType)) {
                    DevServiceUtils.handleBtProximityState(MyApp.this, message, handler);
                    canCall = true;
                }else if("INIT SERVICE".equals(reqType)){
                    Toast.makeText(MyApp.this, "Developer Service " + message, Toast.LENGTH_SHORT).show();
                }
                if (canCall) {
                    if ("START DISPLAY SCAN".equals(reqType)) {
                        DevServiceUtils.callAvailDisplay(MyApp.this, handler);
                    } else if ("AVAILABLE DISPLAYS".equals(reqType)) {
                        DevServiceUtils.handleAvailableDisplays(MyApp.this, message, handler);
                    }
                }
            }
            @Override
            public void dismissDialogCallBack() {

            }
        });
    }

    @Override
    public void onTerminate() {
        unregisterDisplayCallback();
        handleDisableWirelessDisplay();
        unregisterBluetooth();
        Log.d(TAG, "Application onTerminate - Deinitializing developer service");
        boolean success = DeveloperService.deinitializeService(this);
        if (!success) {
            Log.e(TAG, "Failed to deinitialize developer service on termination");
        } else {
            Log.d(TAG, "Developer service deinitialized successfully");
        }

        super.onTerminate();
    }


    /**
     * Unregister display callback
     */
    private void unregisterDisplayCallback() {
        boolean success = DeveloperService.getWirelessDisplayCallback(this, "OFF");
        if (!success) {
            Log.e(TAG, "Failed to unregister display callback");
        } else {
            Log.d(TAG, "Display callback unregistered successfully");
        }
    }

    private void handleDisableWirelessDisplay() {
        DeveloperService.enableWirelessDisplay(this, "OFF");
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

}
