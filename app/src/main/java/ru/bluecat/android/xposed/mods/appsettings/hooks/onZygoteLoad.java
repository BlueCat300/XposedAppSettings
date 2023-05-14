package ru.bluecat.android.xposed.mods.appsettings.hooks;

import android.app.Activity;
import android.app.AndroidAppHelper;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.XResources;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.util.TypedValue;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import ru.bluecat.android.xposed.mods.appsettings.Constants;


class onZygoteLoad {

	private static final String PROP_FULLSCREEN = "AppSettings-Fullscreen";
	private static final String PROP_IMMERSIVE = "AppSettings-Immersive";
	private static final String PROP_KEEP_SCREEN_ON = "AppSettings-KeepScreenOn";
	private static final String PROP_LEGACY_MENU = "AppSettings-LegacyMenu";
	private static final String PROP_ORIENTATION = "AppSettings-Orientation";

	private static final int FLAG_NEEDS_MENU_KEY = 0;
	static final String SYSTEMUI_PACKAGE = "com.android.systemui";
	private static final String[] SYSTEMUI_ADJUSTED_DIMENSIONS = {
		"status_bar_height",
		"navigation_bar_height", "navigation_bar_height_landscape",
		"navigation_bar_width",
		"system_bar_height"
	};

	static void activitySettings(XSharedPreferences prefs) {
		try {
			XposedHelpers.findAndHookMethod("com.android.internal.policy.PhoneWindow",
					null, "generateLayout",
					"com.android.internal.policy.DecorView", new XC_MethodHook() {

				protected void beforeHookedMethod(MethodHookParam param) {
					Window window = (Window) param.thisObject;
					View decorView = (View) param.args[0];
					Context context = window.getContext();
					String packageName = context.getPackageName();
					WindowInsetsController controller = null;
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
						controller = window.getInsetsController();
					}

					if (!Core.isActive(prefs, packageName))
						return;

					int fullscreen;
					try {
						fullscreen = prefs.getInt(packageName + Constants.PREF_FULLSCREEN,
								Constants.FULLSCREEN_DEFAULT);
					} catch (ClassCastException ex) {
						// Legacy boolean setting
						fullscreen = prefs.getBoolean(packageName + Constants.PREF_FULLSCREEN, false)
                                ? Constants.FULLSCREEN_FORCE : Constants.FULLSCREEN_DEFAULT;
					}
					boolean autoHideFullscreen = prefs.getBoolean(packageName + Constants.PREF_AUTO_HIDE_FULLSCREEN, false);
					if (fullscreen == Constants.FULLSCREEN_FORCE) {
						XposedHelpers.setAdditionalInstanceField(window, PROP_FULLSCREEN, Boolean.TRUE);
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
							if(autoHideFullscreen)
								controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

							controller.hide(WindowInsets.Type.statusBars());
						} else {
							window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
						}
					} else if (fullscreen == Constants.FULLSCREEN_PREVENT) {
						XposedHelpers.setAdditionalInstanceField(window, PROP_FULLSCREEN, Boolean.FALSE);
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
							controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
						} else {
							window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
						}
					} else if (fullscreen == Constants.FULLSCREEN_IMMERSIVE) {
						XposedHelpers.setAdditionalInstanceField(window, PROP_FULLSCREEN, Boolean.TRUE);
						XposedHelpers.setAdditionalInstanceField(decorView, PROP_IMMERSIVE, Boolean.TRUE);
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
							if(autoHideFullscreen)
								controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
							controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
						} else {
							window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
							decorView.setSystemUiVisibility(
								View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
									| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
									| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
						}
					}

					if (prefs.getBoolean(packageName + Constants.PREF_NO_TITLE, false))
						window.requestFeature(Window.FEATURE_NO_TITLE);

					if (prefs.getBoolean(packageName + Constants.PREF_ALLOW_ON_LOCKSCREEN, false))
							window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
								WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
								WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

					if (prefs.getBoolean(packageName + Constants.PREF_SCREEN_ON, false)) {
						window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
						XposedHelpers.setAdditionalInstanceField(window, PROP_KEEP_SCREEN_ON, Boolean.TRUE);
					}

					if (prefs.getBoolean(packageName + Constants.PREF_LEGACY_MENU, false)) {
						// NEEDS_MENU_SET_TRUE = 1
						XposedHelpers.callMethod(window, "setNeedsMenuKey", 1);
					}

                    int screenshot = prefs.getInt(packageName + Constants.PREF_SCREENSHOT,
                            Constants.PREF_SCREENSHOT_DEFAULT);
                    if (screenshot == Constants.PREF_SCREENSHOT_ALLOW) {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                    } else if (screenshot == Constants.PREF_SCREENSHOT_PREVENT) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                    }

					int orientation = prefs.getInt(packageName + Constants.PREF_ORIENTATION, prefs.getInt(Constants.PREF_DEFAULT + Constants.PREF_ORIENTATION, 0));
					if (orientation > 0 && orientation < Constants.orientationCodes.length && context instanceof Activity) {
						((Activity) context).setRequestedOrientation(Constants.orientationCodes[orientation]);
						XposedHelpers.setAdditionalInstanceField(context, PROP_ORIENTATION, orientation);
					}
				}
			});

			XposedHelpers.findAndHookMethod(Window.class, "setFlags", int.class, int.class, new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) {
					int flags = (Integer) param.args[0];
					int mask = (Integer) param.args[1];
					if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
						if ((mask & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
							Boolean fullscreen = (Boolean) XposedHelpers.getAdditionalInstanceField(
									param.thisObject, PROP_FULLSCREEN);
							if (fullscreen != null) {
								if (fullscreen) {    //fullscreen.booleanValue())
									flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
								} else {
									flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
								}
								param.args[0] = flags;
							}
						}
					}

					if ((mask & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0) {
						Boolean keepScreenOn = (Boolean) XposedHelpers.getAdditionalInstanceField(
								param.thisObject, PROP_KEEP_SCREEN_ON);
						if (keepScreenOn != null) {
							if (keepScreenOn) {
								flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
							}
							param.args[0] = flags;
						}
					}

					if ((mask & FLAG_NEEDS_MENU_KEY) != 0) {
						Boolean menu = (Boolean) XposedHelpers.getAdditionalInstanceField(
								param.thisObject, PROP_LEGACY_MENU);
						if (menu != null) {
							if (menu) {   //menu.booleanValue())
								flags |= FLAG_NEEDS_MENU_KEY;
							}
							param.args[0] = flags;
						}
					}
				}
			});

			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
				XposedHelpers.findAndHookMethod("android.view.ViewRootImpl", null,
						"dispatchSystemUiVisibilityChanged", int.class, int.class,
						int.class, int.class, new XC_MethodHook() {
					@Override
					protected void beforeHookedMethod(MethodHookParam param) {
						// Has the navigation bar been shown?
						int localChanges = (Integer) param.args[3];
						if ((localChanges & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0)
							return;

						// Should it be hidden?
						View decorView = (View) XposedHelpers.getObjectField(param.thisObject, "mView");
						Boolean immersive = (decorView == null) ? null :
								(Boolean) XposedHelpers.getAdditionalInstanceField(decorView, PROP_IMMERSIVE);
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
			XposedHelpers.findAndHookMethod(Activity.class, "setRequestedOrientation",
					int.class, new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) {
					Integer orientation = (Integer) XposedHelpers.getAdditionalInstanceField(
							param.thisObject, PROP_ORIENTATION);
					if (orientation != null) param.args[0] = Constants.orientationCodes[orientation];
				}
			});

			// fullscreen keyboard input
			XposedHelpers.findAndHookMethod(InputMethodService.class, "doStartInput",
					InputConnection.class, EditorInfo.class, boolean.class, new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) {
					EditorInfo info = (EditorInfo) param.args[1];
					if (info != null && info.packageName != null) {
						if (Core.isActive(prefs, info.packageName, Constants.PREF_NO_FULLSCREEN_IME))
							info.imeOptions |= EditorInfo.IME_FLAG_NO_FULLSCREEN;
					}
				}
			});
		} catch (Throwable e) {
			XposedBridge.log(e);
		}
	}

	static void dpiInSystem(XSharedPreferences prefs) {
		// Hook to override DPI (globally, including resource load + rendering)
		try {
			XposedHelpers.findAndHookMethod(Display.class, "updateDisplayInfoLocked", new XC_MethodHook() {
				@Override
				protected void afterHookedMethod(MethodHookParam param) {
					String packageName = AndroidAppHelper.currentPackageName();

					if (!Core.isActive(prefs, packageName)) {
						// No overrides for this package
						return;
					}

					int packageDPI = prefs.getInt(packageName + Constants.PREF_DPI,
							prefs.getInt(Constants.PREF_DEFAULT + Constants.PREF_DPI, 0));
					if (packageDPI > 0) {
						// Density for this package is overridden, change density
						Object mDisplayInfo = XposedHelpers.getObjectField(param.thisObject, "mDisplayInfo");
						XposedHelpers.setIntField(mDisplayInfo, "logicalDensityDpi", packageDPI);
					}
				}
			});

		} catch (Throwable t) {
			XposedBridge.log(t);
		}
	}

	/** Adjust all framework dimensions that should reflect
	 *  changes related with SystemUI, namely statusbar and navbar sizes.
	 *  The values are adjusted and replaced system-wide by fixed px values.
	 */
	static void adjustSystemDimensions(XSharedPreferences prefs) {
		if (!Core.isActive(prefs, SYSTEMUI_PACKAGE))
			return;

		int systemUiDpi = prefs.getInt(SYSTEMUI_PACKAGE + Constants.PREF_DPI,
				prefs.getInt(Constants.PREF_DEFAULT + Constants.PREF_DPI, 0));
		if (systemUiDpi <= 0)
			return;

		// SystemUI had its DPI overridden.
		// Adjust the relevant framework dimen resources.
		Resources sysRes = Resources.getSystem();
		float sysDensity = sysRes.getDisplayMetrics().density;
		float scaleFactor = (systemUiDpi / 160f) / sysDensity;

		for (String resName : SYSTEMUI_ADJUSTED_DIMENSIONS) {
			int id = sysRes.getIdentifier(resName, "dimen", "android");
			if (id != 0) {
				float original = sysRes.getDimension(id);
				XResources.setSystemWideReplacement(id,
						new XResources.DimensionReplacement(original * scaleFactor, TypedValue.COMPLEX_UNIT_PX));
			}
		}
	}
}
