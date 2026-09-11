package com.emilock.client.service;

import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.os.UserManager;
import android.telephony.TelephonyManager;
import com.emilock.client.receiver.DeviceAdminReceiver;
import com.emilock.client.ui.LockActivity;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LockService extends Service {
    private static final String SERVER_URL = "http://45.130.165.223:4000/api/client/heartbeat";
    private boolean isRunning = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRunning) {
            isRunning = true;
            new Thread(this::runHeartbeatLoop).start();
        }
        return START_STICKY;
    }

    private void runHeartbeatLoop() {
        while (isRunning) {
            try {
                sendPing();
                Thread.sleep(8000); // প্রতি ৮ সেকেন্ড পর পর চেক
            } catch (Exception ignored) {
                try { Thread.sleep(4000); } catch (Exception ignored2) {}
            }
        }
    }

    private void sendPing() {
        try {
            String imei = getImei();
            double lat = 0.0, lng = 0.0;
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm != null) {
                Location loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (loc == null) loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (loc != null) {
                    lat = loc.getLatitude();
                    lng = loc.getLongitude();
                }
            }

            URL url = new URL(SERVER_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            JSONObject req = new JSONObject();
            req.put("imei", imei);
            req.put("model", Build.MANUFACTURER + " " + Build.MODEL);
            req.put("latitude", lat);
            req.put("longitude", lng);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(req.toString().getBytes());
            }

            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                JSONObject res = new JSONObject(sb.toString());
                String status = res.optString("status", "ACTIVE");

                DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
                ComponentName admin = new ComponentName(this, DeviceAdminReceiver.class);

                // ১. লক সিগন্যাল
                if ("LOCKED".equals(status)) {
                    if (dpm != null && dpm.isDeviceOwnerApp(getPackageName())) {
                        dpm.lockNow();
                    }
                    Intent lockIntent = new Intent(this, LockActivity.class);
                    lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(lockIntent);
                }

                // ২. রিমোট ফ্যাক্টরি রিসেট (Wipe Data)
                if ("WIPE".equals(status)) {
                    if (dpm != null && dpm.isDeviceOwnerApp(getPackageName())) {
                        dpm.wipeData(0); // কোনো বাটন ছাড়া দূর থেকেই স্বয়ংক্রিয় রিসেট
                    }
                }

                // ৩. রিলিজ (মুক্ত করা)
                if ("RELEASED".equals(status)) {
                    if (dpm != null && dpm.isDeviceOwnerApp(getPackageName())) {
                        dpm.clearUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET);
                        dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT);
                        dpm.setUninstallBlocked(admin, getPackageName(), false);
                        dpm.clearDeviceOwnerApp(getPackageName());
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private String getImei() {
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                String id = tm.getDeviceId();
                if (id != null) return id;
            }
        } catch (Exception ignored) {}
        return Build.SERIAL != null ? Build.SERIAL : "867400020316612";
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
