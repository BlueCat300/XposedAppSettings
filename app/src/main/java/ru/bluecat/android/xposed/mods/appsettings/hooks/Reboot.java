package ru.bluecat.android.xposed.mods.appsettings.hooks;

import android.os.Build;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import ru.bluecat.android.xposed.mods.appsettings.Common;

public class Reboot {

    // https://github.com/Firefds/FirefdsKit
    static void grantRebootPermission(XC_MethodHook.MethodHookParam param, String pkgName) {
        try {
            Object packageSettings;
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                packageSettings = XposedHelpers.getObjectField(param.args[0], "mExtras");
            } else {
                Object mPackageManagerInt = XposedHelpers.getObjectField(param.thisObject,"mPackageManagerInt");
                packageSettings = XposedHelpers.callMethod(mPackageManagerInt, "getPackageSetting", pkgName);
            }

            Object permissionsState = XposedHelpers.callMethod(packageSettings, "getPermissionsState");
            Object mSettings = XposedHelpers.getObjectField(param.thisObject, "mSettings");
            Object mPermissions = XposedHelpers.getObjectField(mSettings, "mPermissions");
            if (!(Boolean) XposedHelpers.callMethod(permissionsState, "hasInstallPermission", Common.REBOOT)) {
                Object pAccess = XposedHelpers.callMethod(mPermissions, "get", Common.REBOOT);
                XposedHelpers.callMethod(permissionsState, "grantInstallPermission", pAccess);
            }
        } catch (Throwable e) {
            XposedBridge.log(e);
        }
    }

    // https://github.com/Firefds/FirefdsKit
    static void grantRebootPermissionS(Object param, Object pkgId) {
        try {
            Object mRegistry = XposedHelpers.getObjectField(param, "mRegistry");
            Object bp = XposedHelpers.callMethod(mRegistry, "getPermission", Common.REBOOT);
            Object uidState = XposedHelpers.callMethod(param, "getUidStateLocked", pkgId, 0);
            XposedHelpers.callMethod(uidState, "grantPermission", bp);
        } catch (Throwable e) {
            XposedBridge.log(e);
        }
    }
}
