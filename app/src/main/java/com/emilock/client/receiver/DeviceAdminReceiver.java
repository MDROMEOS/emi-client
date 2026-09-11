package com.emilock.client.receiver;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;
import com.emilock.client.service.LockService;

public class DeviceAdminReceiver extends android.app.admin.DeviceAdminReceiver {
    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(context, DeviceAdminReceiver.class);

        if (dpm.isDeviceOwnerApp(context.getPackageName())) {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET);
            dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT);
            dpm.setUninstallBlocked(admin, context.getPackageName(), true);
            dpm.setLockTaskPackages(admin, new String[]{context.getPackageName()});
        }

        context.startService(new Intent(context, LockService.class));
    }

    @Override
    public void onEnabled(Context context, Intent intent) {
        context.startService(new Intent(context, LockService.class));
    }
}
