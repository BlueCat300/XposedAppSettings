package ru.bluecat.android.xposed.mods.appsettings;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.widget.Toast;

import org.apache.commons.lang3.tuple.Pair;

public class Utils {

    public static void showToast(Context context, Pair<String, Integer> id, String arg, int length) {
        String message;
        if(arg != null) {
            message = context.getString(id.getRight(), arg);
        } else {
            message = id.getLeft() != null ? id.getLeft() : context.getString(id.getRight());
        }
        Toast.makeText(context, message, length).show();
    }

    public static boolean isPortrait(Activity context) {
		return context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
	}
}
