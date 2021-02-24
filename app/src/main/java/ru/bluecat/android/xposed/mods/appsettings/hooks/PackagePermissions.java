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
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import ru.bluecat.android.xposed.mods.appsettings.Common;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;

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
		this.mPermissionCallback = getObjectField(svc, mCallback);
		this.mPackages = (Map<String, Object>) getObjectField(pmSvc, "mPackages");
		mSettings = getObjectField(pmSvc, "mSettings");
	}

	public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam, XSharedPreferences prefs) {
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
					String pkgName = (String) getObjectField(param.args[0], "packageName");
					if (!XposedMod.isActive(prefs, pkgName) || !prefs.getBoolean(pkgName + Common.PREF_REVOKEPERMS, false))
						return;

					Set<String> disabledPermissions = prefs.getStringSet(pkgName + Common.PREF_REVOKELIST, null);
					if (disabledPermissions == null || disabledPermissions.isEmpty())
						return;

					ArrayList<String> origRequestedPermissions = (ArrayList<String>) getObjectField(param.args[0], "requestedPermissions");
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

					setObjectField(param.args[0], "requestedPermissions", newRequestedPermissions);
				}

				@SuppressWarnings("unchecked")
				@Override
				protected void afterHookedMethod(MethodHookParam param) {
					permissionSvc = param.thisObject;

					// restore requested permissions if they were modified
					ArrayList<String> origRequestedPermissions = (ArrayList<String>) param.getObjectExtra("orig_requested_permissions");
					if (origRequestedPermissions != null) {
						setObjectField(param.args[0], "requestedPermissions", origRequestedPermissions);
					}
					String pkgName = (String) getObjectField(param.args[0], "packageName");
					if (Common.MY_PACKAGE_NAME.equals(pkgName)) {
						grantRebootPermission(param, pkgName);
					}
				}
			};

			ClassLoader classLoader = lpparam.classLoader;
			Class<?> clsPermManagerService = findClass("com.android.server.pm.permission.PermissionManagerService", classLoader);
			Class<?> clsPkgManagerService = findClass("com.android.server.pm.PackageManagerService", classLoader);

			if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
				findAndHookMethod(clsPkgManagerService, "grantPermissionsLPw", "android.content.pm.PackageParser$Package", boolean.class, String.class, hookGrantPermissions);
			} else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
				findAndHookMethod(clsPermManagerService, "grantPermissions", "android.content.pm.PackageParser$Package", boolean.class, String.class,
						"com.android.server.pm.permission.PermissionManagerInternal$PermissionCallback", hookGrantPermissions);
			} else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
				findAndHookMethod(clsPermManagerService, "restorePermissionState", "android.content.pm.PackageParser$Package", boolean.class, String.class,
						"com.android.server.pm.permission.PermissionManagerServiceInternal$PermissionCallback", hookGrantPermissions);
			} else {
				findAndHookMethod(clsPermManagerService, "restorePermissionState", "com.android.server.pm.parsing.pkg.AndroidPackage", boolean.class, String.class,
						"com.android.server.pm.permission.PermissionManagerServiceInternal$PermissionCallback", hookGrantPermissions);
			}

			// Listen for broadcasts from the Settings part of the mod, so it's applied immediately
			findAndHookMethod(clsPkgManagerService, "systemReady", new XC_MethodHook() {
				@Override
				protected void afterHookedMethod(MethodHookParam param) {
					Context mContext = (Context) getObjectField(param.thisObject, "mContext");
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

	// https://github.com/Firefds/FirefdsKit
	private static void grantRebootPermission(XC_MethodHook.MethodHookParam param, String pkgName) {
		try {
			Object packageSettings;
			if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
				packageSettings = getObjectField(param.args[0], "mExtras");
			} else {
				Object mPackageManagerInt = getObjectField(param.thisObject,"mPackageManagerInt");
				packageSettings = callMethod(mPackageManagerInt, "getPackageSetting", pkgName);
			}

			Object permissionsState = callMethod(packageSettings, "getPermissionsState");
			Object mSettings = getObjectField(param.thisObject, "mSettings");
			Object mPermissions = getObjectField(mSettings, "mPermissions");
			if (!(Boolean) callMethod(permissionsState, "hasInstallPermission", Common.REBOOT)) {
				Object pAccess = callMethod(mPermissions, "get", Common.REBOOT);
				callMethod(permissionsState, "grantInstallPermission", pAccess);
			}
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
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
					callMethod(pmSvc, "grantPermissionsLPw", pkgInfo, true);
				} else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
					callMethod(pmSvc, "grantPermissionsLPw", pkgInfo, true, pkgName);
				} else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
					callMethod(permissionSvc, "grantPermissions", pkgInfo, true, pkgName, mPermissionCallback);
				} else {
					callMethod(permissionSvc, "restorePermissionState", pkgInfo, true, pkgName, mPermissionCallback);
				}
				callMethod(mSettings, "writeLPr");
			}

			// Apply new permissions if needed
			if (killApp) {
				ApplicationInfo appInfo;
				if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
					appInfo = (ApplicationInfo) getObjectField(pkgInfo, "applicationInfo");
				} else {
					Context mContext = (Context) getObjectField(pmSvc, "mContext");
					appInfo = mContext.getPackageManager().getApplicationInfo(pkgName, 0);
				}
				callMethod(pmSvc, "killApplication", pkgName, appInfo.uid, "apply App Settings");
			}
		} catch (Throwable t) {
			XposedBridge.log(t);
		}
	}
}
