package ru.bluecat.android.xposed.mods.appsettings;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.appcompat.app.AlertDialog;

public class ThemeUtil {

    public static boolean isNightTheme(Activity context, SharedPreferences prefs) {
        int theme = prefs.getInt(Common.PREF_THEME, 1);
        return (theme == 2 && (context.getResources().getConfiguration().uiMode &
                Configuration.UI_MODE_NIGHT_YES) > 0) || theme == 1;
    }

    private static int getSelectTheme(Activity context, SharedPreferences prefs) {
        int theme = R.style.Theme_Main_Light;
        if (isNightTheme(context, prefs)) {
            theme = R.style.Theme_Main_Dark;
        }
        return theme;
    }

    public static void setTheme(Activity context, SharedPreferences prefs) {
        context.setTheme(getSelectTheme(context, prefs));
    }

    public static void setThemeDialog(Activity context, SharedPreferences prefs) {
        AlertDialog.Builder mBuilder = new AlertDialog.Builder(context);
        SharedPreferences.Editor editor = prefs.edit();
        mBuilder.setTitle(R.string.theme_title);
        mBuilder.setSingleChoiceItems(R.array.theme_texts, prefs.getInt(Common.PREF_THEME, 1),
                (dialogInterface, i) -> editor.putInt(Common.PREF_THEME, i));
        mBuilder.setPositiveButton(R.string.common_button_ok, (dialogInterface, which) -> {
            dialogInterface.dismiss();
            editor.apply();
            context.recreate();
        });
        mBuilder.setNegativeButton(R.string.common_button_cancel,null);
        AlertDialog alert = mBuilder.create();
        alert.show();
    }
}
