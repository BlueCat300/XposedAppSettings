package ru.bluecat.android.xposed.mods.appsettings.hooks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import ru.bluecat.android.xposed.mods.appsettings.Common;

import static android.os.Build.VERSION.SDK_INT;
import static de.robv.android.xposed.XposedBridge.hookAllConstructors;
import static de.robv.android.xposed.XposedBridge.hookMethod;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookConstructor;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findMethodExact;
import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.getStaticIntField;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.setIntField;


class Activities {

	private static Object processRecord;
	private static final String PROP_FULLSCREEN = "AppSettings-Fullscreen";
	private static final String PROP_IMMERSIVE = "AppSettings-Immersive";
	private static final String PROP_KEEP_SCREEN_ON = "AppSettings-KeepScreenOn";
	private static final String PROP_LEGACY_MENU = "AppSettings-LegacyMenu";
	private static final String PROP_ORIENTATION = "AppSettings-Orientation";

	private static int FLAG_NEEDS_MENU_KEY = SDK_INT >= 22 ? 0 : getStaticIntField(WindowManager.LayoutParams.class, "FLAG_NEEDS_MENU_KEY");
    private static String CLASS_PHONEWINDOW = SDK_INT >= 23 ? "com.android.internal.policy.PhoneWindow" : "com.android.internal.policy.impl.PhoneWindow";

