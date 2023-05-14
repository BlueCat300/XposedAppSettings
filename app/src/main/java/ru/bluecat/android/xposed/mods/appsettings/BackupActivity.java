package ru.bluecat.android.xposed.mods.appsettings;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.apache.commons.lang3.tuple.Pair;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class BackupActivity extends AppCompatActivity {

    private static SharedPreferences prefs;
    private boolean job;
    private ActivityResultLauncher<Intent> onActivityResult;

    public static void startActivity(MainActivity activity, boolean isRestore) {
        Intent i = new Intent(activity, BackupActivity.class);
        i.putExtra("backup", isRestore);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        job = getIntent().getBooleanExtra("backup", false);
        registerActivityAction();
        showFilePicker();
    }

    public void registerActivityAction() {
        onActivityResult = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                Intent data = result.getData();
                if (data != null) {
                    Uri treeUri = data.getData();
                    if(job) restoreTask(this, treeUri);
                    else backupTask(this, treeUri);
                }
            }
            finish();
        });
    }

    private void showFilePicker() {
        try {
            String action;
            if(job) action = Intent.ACTION_OPEN_DOCUMENT;
            else action = Intent.ACTION_CREATE_DOCUMENT;
            Intent intent = new Intent(action);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            if(!job) intent.putExtra(Intent.EXTRA_TITLE, createUniqueBackupName());
            onActivityResult.launch(intent);
        } catch(ActivityNotFoundException e) {
            Utils.showToast(this, Pair.of(null,
                    R.string.imp_exp_file_picker_error), null, Toast.LENGTH_LONG);
            finish();
        }
    }

    private static String createUniqueBackupName() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);
        Date formatedDate = new Date(System.currentTimeMillis());
        return "AppSettings_" + formatter.format(formatedDate) + ".backup";
    }

    @SuppressLint("WorldReadableFiles")
    private static void backupTask(BackupActivity activity, Uri treeUri) {
        Observable.fromCallable(() -> {
            ObjectOutputStream output;
            try {
                //noinspection deprecation
                prefs = activity.getSharedPreferences(Constants.PREFS, Context.MODE_WORLD_READABLE);
            } catch (SecurityException e) {
                Utils.showToast(activity, Pair.of(e.getMessage(), 0), null, Toast.LENGTH_LONG);
                activity.finish();
            }
            if(prefs != null) {
                output = new ObjectOutputStream(activity.getContentResolver().openOutputStream(treeUri));
                output.writeObject(prefs.getAll());
                output.flush();
                output.close();
            }
            return true;

        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Observer<>() {
                @Override
                public void onSubscribe(@NonNull Disposable d) {
                }

                @Override
                public void onNext(@NonNull Boolean progress) {
                }

                @Override
                public void onError(@NonNull Throwable e) {
                    Utils.showToast(activity, Pair.of(null, R.string.imp_exp_backup_error),
                            e.getMessage(), Toast.LENGTH_LONG);
                }

                @Override
                public void onComplete() {
                    MainActivity.showBackupSnackbar(R.string.imp_exp_backup_completed);
                }
            });
    }

    @SuppressLint("WorldReadableFiles")
    private static void restoreTask(BackupActivity activity, Uri treeUri) {
        Observable.fromCallable(() -> {
            ObjectInputStream input;
            try {
                //noinspection deprecation
                prefs = activity.getSharedPreferences(Constants.PREFS, Context.MODE_WORLD_READABLE);
            } catch (SecurityException e) {
                Utils.showToast(activity, Pair.of(e.getMessage(), 0), null, Toast.LENGTH_LONG);
                activity.finish();
            }
            if(prefs != null) {
                input = new ObjectInputStream(activity.getContentResolver().openInputStream(treeUri));
                SharedPreferences.Editor prefEdit = prefs.edit();
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
                input.close();
            }
            return true;

        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<>() {
                @Override
                public void onSubscribe(@NonNull Disposable d) { }

                @Override
                public void onNext(@NonNull Boolean progress) { }

                @Override
                public void onError(@NonNull Throwable e) {
                    Utils.showToast(activity, Pair.of(null, R.string.imp_exp_restore_error),
                            e.getMessage(), Toast.LENGTH_LONG);
                }

                @Override
                public void onComplete() {
                    MainActivity.refreshAppsAfterChanges(true);
                }
            });
    }
}
