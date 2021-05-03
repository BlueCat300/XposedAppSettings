package ru.bluecat.android.xposed.mods.appsettings;

import android.annotation.SuppressLint;
import android.app.Notification;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT;

public class Common {

	public static String TAG = "AppSettingsReborn";
	public static String MY_PACKAGE_NAME = BuildConfig.APPLICATION_ID;
	public static String REBOOT = "android.permission.REBOOT";

	public static String ACTION_PERMISSIONS = "update_permissions";

	public static String PREFS = "ModSettings";

	public static String PREF_DEFAULT = "default";
	public static String PREF_ACTIVE = "/active";
	public static String PREF_DPI = "/dpi";
	public static String PREF_FONT_SCALE = "/font-scale";
	public static String PREF_LOCALE = "/locale";
	public static String PREF_SCREEN = "/screen";
	public static String PREF_XLARGE = "/tablet";
	public static String PREF_SCREENSHOT = "/screenshot";
	public static String PREF_RESIDENT = "/resident";
	public static String PREF_NO_FULLSCREEN_IME = "/no-fullscreen-ime";
	public static String PREF_NO_BIG_NOTIFICATIONS = "/no-big-notifications";
	public static String PREF_INSISTENT_NOTIF = "/insistent-notif";
	public static String PREF_ONGOING_NOTIF = "/ongoing-notif";
	public static String PREF_NOTIF_PRIORITY = "/notif-priority";
	public static String PREF_REVOKEPERMS = "/revoke-perms";
	public static String PREF_REVOKELIST = "/revoke-list";
	public static String PREF_FULLSCREEN = "/fullscreen";
	public static String PREF_AUTO_HIDE_FULLSCREEN = "/auto-hide-fullscreen";
	public static String PREF_NO_TITLE = "/no-title";
	public static String PREF_ALLOW_ON_LOCKSCREEN = "/allow-on-lockscreen";
	public static String PREF_SCREEN_ON = "/screen-on";
	public static String PREF_ORIENTATION = "/orientation";
	public static String PREF_RECENTS_MODE = "/recents-mode";
	public static String PREF_MUTE = "/mute";
	public static String PREF_LEGACY_MENU = "/legacy-menu";
	public static String PREF_RECENT_TASKS = "/recent-tasks";

	public static int[] swdp = { 0, 320, 480, 600, 800, 1000, 1080, 1440 };
	public static int[] wdp = { 0, 320, 480, 600, 800, 1000, 1080, 1440 };
	public static int[] hdp = { 0, 480, 854, 1024, 1280, 1600, 1920, 2560 };

	@SuppressLint("InlinedApi")
	public static int[] orientationCodes = { Integer.MIN_VALUE,
		SCREEN_ORIENTATION_UNSPECIFIED,
		SCREEN_ORIENTATION_PORTRAIT, SCREEN_ORIENTATION_LANDSCAPE,
		SCREEN_ORIENTATION_SENSOR,
		SCREEN_ORIENTATION_SENSOR_PORTRAIT, SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
		SCREEN_ORIENTATION_REVERSE_PORTRAIT, SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
		SCREEN_ORIENTATION_FULL_SENSOR,
		// These require API 18
		SCREEN_ORIENTATION_USER_PORTRAIT, SCREEN_ORIENTATION_USER_LANDSCAPE,
			SCREEN_ORIENTATION_FULL_USER };
	public static int[] orientationLabels = { R.string.settings_default,
		R.string.settings_ori_normal,
		R.string.settings_ori_portrait, R.string.settings_ori_landscape,
		R.string.settings_ori_forceauto,
		R.string.settings_ori_portrait_sensor, R.string.settings_ori_landscape_sensor,
		R.string.settings_ori_portrait_reverse, R.string.settings_ori_landscape_reverse,
		R.string.settings_ori_forceauto_4way,
		// These require API 18
		R.string.settings_ori_portrait_user, R.string.settings_ori_landscape_user,
		R.string.settings_ori_user_4way };

	@SuppressLint("InlinedApi")
	public static int[] notifPriCodes = { Integer.MIN_VALUE,
		Notification.PRIORITY_MAX, Notification.PRIORITY_HIGH,
		Notification.PRIORITY_DEFAULT,
		Notification.PRIORITY_LOW, Notification.PRIORITY_MIN };
	public static int[] notifPriLabels = { R.string.settings_default,
		R.string.settings_npri_max, R.string.settings_npri_high,
		R.string.settings_npri_normal,
		R.string.settings_npri_low,
		R.string.settings_npri_min };

	public static int FULLSCREEN_DEFAULT = 0;
	public static int FULLSCREEN_FORCE = 1;
	public static int FULLSCREEN_PREVENT = 2;
	public static int FULLSCREEN_IMMERSIVE = 3;

	public static int ONGOING_NOTIF_DEFAULT = 0;
	public static int ONGOING_NOTIF_FORCE = 1;
	public static int ONGOING_NOTIF_PREVENT = 2;

	public static int PREF_RECENTS_DEFAULT = 0;
	public static int PREF_RECENTS_FORCE = 1;
	public static int PREF_RECENTS_PREVENT = 2;

    public static int PREF_SCREENSHOT_DEFAULT = 0;
    public static int PREF_SCREENSHOT_ALLOW = 1;
    public static int PREF_SCREENSHOT_PREVENT = 2;

}
