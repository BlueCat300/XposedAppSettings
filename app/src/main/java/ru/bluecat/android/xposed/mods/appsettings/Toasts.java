package ru.bluecat.android.xposed.mods.appsettings;

import android.app.Activity;
import android.os.Build;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.AlignmentSpan;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.lang3.tuple.Pair;

import java.util.Objects;

public class Toasts {

    public static void showToast(Activity activity, Pair<String, Integer> id, String arg, int length) {
        String message;
        if(arg != null) {
            message = activity.getString(id.getRight(), arg);
        } else {
            message = id.getLeft() != null ? id.getLeft() : activity.getString(id.getRight());
        }
        Toast toast;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            toast = Toast.makeText(activity, message, length);
            TextView centredMessage = Objects.requireNonNull(toast.getView()).findViewById(android.R.id.message);
            if(centredMessage != null) centredMessage.setGravity(Gravity.CENTER);
        } else {
            SpannableStringBuilder centredMessage = new SpannableStringBuilder(message);
            AlignmentSpan alignmentSpan = new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER);
            centredMessage.setSpan(alignmentSpan, 0, message.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            toast = Toast.makeText(activity, centredMessage, length);
        }
        toast.show();
    }
}
