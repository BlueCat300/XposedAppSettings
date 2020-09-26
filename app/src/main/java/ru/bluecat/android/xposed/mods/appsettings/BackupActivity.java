package ru.bluecat.android.xposed.mods.appsettings;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class BackupActivity extends Activity {

    static boolean restoreSuccessful;
    private static String backupFileName;

    static void startBackupActivity(XposedModActivity activity, boolean isRestore) {
        Intent i = new Intent(activity, BackupActivity.class);
        i.putExtra("backup", isRestore);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showFilePicker(getIntent().getBooleanExtra("backup", false));
    }

    private void showFilePicker(boolean isRestore) {
        try {
            String action = Intent.ACTION_CREATE_DOCUMENT;
            int requestCode = 1;
            if(isRestore) {
                action = Intent.ACTION_OPEN_DOCUMENT;
                requestCode = 2;
            }

            Intent intent = new Intent(action);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            if(!isRestore) {
                backupFileName = createUniqueBackupName();
                intent.putExtra(Intent.EXTRA_TITLE, backupFileName);
            }
            startActivityForResult(intent, requestCode);

        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.imp_exp_file_picker_error, Toast.LENGTH_LONG).show();
            Log.e(Common.TAG, e.toString());
            e.printStackTrace();
            finish();
        }
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            Uri treeUri = resultData.getData();
            if (treeUri != null) {
                new BackupTask(this).execute(treeUri);
            }
        }
        if (requestCode == 2 && resultCode == Activity.RESULT_OK) {
            Uri documentUri = resultData.getData();
            if (documentUri != null) {
                new RestoreTask(this).execute(documentUri);
            }
        }
        finish();
    }

    private static String createUniqueBackupName() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);
        Date formatedDate = new Date(System.currentTimeMillis());
        return "AppSettings_" + formatter.format(formatedDate) + ".backup";
    }

    private static class BackupTask extends AsyncTask<Uri, String, String> {

        private WeakReference<BackupActivity> activityReference;

        BackupTask(BackupActivity context) {
            activityReference = new WeakReference<>(context);
        }

        @Override
        protected String doInBackground(Uri... params) {
            boolean backupSuccessful = false;
            BackupActivity activity = activityReference.get();

            ObjectOutputStream output = null;
            String error = null;
            try {
                output = new ObjectOutputStream(activity.getContentResolver().openOutputStream(params[0]));
                Context ctx = ContextCompat.createDeviceProtectedStorageContext(activity);
                if (ctx == null) {
                    ctx = activity;
                }
                SharedPreferences pref = ctx.getSharedPreferences(Common.PREFS, Context.MODE_PRIVATE);
                output.writeObject(pref.getAll());
                backupSuccessful = true;
            } catch (IOException e) {
                error = e.getMessage();
                e.printStackTrace();
            } finally {
                try {
                    if (output != null) {
                        output.flush();
                        output.close();
                    }
                } catch (IOException e) {
                    error = e.getMessage();
                    e.printStackTrace();
                }
            }

            if(backupSuccessful) {
                return activity.getResources().getString(R.string.imp_exp_backup_completed, backupFileName);
            } else {
                return activity.getResources().getString(R.string.imp_exp_backup_error, error);
            }
        }

        @Override
        protected void onPostExecute(String result) {
            Toast.makeText(activityReference.get(), result, Toast.LENGTH_LONG).show();
        }
    }

    private static class RestoreTask extends AsyncTask<Uri, String, String> {
        private WeakReference<BackupActivity> activityReference;

        RestoreTask(BackupActivity context) {
            activityReference = new WeakReference<>(context);
        }

        @Override
        protected String doInBackground(Uri... params) {
            BackupActivity activity = activityReference.get();

            ObjectInputStream input = null;
            String error = null;
            try {
                input = new ObjectInputStream(activity.getContentResolver().openInputStream(params[0]));
                Context ctx = ContextCompat.createDeviceProtectedStorageContext(activity);
                if (ctx == null) {
                    ctx = activity;
                }
                SharedPreferences.Editor prefEdit = ctx.getSharedPreferences(Common.PREFS, Context.MODE_PRIVATE).edit();
                prefEdit.clear();
                @SuppressWarnings("unchecked")
                Map<String, ?> entries = (Map<String, ?>) input.readObject();
                for (Map.Entry<String, ?> entry : entries.entrySet()) {
                    Object v = entry.getValue();
                    String key = entry.getKey();

                    if (v instanceof Boolean)
                        prefEdit.putBoolean(key, (Boolean) v);
                    else if (v instanceof Float)
                        prefEdit.putFloat(key, (Float) v);
                    else if (v instanceof Integer)
                        prefEdit.putInt(key, (Integer) v);
                    else if (v instanceof Long)
                        prefEdit.putLong(key, (Long) v);
                    else if (v instanceof String)
                        prefEdit.putString(key, ((String) v));
                }
                prefEdit.apply();
                restoreSuccessful = true;
            } catch (IOException | ClassNotFoundException e) {
                error = e.getMessage();
                e.printStackTrace();
            } finally {
                try {
                    if (input != null) {
                        input.close();
                    }
                } catch (IOException e) {
                    error = e.getMessage();
                    e.printStackTrace();
                }
            }

            if(restoreSuccessful) {
                return activity.getResources().getString(R.string.imp_exp_restored);
            } else {
                return activity.getResources().getString(R.string.imp_exp_restore_error, error);
            }
        }

        @Override
        protected void onPostExecute(String result) {
            Toast.makeText(activityReference.get(), result, Toast.LENGTH_LONG).show();
        }
    }
}
