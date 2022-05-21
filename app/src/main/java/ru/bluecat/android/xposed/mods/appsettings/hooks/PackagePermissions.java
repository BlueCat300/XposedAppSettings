package ru.bluecat.android.xposed.mods.appsettings.hooks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import ru.bluecat.android.xposed.mods.appsettings.Common;

public class PackagePermissions extends BroadcastReceiver {
	private final Object pmSvc;
	private final Object mPermissionCallback;
	private final Map<String, Object> mPackages;
	private static Object mSettings;
	private static Object permissionSvc;

	@SuppressWarnings("unchecked")
	public PackagePermissions(Object pmSvc) {
		this.pmSvc = pmSvc;
		Object svc = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q ? pmSvc : permissionSvc;
		String mCallback = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q ?
				"mPermissionCallback" : "mDefaultPermissionCallback";
		this.mPermissionCallback = XposedHelpers.getObjectField(svc, mCallback);
		this.mPackages = (Map<String, Object>) XposedHelpers.getObjectField(pmSvc, "mPackages");
		mSettings = XposedHelpers.getObjectField(pmSvc, "mSettings");
	}

	public static void init(XC_LoadPackage.LoadPackageParam lpparam, XSharedPreferences prefs) {
		/* Hook to the PackageManager service in order to
		 * - Listen for broadcasts to apply new settings and restart the app
		 * - Intercept the permission granting function to remove disabled permissions
		 */
		try {
			// if the user has disabled certain permissions for an app, do as if the hadn't requested them
			// you requested those internet permissions? I didn't read that, sorry
			// restore requested permissions if they were modified
			XC_MethodHook hookGrantPermissions = new XC_MethodHook() {
				@SuppressWarnings("unchecked")
				@Override
				protected void beforeHookedMethod(MethodHookParam param) {
					String pkgName = (String) XposedHelpers.getObjectField(param.args[0], "packageName");
					if (!Core.isActive(prefs, pkgName) || !prefs.getBoolean(pkgName + Common.PREF_REVOKEPERMS, false))
						return;

					Set<String> disabledPermissions = prefs.getStringSet(pkgName + Common.PREF_REVOKELIST, null);
					if (disabledPermissions == null || disabledPermissions.isEmpty())
						return;

					ArrayList<String> origRequestedPermissions = (ArrayList<String>) XposedHelpers.getObjectField(param.args[0], "requestedPermissions");
					param.setObjectExtra("orig_requested_permissions", origRequestedPermissions);

					ArrayList<String> newRequestedPermissions = new ArrayList<>(origRequestedPermissions.size());
					for (String perm : origRequestedPermissions) {
						if (!disabledPermissions.contains(perm))
							newRequestedPermissions.add(perm);
						else
							// you requested those internet permissions? I didn't read that, sorry
							Log.w(Common.TAG, "Not granting permission " + perm
									+ " to package " + pkgName
									+ " because you think it should not have it");
					}

					XposedHelpers.setObjectField(param.args[0], "requestedPermissions", newRequestedPermissions);
				}

				@SuppressWarnings("unchecked")
				@Override
				protected void afterHookedMethod(MethodHookParam param) {
					permissionSvc = param.thisObject;

					// restore requested permissions if they were modified
					ArrayList<String> origRequestedPermissions = (ArrayList<String>) param.getObjectExtra("orig_requested_permissions");
					if (origRequestedPermissions != null) {
						XposedHelpers.setObjectField(param.args[0], "requestedPermissions", origRequestedPermissions);
					}

					String pkgName = (String) XposedHelpers.getObjectField(param.args[0], "packageName");
					if (Common.MY_PACKAGE_NAME.equals(pkgName)) {
						if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
							Reboot.grantRebootPermission(param, pkgName);
						} else {
							Reboot.grantRebootPermissionS(param.thisObject, param.args[0]);
						}
					}
				}
			};

			ClassLoader classLoader = lpparam.classLoader;
			Class<?> clsPermManagerService = XposedHelpers.findClass(
					"com.android.server.pm.permission.PermissionManagerService", classLoader);
			Class<?> clsPkgManagerService = XposedHelpers.findClass(
					"com.android.server.pm.PackageManagerService", classLoader);

			String targetMethod;
			String param1;
			String param4;
			if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1) {
				targetMethod = "grantPermissionsLPw";
				param1 = "android.content.pm.PackageParser$Package";
				param4 = null;
			} else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
				targetMethod = "grantPermissions";
				param1 = "android.content.pm.PackageParser$Package";
				param4 = "com.android.server.pm.permission.PermissionManagerInternal$PermissionCallback";
			} else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
				targetMethod = "restorePermissionState";
				param1 = "android.content.pm.PackageParser$Package";
				param4 = "com.android.server.pm.permission.PermissionManagerServiceInternal$PermissionCallback";
			} else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
				targetMethod = "restorePermissionState";
				param1 = "com.android.server.pm.parsing.pkg.AndroidPackage";
				param4 = "com.android.server.pm.permission.PermissionManagerServiceInternal$PermissionCallback";
			} else {
				targetMethod = "restorePermissionState";
				param1 = "com.android.server.pm.parsing.pkg.AndroidPackage";
				param4 = "com.android.server.pm.permission.PermissionManagerService$PermissionCallback";
			}

			if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1) {
				XposedHelpers.findAndHookMethod(clsPkgManagerService, targetMethod, param1,
						boolean.class, String.class, hookGrantPermissions);
			} else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
				XposedHelpers.findAndHookMethod(clsPermManagerService, targetMethod,
						param1, boolean.class, String.class, param4, hookGrantPermissions);
			} else {
				XposedHelpers.findAndHookMethod(clsPermManagerService, targetMethod,
						param1, boolean.class, String.class, param4, int.class, hookGrantPermissions);
			}

			// Listen for broadcasts from the Settings part of the mod, so it's applied immediately
			XposedHelpers.findAndHookMethod(clsPkgManagerService, "systemReady", new XC_MethodHook() {
				@Override
				protected void afterHookedMethod(MethodHookParam param) {
					Context mContext = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
					mContext.registerReceiver(new PackagePermissions(param.thisObject),
							new IntentFilter(Common.MY_PACKAGE_NAME + ".UPDATE_PERMISSIONS"),
							Common.MY_PACKAGE_NAME + ".BROADCAST_PERMISSION",
							null);
				}
			});
		} catch (Throwable e) {
			XposedBridge.log(e);
		}
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		try {
			// The app broadcasted a request to update settings for a running app

			// Validate the action being requested
			if (intent.getExtras() ==  null || !Common.ACTION_PERMISSIONS.equals(intent.getExtras().getString("action")))
				return;

			String pkgName = intent.getExtras().getString("Package");
			boolean killApp = intent.getExtras().getBoolean("Kill", false);

			Object pkgInfo;
			synchronized (mPackages) {
				pkgInfo = mPackages.get(pkgName);
				if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
					XposedHelpers.callMethod(pmSvc, "grantPermissionsLPw",
							pkgInfo, true, pkgName);
				} else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
					XposedHelpers.callMethod(permissionSvc, "grantPermissions",
							pkgInfo, true, pkgName, mPermissionCallback);
				} else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
					XposedHelpers.callMethod(permissionSvc, "restorePermissionState",
							pkgInfo, true, pkgName, mPermissionCallback);
				} else {
					XposedHelpers.callMethod(permissionSvc, "restorePermissionState",
							pkgInfo, true, pkgName, mPermissionCallback, 0);
				}
				XposedHelpers.callMethod(mSettings, "writeLPr");
			}

			// Apply new permissions if needed
			if (killApp) {
				ApplicationInfo appInfo;
				if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
					appInfo = (ApplicationInfo) XposedHelpers.getObjectField(pkgInfo, "applicationInfo");
				} else {
					Context mContext = (Context) XposedHelpers.getObjectField(pmSvc, "mContext");
					appInfo = mContext.getPackageManager().getApplicationInfo(pkgName, 0);
				}
				XposedHelpers.callMethod(pmSvc, "killApplication", pkgName, appInfo.uid, "apply App Settings");
			}
		} catch (Throwable t) {
			XposedBridge.log(t);
		}
	}
}