    static void hookActivitySettings() {
        String CLASS_PHONEWINDOW_DECORVIEW;
        if (SDK_INT >= 24) {
			CLASS_PHONEWINDOW_DECORVIEW = "com.android.internal.policy.DecorView";
		} else if (SDK_INT == 23) {
			CLASS_PHONEWINDOW_DECORVIEW = "com.android.internal.policy.PhoneWindow.DecorView";
		} else {
			CLASS_PHONEWINDOW_DECORVIEW = "com.android.internal.policy.impl.PhoneWindow.DecorView";
		}
		try {
			findAndHookMethod(CLASS_PHONEWINDOW, null, "generateLayout",
					CLASS_PHONEWINDOW_DECORVIEW, new XC_MethodHook() {

				protected void beforeHookedMethod(MethodHookParam param) {
					Window window = (Window) param.thisObject;
					View decorView = (View) param.args[0];
					Context context = window.getContext();
					String packageName = context.getPackageName();

					if (!XposedMod.isActive(packageName))
						return;

					int fullscreen;
					try {
						fullscreen = XposedMod.prefs.getInt(packageName + Common.PREF_FULLSCREEN,
								Common.FULLSCREEN_DEFAULT);
					} catch (ClassCastException ex) {
						// Legacy boolean setting
						fullscreen = XposedMod.prefs.getBoolean(packageName + Common.PREF_FULLSCREEN, false)
                                ? Common.FULLSCREEN_FORCE : Common.FULLSCREEN_DEFAULT;
					}
					if (fullscreen == Common.FULLSCREEN_FORCE) {
						window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
						setAdditionalInstanceField(window, PROP_FULLSCREEN, Boolean.TRUE);
					} else if (fullscreen == Common.FULLSCREEN_PREVENT) {
						window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
						setAdditionalInstanceField(window, PROP_FULLSCREEN, Boolean.FALSE);
					} else if (fullscreen == Common.FULLSCREEN_IMMERSIVE && SDK_INT >= 19) {
						window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
						setAdditionalInstanceField(window, PROP_FULLSCREEN, Boolean.TRUE);
						setAdditionalInstanceField(decorView, PROP_IMMERSIVE, Boolean.TRUE);
						decorView.setSystemUiVisibility(
								View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
								| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
								| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
					}

					if (XposedMod.prefs.getBoolean(packageName + Common.PREF_NO_TITLE, false))
						window.requestFeature(Window.FEATURE_NO_TITLE);

					if (XposedMod.prefs.getBoolean(packageName + Common.PREF_ALLOW_ON_LOCKSCREEN, false))
							window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
								WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
								WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

					if (XposedMod.prefs.getBoolean(packageName + Common.PREF_SCREEN_ON, false)) {
						window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
						setAdditionalInstanceField(window, PROP_KEEP_SCREEN_ON, Boolean.TRUE);
					}

					if (XposedMod.prefs.getBoolean(packageName + Common.PREF_LEGACY_MENU, false)) {
						if (SDK_INT >= 22) {
						     // NEEDS_MENU_SET_TRUE = 1
						     callMethod(window, "setNeedsMenuKey", 1);
						}
						else {
						     window.setFlags(FLAG_NEEDS_MENU_KEY, FLAG_NEEDS_MENU_KEY);
						     setAdditionalInstanceField(window, PROP_LEGACY_MENU, Boolean.TRUE);							
						}
					}

                    int screenshot = XposedMod.prefs.getInt(packageName + Common.PREF_SCREENSHOT,
                            Common.PREF_SCREENSHOT_DEFAULT);
                    if (screenshot == Common.PREF_SCREENSHOT_ALLOW) {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                    } else if (screenshot == Common.PREF_SCREENSHOT_PREVENT) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                    }

					int orientation = XposedMod.prefs.getInt(packageName + Common.PREF_ORIENTATION, XposedMod.prefs.getInt(Common.PREF_DEFAULT + Common.PREF_ORIENTATION, 0));
					if (orientation > 0 && orientation < Common.orientationCodes.length && context instanceof Activity) {
						((Activity) context).setRequestedOrientation(Common.orientationCodes[orientation]);
						setAdditionalInstanceField(context, PROP_ORIENTATION, orientation);
					}
				}
			});

			findAndHookMethod(Window.class, "setFlags", int.class, int.class,
					new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) {

					int flags = (Integer) param.args[0];
					int mask = (Integer) param.args[1];
					if ((mask & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
						Boolean fullscreen = (Boolean) getAdditionalInstanceField(param.thisObject, PROP_FULLSCREEN);
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
						Boolean keepScreenOn = (Boolean) getAdditionalInstanceField(param.thisObject, PROP_KEEP_SCREEN_ON);
						if (keepScreenOn != null) {
							if (keepScreenOn) {
								flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
							}
							param.args[0] = flags;
						}
					}
					if ((mask & FLAG_NEEDS_MENU_KEY) != 0) {
						Boolean menu = (Boolean) getAdditionalInstanceField(param.thisObject, PROP_LEGACY_MENU);
						if (menu != null) {
							if (menu) {   //menu.booleanValue())
								flags |= FLAG_NEEDS_MENU_KEY;
							}
							param.args[0] = flags;
						}
					}
				}
			});

			if (SDK_INT >= 19) {
				findAndHookMethod("android.view.ViewRootImpl", null, "dispatchSystemUiVisibilityChanged",
						int.class, int.class, int.class, int.class, new XC_MethodHook() {
					@Override
					protected void beforeHookedMethod(MethodHookParam param) {
						// Has the navigation bar been shown?
						int localChanges = (Integer) param.args[3];
						if ((localChanges & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0)
							return;

						// Should it be hidden?
						View decorView = (View) getObjectField(param.thisObject, "mView");
						Boolean immersive = (decorView == null)
								? null
								: (Boolean) getAdditionalInstanceField(decorView, PROP_IMMERSIVE);
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
			}

			// force orientation
			findAndHookMethod(Activity.class, "setRequestedOrientation", int.class, new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) {
					Integer orientation = (Integer) getAdditionalInstanceField(param.thisObject, PROP_ORIENTATION);
					if (orientation != null)
						param.args[0] = Common.orientationCodes[orientation];
				}
			});

			// fullscreen keyboard input
			findAndHookMethod(InputMethodService.class, "doStartInput",
					InputConnection.class, EditorInfo.class, boolean.class, new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) {
					EditorInfo info = (EditorInfo) param.args[1];
					if (info != null && info.packageName != null) {
						if (XposedMod.isActive(info.packageName, Common.PREF_NO_FULLSCREEN_IME))
							info.imeOptions |= EditorInfo.IME_FLAG_NO_FULLSCREEN;
					}
				}
			});
		} catch (Throwable e) {
			XposedBridge.log(e);
		}
	}

	static void hookActivitySettingsInSystemServer(XC_LoadPackage.LoadPackageParam lpparam) {
		try {
			// Hook one of the several variations of ActivityStack.realStartActivityLocked from different ROMs
			ClassLoader classLoader = lpparam.classLoader;
			Method mthRealStartActivityLocked;
			if (SDK_INT < 19) {
				try {
					mthRealStartActivityLocked = findMethodExact("com.android.server.am.ActivityStack", classLoader, "realStartActivityLocked",
							"com.android.server.am.ActivityRecord", "com.android.server.am.ProcessRecord",
							boolean.class, boolean.class, boolean.class);
				} catch (NoSuchMethodError t) {
					mthRealStartActivityLocked = findMethodExact("com.android.server.am.ActivityStack", classLoader, "realStartActivityLocked",
							"com.android.server.am.ActivityRecord", "com.android.server.am.ProcessRecord",
							boolean.class, boolean.class);
				}
			} else if (SDK_INT <= 28) {
				mthRealStartActivityLocked = findMethodExact("com.android.server.am.ActivityStackSupervisor", classLoader, "realStartActivityLocked",
						"com.android.server.am.ActivityRecord", "com.android.server.am.ProcessRecord",
						boolean.class, boolean.class);
			} else {
				mthRealStartActivityLocked = findMethodExact("com.android.server.wm.ActivityStackSupervisor", classLoader, "realStartActivityLocked",
						"com.android.server.wm.ActivityRecord", "com.android.server.wm.WindowProcessController",
						boolean.class, boolean.class);
			}
            if (SDK_INT >= 29) {
                findAndHookConstructor("com.android.server.am.ProcessRecord", classLoader, "com.android.server.am.ActivityManagerService",
						"android.content.pm.ApplicationInfo", String.class, int.class, new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) {
						processRecord = param.thisObject;
					}
				});
            }

            // Resident
			hookMethod(mthRealStartActivityLocked, new XC_MethodHook() {
				@Override
				protected void afterHookedMethod(MethodHookParam param) {
					String pkgName = (String) getObjectField(param.args[0], "packageName");
					if (XposedMod.isActive(pkgName, Common.PREF_RESIDENT)) {
						int adj = -12;
						Object proc = getObjectField(param.args[0], "app");

						// Override the *Adj values if meant to be resident in memory
						if (proc != null) {
							if (SDK_INT >= 29) {
								proc = processRecord;
							}
							setIntField(proc, "maxAdj", adj);
							if (SDK_INT <= 18) {
								setIntField(proc, "hiddenAdj", adj);
							}
							if (SDK_INT >= 29) {
								setIntField(proc, "mCurRawAdj", adj);
							} else {
								setIntField(proc, "curRawAdj", adj);
							}
							setIntField(proc, "setRawAdj", adj);
							setIntField(proc, "curAdj", adj);
							setIntField(proc, "setAdj", adj);
						}
					}
				}
			});

            // Recent Tasks
			String activityRecordClass = "com.android.server.am.ActivityRecord";
			if (SDK_INT >= 29) {
				activityRecordClass = "com.android.server.wm.ActivityRecord";
			}
			hookAllConstructors(findClass(activityRecordClass, classLoader), new XC_MethodHook() {
				@Override
				protected void afterHookedMethod(MethodHookParam param) {
					ActivityInfo aInfo = (ActivityInfo) getObjectField(param.thisObject, "info");
					if (aInfo == null)
						return;
					String pkgName = aInfo.packageName;
					if (XposedMod.prefs.getInt(pkgName + Common.PREF_RECENTS_MODE, Common.PREF_RECENTS_DEFAULT) > 0) {
						int recentsMode = XposedMod.prefs.getInt(pkgName + Common.PREF_RECENTS_MODE, Common.PREF_RECENTS_DEFAULT);
						if (recentsMode == Common.PREF_RECENTS_DEFAULT)
							return;
						Intent intent = (Intent) getObjectField(param.thisObject, "intent");
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
            if(SDK_INT >= 21) {
				String recentTasksClass = "com.android.server.am.ActivityManagerService";
				if (SDK_INT >= 29) {
					recentTasksClass = "com.android.server.wm.ActivityTaskManagerService";
				}
				findAndHookMethod(recentTasksClass, classLoader, "isGetTasksAllowed",
						String.class, int.class, int.class, new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) {
						Context mContext = (Context) getObjectField(param.thisObject, "mContext");
						String callingApp = mContext.getPackageManager().getNameForUid((Integer) param.args[2]);
						if (Common.MY_PACKAGE_NAME.equals(callingApp) || XposedMod.isActive(callingApp, Common.PREF_RECENT_TASKS)) {
							param.setResult(true);
						}
					}
				});
            }
		} catch (Throwable e) {
			XposedBridge.log(e);
		}
	}
}
