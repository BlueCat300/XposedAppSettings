package ru.bluecat.android.xposed.mods.appsettings.hooks;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.media.AudioTrack;
import android.media.JetPlayer;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.ViewConfiguration;

import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import ru.bluecat.android.xposed.mods.appsettings.Common;

public class onPackageLoad {

    static void screenSettings(final XC_LoadPackage.LoadPackageParam lpparam, XSharedPreferences prefs) {
        if (Core.isActive(prefs, lpparam.packageName)) {
            // Override settings used when loading resources
            try {
                XposedHelpers.findAndHookMethod(ContextWrapper.class, "attachBaseContext",
                        Context.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (param.args[0] != null && (param.args[0] instanceof Context)) {

                            Context context = (Context) param.args[0];
                            String packageName = lpparam.packageName;
                            Resources res = context.getResources();
                            Configuration config = new Configuration(res.getConfiguration());

                            DisplayMetrics runningMetrics = res.getDisplayMetrics();
                            DisplayMetrics newMetrics;
                            if (runningMetrics != null) {
                                newMetrics = new DisplayMetrics();
                                newMetrics.setTo(runningMetrics);
                            } else {
                                newMetrics = res.getDisplayMetrics();
                            }

                            // Workaround for KitKat. The keyguard is a different package now but runs in the
                            // same process as SystemUI and displays as main package
                            if (packageName.equals("com.android.keyguard")) {
                                packageName = onZygoteLoad.SYSTEMUI_PACKAGE;
                            }

                            // settings related to the density etc. are calculated for the running app...
                            if (packageName != null) {
                                int screen = prefs.getInt(packageName + Common.PREF_SCREEN,
                                        prefs.getInt(Common.PREF_DEFAULT + Common.PREF_SCREEN, 0));
                                if (screen < 0 || screen >= Common.swdp.length) {
                                    screen = 0;
                                }

                                int dpi = prefs.getInt(packageName + Common.PREF_DPI,
                                        prefs.getInt(Common.PREF_DEFAULT + Common.PREF_DPI, 0));
                                int fontScale = prefs.getInt(packageName + Common.PREF_FONT_SCALE,
                                        prefs.getInt(Common.PREF_DEFAULT + Common.PREF_FONT_SCALE, 0));
                                int swdp = Common.swdp[screen];
                                int wdp = Common.wdp[screen];
                                int hdp = Common.hdp[screen];

                                boolean xlarge = prefs.getBoolean(packageName + Common.PREF_XLARGE, false);
                                boolean ltr = prefs.getBoolean(packageName + Common.PREF_LTR, false);

                                if (swdp > 0) {
                                    config.smallestScreenWidthDp = swdp;
                                    config.screenWidthDp = wdp;
                                    config.screenHeightDp = hdp;
                                }
                                if (xlarge) {
                                    config.screenLayout |= Configuration.SCREENLAYOUT_SIZE_XLARGE;
                                }
                                if (dpi > 0) {
                                    newMetrics.density = dpi / 160f;
                                    newMetrics.densityDpi = dpi;
                                    XposedHelpers.setIntField(config, "densityDpi", dpi);
                                }
                                if (fontScale > 0) {
                                    config.fontScale = fontScale / 100.0f;
                                }

                                // https://github.com/solohsu/XposedAppLocale
                                Locale loc = getPackageSpecificLocale(prefs, packageName);

                                if (loc != null) {
                                    config.setLocale(loc);
                                }

                                if (ltr) {
                                    config.setLayoutDirection(new Locale("en-us"));
                                }
                                context = context.createConfigurationContext(config);
                            }

                            param.args[0] = context;
                        }
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log(t);
            }

            // Override the default Locale if one is defined (not res-related, here)
            Locale packageLocale = getPackageSpecificLocale(prefs, lpparam.packageName);
            if (packageLocale != null) {
                Locale.setDefault(packageLocale);
            }
        }
    }

    static void soundPool(XC_LoadPackage.LoadPackageParam lpparam, XSharedPreferences prefs) {
        if (Core.isActive(prefs, lpparam.packageName, Common.PREF_MUTE)) {
            try {
                // Hook the AudioTrack API
                XposedHelpers.findAndHookMethod(AudioTrack.class, "play",
                        XC_MethodReplacement.returnConstant(null));

                // Hook the JetPlayer API
                XposedHelpers.findAndHookMethod(JetPlayer.class, "play",
                        XC_MethodReplacement.returnConstant(null));

                // Hook the MediaPlayer API
                XC_MethodHook displayHook = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        // Detect if video will be used for this media
                        if (param.args[0] != null) {
                            XposedHelpers.setAdditionalInstanceField(param.thisObject, "HasVideo", true);
                        } else {
                            XposedHelpers.removeAdditionalInstanceField(param.thisObject, "HasVideo");
                        }
                    }
                };
                XposedHelpers.findAndHookMethod(MediaPlayer.class, "setSurface", Surface.class, displayHook);
                XposedHelpers.findAndHookMethod(MediaPlayer.class, "setDisplay", SurfaceHolder.class, displayHook);
                XposedHelpers.findAndHookMethod(MediaPlayer.class, "start", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (XposedHelpers.getAdditionalInstanceField(param.thisObject, "HasVideo") != null) {
                            // Video will be used - still start the media but with muted volume
                            ((MediaPlayer) param.thisObject).setVolume(0, 0);
                        } else {
                            // No video - skip starting to play the media altogether
                            param.setResult(null);
                        }
                    }
                });

                // Hook the SoundPool API
                XposedHelpers.findAndHookMethod(SoundPool.class, "play", int.class,
                        float.class, float.class, int.class, int.class, float.class,
                        XC_MethodReplacement.returnConstant(0));
                XposedHelpers.findAndHookMethod(SoundPool.class, "resume", int.class,
                        XC_MethodReplacement.returnConstant(null));
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
    }

    static void legacyMenu(XC_LoadPackage.LoadPackageParam lpparam, XSharedPreferences prefs) {
        if (Core.isActive(prefs, lpparam.packageName, Common.PREF_LEGACY_MENU)) {
            try {
                XposedHelpers.findAndHookMethod(ViewConfiguration.class, "hasPermanentMenuKey",
                        XC_MethodReplacement.returnConstant(true));
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
    }

    private static Locale getPackageSpecificLocale(XSharedPreferences prefs, String packageName) {
        String locale = prefs.getString(packageName + Common.PREF_LOCALE, null);
        if (locale == null || locale.isEmpty())
            return null;

        String[] localeParts = locale.split("_", 3);
        String language = localeParts[0];
        String region = (localeParts.length >= 2) ? localeParts[1] : "";
        String variant = (localeParts.length >= 3) ? localeParts[2] : "";
        return new Locale(language, region, variant);
    }
}
