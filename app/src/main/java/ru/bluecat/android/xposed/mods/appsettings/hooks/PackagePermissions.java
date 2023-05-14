package ru.bluecat.android.xposed.mods.appsettings.hooks;

import static ru.bluecat.android.xposed.mods.appsettings.Constants.ACTION_PERMISSIONS;
import static ru.bluecat.android.xposed.mods.appsettings.Constants.MY_PACKAGE_NAME;
import static ru.bluecat.android.xposed.mods.appsettings.Constants.PREF_REVOKELIST;
import static ru.bluecat.android.xposed.mods.appsettings.Constants.PREF_REVOKEPERMS;
import static ru.bluecat.android.xposed.mods.appsettings.Constants.TAG;
import static ru.bluecat.android.xposed.mods.appsettings.hooks.Core.mResources;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import ru.bluecat.android.xposed.mods.appsettings.R;

public class PackagePermissions extends BroadcastReceiver {
	private final Object pmSvc;
	private final Object mPermissionCallback;
	private final Map<String, Object> mPackages;
	private final Object mSettings;
	private static Object permissionSvc;
	private static Object computer;

	@SuppressWarnings("unchecked")
	public PackagePermissions(Object pmSvc) {
		this.pmSvc = pmSvc;
		var svc = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q ? pmSvc : permissionSvc;
		var mCallback = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q ?
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
					var pkgName = (String) XposedHelpers.getObjectField(param.args[0], "packageName");
					if (!Core.isActive(prefs, pkgName) || !prefs.getBoolean(pkgName + PREF_REVOKEPERMS, false))
						return;

					var disabledPermissions = prefs.getStringSet(pkgName + PREF_REVOKELIST, null);
					if (disabledPermissions == null || disabledPermissions.isEmpty())
						return;

					var origRequestedPermissions = (ArrayList<String>) XposedHelpers.getObjectField(param.args[0],
							"requestedPermissions");

					param.setObjectExtra("orig_requested_permissions", origRequestedPermissions);

					var newRequestedPermissions = new ArrayList<>(origRequestedPermissions.size());
					for (String perm : origRequestedPermissions) {
						if (!disabledPermissions.contains(perm))
							newRequestedPermissions.add(perm);
						else
							// you requested those internet permissions? I didn't read that, sorry
							Log.w(TAG, "Not granting permission " + perm
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
					var origRequestedPermissions = (ArrayList<String>) param.getObjectExtra("orig_requested_permissions");
					if (origRequestedPermissions != null) {
						XposedHelpers.setObjectField(param.args[0], "requestedPermissions", origRequestedPermissions);
					}

					var pkgName = (String) XposedHelpers.getObjectField(param.args[0], "packageName");
					if (MY_PACKAGE_NAME.equals(pkgName)) {
						if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
							Reboot.grantRebootPermission(param, pkgName);
						} else {
							Reboot.grantRebootPermissionS(param.thisObject, param.args[0]);
						}
					}
				}
			};

			var classLoader = lpparam.classLoader;
			Class<?> clsPermManagerService;
			if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
				clsPermManagerService = XposedHelpers.findClass(
						"com.android.server.pm.permission.PermissionManagerService", classLoader);
			} else {
				clsPermManagerService = XposedHelpers.findClass(
						"com.android.server.pm.permission.PermissionManagerServiceImpl", classLoader);
			}

			var clsPkgManagerService = XposedHelpers.findClass(
					"com.android.server.pm.PackageManagerService", classLoader);


			var corePackage = "com.android.server.pm.";

			var targetMethod = switch (Build.VERSION.SDK_INT) {
				case Build.VERSION_CODES.O_MR1 -> "grantPermissionsLPw";
				case Build.VERSION_CODES.P -> "grantPermissions";
				default -> "restorePermissionState";
			};

			var param1 = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q ?
					"android.content.pm.PackageParser$Package" :
					corePackage + "parsing.pkg.AndroidPackage";

			var param4 = switch (Build.VERSION.SDK_INT) {
				case Build.VERSION_CODES.P -> corePackage +
						"permission.PermissionManagerInternal$PermissionCallback";
				case Build.VERSION_CODES.Q, Build.VERSION_CODES.R -> corePackage +
						"permission.PermissionManagerServiceInternal$PermissionCallback";
				case Build.VERSION_CODES.S, Build.VERSION_CODES.S_V2 -> corePackage +
						"permission.PermissionManagerService$PermissionCallback";
				default -> corePackage + "permission.PermissionManagerServiceImpl$PermissionCallback";
			};

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
					var mContext = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
					mContext.registerReceiver(new PackagePermissions(param.thisObject),
							new IntentFilter(MY_PACKAGE_NAME + ".UPDATE_PERMISSIONS"),
							MY_PACKAGE_NAME + ".BROADCAST_PERMISSION",
							null);
				}
			});

			XposedHelpers.findAndHookMethod("com.android.server.pm.Computer", classLoader,
					"getUsed", new XC_MethodHook() {
				@Override
				protected void afterHookedMethod(MethodHookParam param) {
					computer = param.thisObject;
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
			if (intent.getExtras() ==  null || !ACTION_PERMISSIONS.equals(intent.getExtras().getString("action")))
				return;

			var pkgName = intent.getExtras().getString("Package");
			var killApp = intent.getExtras().getBoolean("Kill", false);

			Object pkgInfo;
			synchronized (mPackages) {
				pkgInfo = mPackages.get(pkgName);
				switch (Build.VERSION.SDK_INT) {
					case Build.VERSION_CODES.O_MR1 -> XposedHelpers.callMethod(pmSvc,
							"grantPermissionsLPw", pkgInfo, true, pkgName);
					case Build.VERSION_CODES.P -> XposedHelpers.callMethod(permissionSvc,
							"grantPermissions", pkgInfo, true, pkgName, mPermissionCallback);
					case Build.VERSION_CODES.Q, Build.VERSION_CODES.R -> XposedHelpers.callMethod(permissionSvc,
							"restorePermissionState", pkgInfo, true, pkgName, mPermissionCallback);
					default -> XposedHelpers.callMethod(permissionSvc, "restorePermissionState",
							pkgInfo, true, pkgName, mPermissionCallback, 0);
				}

				if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
					XposedHelpers.callMethod(mSettings, "writeLPr");
				} else {
					XposedHelpers.callMethod(mSettings, "writeLPr", (Object) computer);
				}
			}

			// Apply new permissions if needed
			if (killApp) {
				ApplicationInfo appInfo;
				Context mContext = (Context) XposedHelpers.getObjectField(pmSvc, "mContext");
				if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
					appInfo = (ApplicationInfo) XposedHelpers.getObjectField(pkgInfo, "applicationInfo");
				} else {
					appInfo = mContext.getPackageManager().getApplicationInfo(pkgName, 0);
				}
				XposedHelpers.callMethod(pmSvc, "killApplication", pkgName,
						appInfo.uid, "apply App Settings");
				Toast.makeText(context, mResources.getString(R.string.module_force_stop),
						Toast.LENGTH_SHORT).show();

			}
		} catch (Throwable t) {
			XposedBridge.log(t);
		}
	}
}
