package ru.bluecat.android.xposed.mods.appsettings;

import com.topjohnwu.superuser.Shell;

public class SELinux {

    public static boolean isSELinuxPermissive() {
        boolean isPermissive = false;
        Shell.Result result = Shell.su("getenforce").exec();
        if (result.isSuccess() && result.getOut().get(0).equals("Permissive")) {
            isPermissive = true;
        }
        return isPermissive;
    }
}
