package de.robv.android.xposed.mods.appsettings.hooks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.mods.appsettings.Common;

import static android.os.Build.VERSION.SDK_INT;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookConstructor;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;

public class PackagePermissions extends BroadcastReceiver {
	private Object pmSvc;
	private Object mPermissionCallback;
	private final Map<String, Object> mPackages;
	private static Object mSettings;
	private static Object permissionSvc;

	@SuppressWarnings("unchecked")
	public PackagePermissions(Object pmSvc) {
		this.pmSvc = pmSvc;
		this.mPermissionCallback = getObjectField(pmSvc, "mPermissionCallback");
		this.mPackages = (Map<String, Object>) getObjectField(pmSvc, "mPackages");
		mSettings = getObjectField(pmSvc, "mSettings");
	}

	public static void initHooks(XC_LoadPackage.LoadPackageParam lpparam) {
		/* Hook to the PackageManager service in order to
		 * - Listen for broadcasts to apply new settings and restart the app
		 * - Intercept the permission granting function to remove disabled permissions
		 */
		try {
			ClassLoader classLoader = lpparam.classLoader;
			Class<?> clsManagerService;
			clsManagerService = findClass("com.android.server.pm.PackageManagerService", classLoader);

			// Listen for broadcasts from the Settings part of the mod, so it's applied immediately
			findAndHookMethod(clsManagerService, "systemReady", new XC_MethodHook() {
				@Override
				protected void afterHookedMethod(MethodHookParam param) {
					Context mContext = (Context) getObjectField(param.thisObject, "mContext");
					mContext.registerReceiver(new PackagePermissions(param.thisObject),
							new IntentFilter(Common.MY_PACKAGE_NAME + ".UPDATE_PERMISSIONS"),
							Common.MY_PACKAGE_NAME + ".BROADCAST_PERMISSION",
							null);
				}
			});

			if (SDK_INT >= 28) {
				clsManagerService = findClass("com.android.server.pm.permission.PermissionManagerService", classLoader);

				findAndHookConstructor(clsManagerService, Context.class, Object.class, new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) {
						permissionSvc = param.thisObject;
					}
				});
			}

			// if the user has disabled certain permissions for an app, do as if the hadn't requested them
			// you requested those internet permissions? I didn't read that, sorry
			// restore requested permissions if they were modified
			XC_MethodHook hookGrantPermissions = new XC_MethodHook() {
				@SuppressWarnings("unchecked")
				@Override
				protected void beforeHookedMethod(MethodHookParam param) {
					String pkgName = (String) getObjectField(param.args[0], "packageName");
					if (!XposedMod.isActive(pkgName) || !XposedMod.prefs.getBoolean(pkgName + Common.PREF_REVOKEPERMS, false))
						return;

					Set<String> disabledPermissions = XposedMod.prefs.getStringSet(pkgName + Common.PREF_REVOKELIST, null);
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
					// restore requested permissions if they were modified
					ArrayList<String> origRequestedPermissions = (ArrayList<String>) param.getObjectExtra("orig_requested_permissions");
					if (origRequestedPermissions != null) {
						setObjectField(param.args[0], "requestedPermissions", origRequestedPermissions);
					}
					String pkgName = (String) getObjectField(param.args[0], "packageName");
					if (Common.MY_PACKAGE_NAME.equals(pkgName)) {
						grantRebootPermission(param);
					}
				}
			};
			if (SDK_INT < 21) {
				findAndHookMethod(clsManagerService, "grantPermissionsLPw", "android.content.pm.PackageParser$Package", boolean.class, hookGrantPermissions);
			} else if (SDK_INT <= 27){
				findAndHookMethod(clsManagerService, "grantPermissionsLPw", "android.content.pm.PackageParser$Package", boolean.class, String.class, hookGrantPermissions);
			} else if (SDK_INT == 28){
				findAndHookMethod(clsManagerService, "grantPermissions", "android.content.pm.PackageParser$Package", boolean.class, String.class,
						"com.android.server.pm.permission.PermissionManagerServiceInternal$PermissionCallback", hookGrantPermissions);
			} else {
				findAndHookMethod(clsManagerService, "restorePermissionState", "android.content.pm.PackageParser$Package", boolean.class, String.class,
						"com.android.server.pm.permission.PermissionManagerServiceInternal$PermissionCallback", hookGrantPermissions);
			}
		} catch (Throwable e) {
			XposedBridge.log(e);
		}
	}

	// https://github.com/Firefds/FirefdsKit
	private static void grantRebootPermission(XC_MethodHook.MethodHookParam param) {
		try {
			Object extras = getObjectField(param.args[0], "mExtras");
			Object ps = callMethod(extras, "getPermissionsState");
			Object settings = getObjectField(param.thisObject, "mSettings");
			Object permissions = getObjectField(settings, "mPermissions");
			if (!(Boolean) callMethod(ps, "hasInstallPermission", Common.REBOOT)) {
				Object pAccess = callMethod(permissions, "get", Common.REBOOT);
				callMethod(ps, "grantInstallPermission", pAccess);
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
				if (SDK_INT < 21) {
					callMethod(pmSvc, "grantPermissionsLPw", pkgInfo, true);
				} else if (SDK_INT <= 27) {
					callMethod(pmSvc, "grantPermissionsLPw", pkgInfo, true, pkgName);
				} else if (SDK_INT == 28) {
					callMethod(permissionSvc, "grantPermissions", pkgInfo, true, pkgName, mPermissionCallback);
				} else {
					callMethod(permissionSvc, "restorePermissionState", pkgInfo, true, pkgName, mPermissionCallback);
				}
				callMethod(mSettings, "writeLPr");
			}

			// Apply new permissions if needed
			if (killApp) {
				try {
					ApplicationInfo appInfo = (ApplicationInfo) getObjectField(pkgInfo, "applicationInfo");
					if (SDK_INT <= 18) {
						callMethod(pmSvc, "killApplication", pkgName, appInfo.uid);
					} else
						callMethod(pmSvc, "killApplication", pkgName, appInfo.uid, "apply App Settings");
				} catch (Throwable t) {
					XposedBridge.log(t);
				}
			}
		} catch (Throwable t) {
			XposedBridge.log(t);
		}
	}
}
