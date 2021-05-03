package ru.bluecat.android.xposed.mods.appsettings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.FileObserver;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.Objects;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

// https://github.com/chiehmin/MinMinGuard/commit/0bd9e353c63a44d9c948b68365bf26675a3a3104
public class PrefFileManager {
    private final Context mContext;
    private final FileObserver mFileObserver;
    private boolean mSelfAttrChange;

    private PrefFileManager(Context context) {
        mContext = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !ContextCompat.isDeviceProtectedStorage(context)
                ? ContextCompat.createDeviceProtectedStorageContext(context) : context;

        mFileObserver = new FileObserver(Objects.requireNonNull(mContext).getFilesDir().
                getParentFile() + "/shared_prefs", FileObserver.ATTRIB) {
            @Override
            public void onEvent(int event, String path) {
                if ((event & FileObserver.ATTRIB) != 0) {
                    onFileAttributesChanged(path);
                }
            }
        };
        mFileObserver.startWatching();
    }

    public static synchronized PrefFileManager getInstance(Context context) {
        if (context == null) throw new IllegalArgumentException("Context cannot be null");
        if (context.getApplicationContext() != null) context = context.getApplicationContext();
        return new PrefFileManager(context);
    }

    @SuppressLint("SetWorldReadable")
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void fixFolderPermissionsAsync() {
        Observable.fromCallable(() -> {
            File pkgFolder = mContext.getFilesDir().getParentFile();
            if (Objects.requireNonNull(pkgFolder).exists()) {
                pkgFolder.setExecutable(true, false);
                pkgFolder.setReadable(true, false);
            }
            File cacheFolder = mContext.getCacheDir();
            if (cacheFolder.exists()) {
                cacheFolder.setExecutable(true, false);
                cacheFolder.setReadable(true, false);
            }
            File filesFolder = mContext.getFilesDir();
            if (filesFolder.exists()) {
                filesFolder.setExecutable(true, false);
                filesFolder.setReadable(true, false);
                for (File f : Objects.requireNonNull(filesFolder.listFiles())) {
                    f.setExecutable(true, false);
                    f.setReadable(true, false);
                }
            }
            return true;

        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe();
    }

    public void onPause() {
        mFileObserver.stopWatching();
        fixPermissions(true);
    }

    public void onResume() {
        fixPermissions(true);
        mFileObserver.startWatching();
    }

    @SuppressLint("SetWorldReadable")
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void fixPermissions(boolean force) {
        File sharedPrefsFolder = new File(mContext.getFilesDir().getParentFile(), "shared_prefs");
        if (sharedPrefsFolder.exists()) {
            sharedPrefsFolder.setExecutable(true, false);
            sharedPrefsFolder.setReadable(true, false);
            File f = new File(sharedPrefsFolder, Common.PREFS + ".xml");
            if (f.exists()) {
                mSelfAttrChange = !force;
                f.setReadable(true, false);
            }
        }
    }

    private void onFileAttributesChanged(String path) {
        if (path != null && path.endsWith(Common.PREFS + ".xml")) {
            if (mSelfAttrChange) {
                mSelfAttrChange = false;
                return;
            }
            fixPermissions(false);
        }
    }
}

