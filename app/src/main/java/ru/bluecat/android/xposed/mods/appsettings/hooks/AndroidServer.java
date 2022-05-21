package ru.bluecat.android.xposed.mods.appsettings.hooks;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import ru.bluecat.android.xposed.mods.appsettings.Common;

public class AndroidServer {

    private static Object processRecord;
    private static final String core = "com.android.server";

    static void resident(XC_LoadPackage.LoadPackageParam lpparam, XSharedPreferences prefs) {
        try {
            // Hook one of the several variations of ActivityStack.realStartActivityLocked from different ROMs
            ClassLoader classLoader = lpparam.classLoader;
            String targetMethod = "realStartActivityLocked";
            String targetClass;
            String param1;
            String param2;
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                targetClass = core + ".am.ActivityStackSupervisor";
                param1 = core + ".am.ActivityRecord";
                param2 = core + ".am.ProcessRecord";
            } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                targetClass = core + ".wm.ActivityStackSupervisor";
                param1 = core + ".wm.ActivityRecord";
                param2 = core + ".wm.WindowProcessController";
            } else {
                targetClass = core + ".wm.ActivityTaskSupervisor";
                param1 = core + ".wm.ActivityRecord";
                param2 = core + ".wm.WindowProcessController";
            }
            Method mthRealStartActivityLocked = XposedHelpers.findMethodExact(targetClass, classLoader,
                    targetMethod, param1, param2, boolean.class, boolean.class);

            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                XposedHelpers.findAndHookConstructor(core + ".am.ProcessRecord",
                        classLoader, core + ".am.ActivityManagerService",
                        "android.content.pm.ApplicationInfo", String.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        processRecord = param.thisObject;
                    }
                });
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                XposedHelpers.findAndHookConstructor(core + ".am.ProcessStateRecord",
                        classLoader, core + ".am.ProcessRecord", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        processRecord = param.thisObject;
                    }
                });
            }

            XposedBridge.hookMethod(mthRealStartActivityLocked, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    String pkgName = (String) XposedHelpers.getObjectField(param.args[0], "packageName");
                    if (Core.isActive(prefs, pkgName, Common.PREF_RESIDENT)) {
                        int adj = -12;
                        Object proc = XposedHelpers.getObjectField(param.args[0], "app");

                        // Override the *Adj values if meant to be resident in memory
                        if (proc != null) {
                            String max = "maxAdj";
                            String curRaw = "curRawAdj";
                            String setRaw = "setRawAdj";
                            String cur = "curAdj";
                            String set = "setAdj";
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                proc = processRecord;
                                curRaw = "mCurRawAdj";
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                max = "mMaxAdj";
                                setRaw = "mSetRawAdj";
                                cur = "mCurAdj";
                                set = "mSetAdj";
                            }

                            XposedHelpers.setIntField(proc, max, adj);
                            XposedHelpers.setIntField(proc, curRaw, adj);
                            XposedHelpers.setIntField(proc, setRaw, adj);
                            XposedHelpers.setIntField(proc, cur, adj);
                            XposedHelpers.setIntField(proc, set, adj);
                        }
                    }
                }
            });
        } catch (Throwable e) {
            XposedBridge.log(e);
        }
    }

    static void recentTasks(XC_LoadPackage.LoadPackageParam lpparam, XSharedPreferences prefs) {
        try {
            String activityRecordClass = core + ".am.ActivityRecord";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                activityRecordClass = core + ".wm.ActivityRecord";
            }
            XposedBridge.hookAllConstructors(XposedHelpers.findClass(activityRecordClass,
                    lpparam.classLoader), new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    ActivityInfo aInfo = (ActivityInfo) XposedHelpers.getObjectField(param.thisObject, "info");
                    if (aInfo == null) return;
                    String pkgName = aInfo.packageName;
                    if (prefs.getInt(pkgName + Common.PREF_RECENTS_MODE, Common.PREF_RECENTS_DEFAULT) > 0) {
                        int recentsMode = prefs.getInt(pkgName + Common.PREF_RECENTS_MODE, Common.PREF_RECENTS_DEFAULT);
                        if (recentsMode == Common.PREF_RECENTS_DEFAULT) return;
                        Intent intent = (Intent) XposedHelpers.getObjectField(param.thisObject, "intent");
                        if (recentsMode == Common.PREF_RECENTS_FORCE) {
                            int flags = (intent.getFlags() & ~Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                            intent.setFlags(flags);
                        } else if (recentsMode == Common.PREF_RECENTS_PREVENT) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                        }
                    }
                }
            });
        } catch (Throwable e) {
            XposedBridge.log(e);
        }
    }

    static void UnrestrictedGetTasks(XC_LoadPackage.LoadPackageParam lpparam, XSharedPreferences prefs) {
        try {
            //Disable restricting Android API to get recent tasks in Lollipop and newer.
            //https://github.com/pylerSM/UnrestrictedGetTasks
            String recentTasksClass = core + ".am.ActivityManagerService";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                recentTasksClass = core + ".wm.ActivityTaskManagerService";
            }
            XposedHelpers.findAndHookMethod(recentTasksClass, lpparam.classLoader, "isGetTasksAllowed",
                    String.class, int.class, int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Context mContext = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                    String callingApp = mContext.getPackageManager().getNameForUid((Integer) param.args[2]);
                    if (Common.MY_PACKAGE_NAME.equals(callingApp) || Core.isActive(prefs, callingApp, Common.PREF_RECENT_TASKS)) {
                        param.setResult(true);
                    }
                }
            });
        } catch (Throwable e) {
            XposedBridge.log(e);
        }
    }

    static void notificationManager(XC_LoadPackage.LoadPackageParam lpparam, XSharedPreferences prefs) {
        try {
            ClassLoader classLoader = lpparam.classLoader;
            XC_MethodHook notifyHook = new XC_MethodHook() {
                @SuppressWarnings("deprecation")
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String packageName = (String) param.args[0];
                    Notification n = (Notification) param.args[6];

                    if (!Core.isActive(prefs, packageName)) {
                        return;
                    }

                    if (Core.isActive(prefs, packageName, Common.PREF_INSISTENT_NOTIF)) {
                        n.flags |= Notification.FLAG_INSISTENT;
                    }
                    int ongoingNotif = prefs.getInt(packageName + Common.PREF_ONGOING_NOTIF,
                            Common.ONGOING_NOTIF_DEFAULT);
                    if (ongoingNotif == Common.ONGOING_NOTIF_FORCE) {
                        n.flags |= Notification.FLAG_ONGOING_EVENT;
                    } else if (ongoingNotif == Common.ONGOING_NOTIF_PREVENT) {
                        n.flags &= ~Notification.FLAG_ONGOING_EVENT & ~Notification.FLAG_FOREGROUND_SERVICE;
                    }

                    if (Core.isActive(prefs, packageName, Common.PREF_MUTE)) {
                        n.sound = null;
                        n.flags &= ~Notification.DEFAULT_SOUND;
                    }
                }
            };
            String notificationHookedMethod = "enqueueNotificationInternal";
            String notificationHookedClass = "com.android.server.notification.NotificationManagerService";
            XposedHelpers.findAndHookMethod(notificationHookedClass, classLoader, notificationHookedMethod,
                    String.class, String.class, int.class, int.class, String.class, int.class,
                    Notification.class, int.class, notifyHook);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
