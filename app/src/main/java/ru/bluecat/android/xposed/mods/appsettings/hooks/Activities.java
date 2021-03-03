package ru.bluecat.android.xposed.mods.appsettings.hooks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import ru.bluecat.android.xposed.mods.appsettings.Common;


class Activities {

	private static Object processRecord;
	private static final String PROP_FULLSCREEN = "AppSettings-Fullscreen";
	private static final String PROP_IMMERSIVE = "AppSettings-Immersive";
	private static final String PROP_KEEP_SCREEN_ON = "AppSettings-KeepScreenOn";
	private static final String PROP_LEGACY_MENU = "AppSettings-LegacyMenu";
	private static final String PROP_ORIENTATION = "AppSettings-Orientation";

	private static final int FLAG_NEEDS_MENU_KEY = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 ?
			0 : XposedHelpers.getStaticIntField(WindowManager.LayoutParams.class, "FLAG_NEEDS_MENU_KEY");
    private static final String CLASS_PHONEWINDOW = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
			"com.android.internal.policy.PhoneWindow" : "com.android.internal.policy.impl.PhoneWindow";

    static void hookActivitySettings(XSharedPreferences prefs) {
        String CLASS_PHONEWINDOW_DECORVIEW;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			CLASS_PHONEWINDOW_DECORVIEW = "com.android.internal.policy.DecorView";
		} else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
			CLASS_PHONEWINDOW_DECORVIEW = "com.android.internal.policy.PhoneWindow.DecorView";
		} else {
			CLASS_PHONEWINDOW_DECORVIEW = "com.android.internal.policy.impl.PhoneWindow.DecorView";
		}
		try {
			XposedHelpers.findAndHookMethod(CLASS_PHONEWINDOW, null, "generateLayout",
					CLASS_PHONEWINDOW_DECORVIEW, new XC_MethodHook() {

				protected void beforeHookedMethod(MethodHookParam param) {
					Window window = (Window) param.thisObject;
					View decorView = (View) param.args[0];
					Context context = window.getContext();
					String packageName = context.getPackageName();

					if (!XposedMod.isActive(prefs, packageName))
						return;

					int fullscreen;
					try {
						fullscreen = prefs.getInt(packageName + Common.PREF_FULLSCREEN,
								Common.FULLSCREEN_DEFAULT);
					} catch (ClassCastException ex) {
						// Legacy boolean setting
						fullscreen = prefs.getBoolean(packageName + Common.PREF_FULLSCREEN, false)
                                ? Common.FULLSCREEN_FORCE : Common.FULLSCREEN_DEFAULT;
					}
					if (fullscreen == Common.FULLSCREEN_FORCE) {
						window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
						XposedHelpers.setAdditionalInstanceField(window, PROP_FULLSCREEN, Boolean.TRUE);
					} else if (fullscreen == Common.FULLSCREEN_PREVENT) {
						window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
						XposedHelpers.setAdditionalInstanceField(window, PROP_FULLSCREEN, Boolean.FALSE);
					} else if (fullscreen == Common.FULLSCREEN_IMMERSIVE) {
						window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
						XposedHelpers.setAdditionalInstanceField(window, PROP_FULLSCREEN, Boolean.TRUE);
						XposedHelpers.setAdditionalInstanceField(decorView, PROP_IMMERSIVE, Boolean.TRUE);
						decorView.setSystemUiVisibility(
								View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
								| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
								| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
					}

					if (prefs.getBoolean(packageName + Common.PREF_NO_TITLE, false))
						window.requestFeature(Window.FEATURE_NO_TITLE);

					if (prefs.getBoolean(packageName + Common.PREF_ALLOW_ON_LOCKSCREEN, false))
							window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
								WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
								WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

					if (prefs.getBoolean(packageName + Common.PREF_SCREEN_ON, false)) {
						window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
						XposedHelpers.setAdditionalInstanceField(window, PROP_KEEP_SCREEN_ON, Boolean.TRUE);
					}

					if (prefs.getBoolean(packageName + Common.PREF_LEGACY_MENU, false)) {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
						     // NEEDS_MENU_SET_TRUE = 1
							XposedHelpers.callMethod(window, "setNeedsMenuKey", 1);
						}
						else {
						     window.setFlags(FLAG_NEEDS_MENU_KEY, FLAG_NEEDS_MENU_KEY);
							XposedHelpers.setAdditionalInstanceField(window, PROP_LEGACY_MENU, Boolean.TRUE);
						}
					}

                    int screenshot = prefs.getInt(packageName + Common.PREF_SCREENSHOT,
                            Common.PREF_SCREENSHOT_DEFAULT);
                    if (screenshot == Common.PREF_SCREENSHOT_ALLOW) {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                    } else if (screenshot == Common.PREF_SCREENSHOT_PREVENT) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                    }

					int orientation = prefs.getInt(packageName + Common.PREF_ORIENTATION, prefs.getInt(Common.PREF_DEFAULT + Common.PREF_ORIENTATION, 0));
					if (orientation > 0 && orientation < Common.orientationCodes.length && context instanceof Activity) {
						((Activity) context).setRequestedOrientation(Common.orientationCodes[orientation]);
						XposedHelpers.setAdditionalInstanceField(context, PROP_ORIENTATION, orientation);
					}
				}
			});

			XposedHelpers.findAndHookMethod(Window.class, "setFlags", int.class, int.class,
					new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) {

					int flags = (Integer) param.args[0];
					int mask = (Integer) param.args[1];
					if ((mask & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
						Boolean fullscreen = (Boolean) XposedHelpers.getAdditionalInstanceField(param.thisObject, PROP_FULLSCREEN);
						if (fullscreen != null) {
							if (fullscreen) {    //fullscreen.booleanValue())
								flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
							} else {
								flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
							}
							param.args[0] = flags;
						}
					}
					if ((mask & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0) {
						Boolean keepScreenOn = (Boolean) XposedHelpers.getAdditionalInstanceField(param.thisObject, PROP_KEEP_SCREEN_ON);
						if (keepScreenOn != null) {
							if (keepScreenOn) {
								flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
							}
							param.args[0] = flags;
						}
					}
					if ((mask & FLAG_NEEDS_MENU_KEY) != 0) {
						Boolean menu = (Boolean) XposedHelpers.getAdditionalInstanceField(param.thisObject, PROP_LEGACY_MENU);
						if (menu != null) {
							if (menu) {   //menu.booleanValue())
								flags |= FLAG_NEEDS_MENU_KEY;
							}
							param.args[0] = flags;
						}
					}
				}
			});

			XposedHelpers.findAndHookMethod("android.view.ViewRootImpl", null,
					"dispatchSystemUiVisibilityChanged", int.class, int.class, int.class, int.class, new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) {
					// Has the navigation bar been shown?
					int localChanges = (Integer) param.args[3];
					if ((localChanges & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0)
						return;

					// Should it be hidden?
					View decorView = (View) XposedHelpers.getObjectField(param.thisObject, "mView");
					Boolean immersive = (decorView == null) ? null : (Boolean) XposedHelpers.getAdditionalInstanceField(decorView, PROP_IMMERSIVE);
					if (immersive == null || !immersive) //immersive.booleanValue())
						return;

					// Enforce SYSTEM_UI_FLAG_HIDE_NAVIGATION and hide changes to this flag
					int globalVisibility = (Integer) param.args[1];
					int localValue = (Integer) param.args[2];
					param.args[1] = globalVisibility | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
					param.args[2] = localValue | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
					param.args[3] = localChanges & ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
				}
			});

			// force orientation
			XposedHelpers.findAndHookMethod(Activity.class, "setRequestedOrientation", int.class, new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) {
					Integer orientation = (Integer) XposedHelpers.getAdditionalInstanceField(param.thisObject, PROP_ORIENTATION);
					if (orientation != null)
						param.args[0] = Common.orientationCodes[orientation];
				}
			});

			// fullscreen keyboard input
			XposedHelpers.findAndHookMethod(InputMethodService.class, "doStartInput",
					InputConnection.class, EditorInfo.class, boolean.class, new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) {
					EditorInfo info = (EditorInfo) param.args[1];
					if (info != null && info.packageName != null) {
						if (XposedMod.isActive(prefs, info.packageName, Common.PREF_NO_FULLSCREEN_IME))
							info.imeOptions |= EditorInfo.IME_FLAG_NO_FULLSCREEN;
					}
				}
			});
		} catch (Throwable e) {
			XposedBridge.log(e);
		}
	}

	static void hookActivitySettingsInSystemServer(XC_LoadPackage.LoadPackageParam lpparam, XSharedPreferences prefs) {
		try {
			// Hook one of the several variations of ActivityStack.realStartActivityLocked from different ROMs
			ClassLoader classLoader = lpparam.classLoader;
			Method mthRealStartActivityLocked;
			if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
				mthRealStartActivityLocked = XposedHelpers.findMethodExact("com.android.server.am.ActivityStackSupervisor", classLoader, "realStartActivityLocked",
						"com.android.server.am.ActivityRecord", "com.android.server.am.ProcessRecord",
						boolean.class, boolean.class);
			} else {
				mthRealStartActivityLocked = XposedHelpers.findMethodExact("com.android.server.wm.ActivityStackSupervisor", classLoader, "realStartActivityLocked",
						"com.android.server.wm.ActivityRecord", "com.android.server.wm.WindowProcessController",
						boolean.class, boolean.class);
			}
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				XposedHelpers.findAndHookConstructor("com.android.server.am.ProcessRecord", classLoader, "com.android.server.am.ActivityManagerService",
						"android.content.pm.ApplicationInfo", String.class, int.class, new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) {
						processRecord = param.thisObject;
					}
				});
            }

            // Resident
			XposedBridge.hookMethod(mthRealStartActivityLocked, new XC_MethodHook() {
				@Override
				protected void afterHookedMethod(MethodHookParam param) {
					String pkgName = (String) XposedHelpers.getObjectField(param.args[0], "packageName");
					if (XposedMod.isActive(prefs, pkgName, Common.PREF_RESIDENT)) {
						int adj = -12;
						Object proc = XposedHelpers.getObjectField(param.args[0], "app");

						// Override the *Adj values if meant to be resident in memory
						if (proc != null) {
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
								proc = processRecord;
							}
							XposedHelpers.setIntField(proc, "maxAdj", adj);
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
								XposedHelpers.setIntField(proc, "mCurRawAdj", adj);
							} else {
								XposedHelpers.setIntField(proc, "curRawAdj", adj);
							}
							XposedHelpers.setIntField(proc, "setRawAdj", adj);
							XposedHelpers.setIntField(proc, "curAdj", adj);
							XposedHelpers.setIntField(proc, "setAdj", adj);
						}
					}
				}
			});

            // Recent Tasks
			String activityRecordClass = "com.android.server.am.ActivityRecord";
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				activityRecordClass = "com.android.server.wm.ActivityRecord";
			}
			XposedBridge.hookAllConstructors(XposedHelpers.findClass(activityRecordClass, classLoader), new XC_MethodHook() {
				@Override
				protected void afterHookedMethod(MethodHookParam param) {
					ActivityInfo aInfo = (ActivityInfo) XposedHelpers.getObjectField(param.thisObject, "info");
					if (aInfo == null)
						return;
					String pkgName = aInfo.packageName;
					if (prefs.getInt(pkgName + Common.PREF_RECENTS_MODE, Common.PREF_RECENTS_DEFAULT) > 0) {
						int recentsMode = prefs.getInt(pkgName + Common.PREF_RECENTS_MODE, Common.PREF_RECENTS_DEFAULT);
						if (recentsMode == Common.PREF_RECENTS_DEFAULT)
							return;
						Intent intent = (Intent) XposedHelpers.getObjectField(param.thisObject, "intent");
						if (recentsMode == Common.PREF_RECENTS_FORCE) {
							int flags = (intent.getFlags() & ~Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
							intent.setFlags(flags);
						}
						else if (recentsMode == Common.PREF_RECENTS_PREVENT)
							intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
					}
				}
			});

            //Disable restricting Android API to get recent tasks in Lollipop and newer.
            //https://github.com/pylerSM/UnrestrictedGetTasks
			String recentTasksClass = "com.android.server.am.ActivityManagerService";
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				recentTasksClass = "com.android.server.wm.ActivityTaskManagerService";
			}
			XposedHelpers.findAndHookMethod(recentTasksClass, classLoader, "isGetTasksAllowed",
					String.class, int.class, int.class, new XC_MethodHook() {
				@Override
				protected void afterHookedMethod(MethodHookParam param) {
					Context mContext = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
					String callingApp = mContext.getPackageManager().getNameForUid((Integer) param.args[2]);
					if (Common.MY_PACKAGE_NAME.equals(callingApp) || XposedMod.isActive(prefs, callingApp, Common.PREF_RECENT_TASKS)) {
						param.setResult(true);
					}
				}
			});
		} catch (Throwable e) {
			XposedBridge.log(e);
		}
	}
}
