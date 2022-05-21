package ru.bluecat.android.xposed.mods.appsettings;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RecentTaskInfo;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.LinkMovementMethod;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.DividerDrawerItem;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;

import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import ru.bluecat.android.xposed.mods.appsettings.ui.FilterItemComponent;
import ru.bluecat.android.xposed.mods.appsettings.ui.FilterItemComponent.FilterState;
import ru.bluecat.android.xposed.mods.appsettings.ui.PermissionsListAdapter;

public class MainActivity extends AppCompatActivity {

	private static final ArrayList<ApplicationInfo> appList = new ArrayList<>();
	private static ArrayList<ApplicationInfo> filteredAppList = new ArrayList<>();

	private static final Map<String, Set<String>> permUsage = new HashMap<>();
	private static final Map<String, Set<String>> sharedUsers = new HashMap<>();
	@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
	private static final Map<String, String> pkgSharedUsers = new HashMap<>();

	private static FilterState filterAppType;
	private static FilterState filterAppState;
	private static FilterState filterActive;
	private static String nameFilter;
	private static String filterPermissionUsage;

	private static List<SettingInfo> settings;

	private static MainActivity activityContext;
	private static SharedPreferences prefs;
	private static Menu optionsMenu;
	private Drawer drawer;
	private int mPosition;
	private ActivityResultLauncher<Intent> onActivityResult;

	@SuppressLint("WorldReadableFiles")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if(!isModuleActive()) {
			frameworkWarning(this);
			return;
		}
		try {
			//noinspection deprecation
			prefs = this.getSharedPreferences(Common.PREFS, Context.MODE_WORLD_READABLE);
		} catch (SecurityException e) {
			Toasts.showToast(this, Pair.of(e.getMessage(), 0), null, Toast.LENGTH_LONG);
			finish();
		}
		activityContext = this;
		setContentView(R.layout.main_activity);
		Toolbar toolbar = findViewById(R.id.listToolbar);
		setSupportActionBar(toolbar);
		setTitle(null);
		setUpDrawer();
		loadSettings();
		registerActivityAction();
		ListView list = findViewById(R.id.lstApps);
		registerForContextMenu(list);
		list.setOnItemClickListener((parent, view, position, id) -> {
			// Open settings activity when clicking on an application
			String pkgName = ((TextView) view.findViewById(R.id.app_package)).getText().toString();
			Intent i = new Intent(getApplicationContext(), AppSettingsActivity.class);
			i.putExtra("package", pkgName);
			mPosition = position;
			onActivityResult.launch(i);
		});
		refreshApps();
	}

	private void registerActivityAction() {
		onActivityResult = registerForActivityResult(
				new ActivityResultContracts.StartActivityForResult(), result -> {
			if (result.getResultCode() == Activity.RESULT_OK) {
				ListView list = findViewById(R.id.lstApps);
				if (mPosition >= list.getFirstVisiblePosition() && mPosition <= list.getLastVisiblePosition()) {
					View v = list.getChildAt(mPosition - list.getFirstVisiblePosition());
					list.getAdapter().getView(mPosition, v, list);
				} else if (mPosition == 2000) {
					list.invalidateViews();
				}
			}
		});
	}

	private void frameworkWarning(Activity context) {
		AlertDialog.Builder dialog = new AlertDialog.Builder(context);
		dialog.setTitle(R.string.app_framework_warning_title);
		dialog.setMessage(R.string.app_framework_warning_message);
		dialog.setPositiveButton(R.string.common_button_ok, (dialogInterface, which) -> {
			dialogInterface.dismiss();
			System.exit(0);
		});
		AlertDialog alert = dialog.create();
		alert.setCancelable(false);
		alert.setCanceledOnTouchOutside(false);
		alert.show();
	}

	public static boolean isNightTheme(Activity context) {
		return ((context.getResources().getConfiguration().uiMode
				& Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
	}

	private void setUpDrawer() {
		PrimaryDrawerItem backup = new PrimaryDrawerItem().withName(R.string.drawer_backup)
				.withIcon(R.drawable.ic_drawer_backup).withIdentifier(1).withSelectable(false);
		PrimaryDrawerItem restore = new PrimaryDrawerItem().withName(R.string.drawer_restore)
				.withIcon(R.drawable.ic_drawer_restore).withIdentifier(2).withSelectable(false);

		PrimaryDrawerItem filter = new PrimaryDrawerItem().withName(R.string.drawer_filter)
				.withIcon(R.drawable.ic_drawer_filter).withIdentifier(3).withSelectable(false);
		PrimaryDrawerItem permission = new PrimaryDrawerItem().withName(R.string.drawer_permission)
				.withIcon(R.drawable.ic_drawer_filter_permission).withIdentifier(4).withSelectable(false);

		PrimaryDrawerItem about = new PrimaryDrawerItem().withName(R.string.drawer_about)
				.withIcon(R.drawable.ic_drawer_about).withIdentifier(5).withSelectable(false);

		//noinspection rawtypes
		IDrawerItem[] itemsList = {backup, restore, new DividerDrawerItem(), filter, permission,
				new DividerDrawerItem(), about};

		DrawerBuilder drawerBuilder = new DrawerBuilder()
				.withActivity(this)
				.withHeader(R.layout.drawer_header)
				.withRootView(R.id.drawerLayout)
				.withToolbar(findViewById(R.id.listToolbar))
				.withActionBarDrawerToggle(true)
				.withActionBarDrawerToggleAnimated(true)
				.withHasStableIds(true)
				.withHeaderDivider(false)
				.withSelectedItem(-1)
				.addDrawerItems(itemsList)
				.withOnDrawerItemClickListener((view, position, drawerItem) -> {
					if (drawerItem != null) {
						long id = drawerItem.getIdentifier();
						if (id == 1) BackupActivity.startActivity(this, false);
						if (id == 2) BackupActivity.startActivity(this, true);
						if (id == 3) appFilter();
						if (id == 4) permissionFilter();
						if (id == 5) showAboutDialog();
					}
					return false;
				});
		drawer = drawerBuilder.build();
	}

	private boolean closeDrawer() {
		if (drawer.isDrawerOpen()) {
			drawer.closeDrawer();
			return true;
		}
		return false;
	}

	private void clearAllFilters() {
		filterAppType = FilterState.ALL;
		filterAppState = FilterState.ALL;
		filterActive = FilterState.ALL;
		for (SettingInfo setting : settings) {
			setting.filter = FilterState.ALL;
		}

		SearchView searchV = findViewById(R.id.menu_searchApp);
		if (searchV.isShown()) {
			searchV.onActionViewCollapsed();
		}
		getListAdapter().getFilter().filter(nameFilter);
	}

	@Override
	public void onBackPressed() {
		if (!closeDrawer()) {
			clearAllFilters();
			super.onBackPressed();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_main, menu);
		optionsMenu = menu;
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.menu_refresh) {
			refreshApps();
		} else if (id == R.id.menu_recents) {
			showRecents();
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		if (v.getId() == R.id.lstApps) {
			AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
			ApplicationInfo appInfo = filteredAppList.get(info.position);

			menu.setHeaderTitle(getPackageManager().getApplicationLabel(appInfo));
			getMenuInflater().inflate(R.menu.menu_app, menu);
			menu.findItem(R.id.menu_save).setVisible(false);

			AppSettingsActivity.updateMenuEntries(this, menu, appInfo.packageName);
		} else {
			super.onCreateContextMenu(menu, v, menuInfo);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
		String pkgName = filteredAppList.get(info.position).packageName;
		if (item.getItemId() == R.id.menu_app_launch) {
			Intent LaunchIntent = getPackageManager().getLaunchIntentForPackage(pkgName);
			startActivity(LaunchIntent);
			return true;
		} else if (item.getItemId() == R.id.menu_app_settings) {
			startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
					Uri.parse("package:" + pkgName)));
			return true;
		} else if (item.getItemId() == R.id.menu_app_store) {
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkgName)));
			return true;
		}
		return super.onContextItemSelected(item);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_SEARCH && (event.getFlags() & KeyEvent.FLAG_CANCELED) == 0) {
			SearchView searchV = findViewById(R.id.menu_searchApp);
			if (searchV.isShown()) {
				searchV.setIconified(false);
				return true;
			}
		}
		return super.onKeyUp(keyCode, event);
	}

	private void refreshApps() {
		appList.clear();
		// (re)load the list of apps in the background
		prepareAppsAdapter(this);
	}

	@SuppressLint("WorldReadableFiles")
	static void refreshAppsAfterChanges(boolean isRestored) {
		// Refresh preferences
		try {
			//noinspection deprecation
			prefs = activityContext.getSharedPreferences(Common.PREFS, Context.MODE_WORLD_READABLE);
		} catch (SecurityException e) {
			Toasts.showToast(activityContext, Pair.of(e.getMessage(), 0), null, Toast.LENGTH_LONG);
			activityContext.finish();
		}
		// Refresh listed apps (account for filters)
		getListAdapter().getFilter().filter(nameFilter);
		if(isRestored) showBackupSnackbar(R.string.imp_exp_restored);
	}

	static void showBackupSnackbar(int stringId) {
		new Handler(Looper.getMainLooper()).postDelayed(() -> {
			Snackbar snackbar = Snackbar
					.make(activityContext.findViewById(android.R.id.content), stringId, Snackbar.LENGTH_SHORT);
			snackbar.getView().setBackgroundColor(ContextCompat.getColor(activityContext, R.color.snackbar_background));
			TextView centredMessage = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
			centredMessage.setTextColor(ContextCompat.getColor(activityContext, R.color.day_night_text));
			centredMessage.setGravity(Gravity.CENTER);
			snackbar.show();
		}, 500);
	}

	private void loadSettings() {
		settings = new ArrayList<>();

		settings.add(new SettingInfo(Common.PREF_DPI, getString(R.string.settings_dpi)));
		settings.add(new SettingInfo(Common.PREF_FONT_SCALE, getString(R.string.settings_fontscale)));
		settings.add(new SettingInfo(Common.PREF_SCREEN, getString(R.string.settings_screen)));
		settings.add(new SettingInfo(Common.PREF_XLARGE, getString(R.string.settings_xlargeres)));
		settings.add(new SettingInfo(Common.PREF_LTR, getString(R.string.settings_ltr)));
		settings.add(new SettingInfo(Common.PREF_SCREENSHOT, getString(R.string.settings_screenshot)));
		settings.add(new SettingInfo(Common.PREF_LOCALE, getString(R.string.settings_locale)));
		settings.add(new SettingInfo(Common.PREF_FULLSCREEN, getString(R.string.settings_fullscreen)));
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
			settings.add(new SettingInfo(Common.PREF_AUTO_HIDE_FULLSCREEN, getString(R.string.settings_autofullscreen)));
		settings.add(new SettingInfo(Common.PREF_NO_TITLE, getString(R.string.settings_notitle)));
		settings.add(new SettingInfo(Common.PREF_SCREEN_ON, getString(R.string.settings_screenon)));
		settings.add(new SettingInfo(Common.PREF_ALLOW_ON_LOCKSCREEN, getString(R.string.settings_showwhenlocked)));
		settings.add(new SettingInfo(Common.PREF_RESIDENT, getString(R.string.settings_resident)));
		settings.add(new SettingInfo(Common.PREF_NO_FULLSCREEN_IME, getString(R.string.settings_nofullscreenime)));
		settings.add(new SettingInfo(Common.PREF_ORIENTATION, getString(R.string.settings_orientation)));
		settings.add(new SettingInfo(Common.PREF_INSISTENT_NOTIF, getString(R.string.settings_insistentnotif)));
		settings.add(new SettingInfo(Common.PREF_ONGOING_NOTIF, getString(R.string.settings_ongoingnotif)));
		settings.add(new SettingInfo(Common.PREF_RECENTS_MODE, getString(R.string.settings_recents_mode)));
		settings.add(new SettingInfo(Common.PREF_MUTE, getString(R.string.settings_mute)));
		settings.add(new SettingInfo(Common.PREF_LEGACY_MENU, getString(R.string.settings_legacy_menu)));
		settings.add(new SettingInfo(Common.PREF_RECENT_TASKS, getString(R.string.settings_recent_tasks)));
		settings.add(new SettingInfo(Common.PREF_REVOKEPERMS, getString(R.string.settings_permissions)));
	}

	private void showRecents() {
		ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		PackageManager pm = getPackageManager();

		final List<Map<String, Object>> data = new ArrayList<>();
		if (am != null) {
			//noinspection deprecation
			for (RecentTaskInfo task : am.getRecentTasks(30, ActivityManager.RECENT_WITH_EXCLUDED)) {
				Intent i = task.baseIntent;
				if (i.getComponent() == null)
					continue;

				Map<String, Object> entry = new HashMap<>();
				try {
					entry.put("image", pm.getActivityIcon(i));
				} catch (NameNotFoundException e) {
					entry.put("image", pm.getDefaultActivityIcon());
				}
				try {
					entry.put("label", pm.getActivityInfo(i.getComponent(), 0).loadLabel(pm).toString());
				} catch (NameNotFoundException e) {
					entry.put("label", "");
				}

				entry.put("package", i.getComponent().getPackageName());
				data.add(entry);
			}
		}
		String[] from = new String[] { "image", "label", "package" };
		int[] to = new int[] { R.id.recent_icon, R.id.recent_label, R.id.recent_package };

		SimpleAdapter adapter = new SimpleAdapter(this, data, R.layout.recent_item, from, to);
		adapter.setViewBinder((view, data1, textRepresentation) -> {
			if (view instanceof ImageView) {
				((ImageView) view).setImageDrawable((Drawable) data1);
				return true;
			}
			return false;
		});

		new AlertDialog.Builder(this)
			.setTitle(R.string.recents_title)
			.setAdapter(adapter, (dialog, which) -> {
				Intent i = new Intent(getApplicationContext(), AppSettingsActivity.class);
				i.putExtra("package", (String) data.get(which).get("package"));
				mPosition = 2000;
				onActivityResult.launch(i);
			}).show();
	}

	public static boolean isModuleActive() {
		return false;
	}

	private void showAboutDialog() {
		View vAbout;
		vAbout = View.inflate(this, R.layout.about, null);

		// Display the resources translator, or hide it if none
		String translator = getResources().getString(R.string.translator);
		TextView txtTranslation = vAbout.findViewById(R.id.about_translation);
		if (translator.isEmpty()) {
			txtTranslation.setVisibility(View.GONE);
		} else {
			txtTranslation.setText(getString(R.string.app_translation, translator));
			txtTranslation.setMovementMethod(LinkMovementMethod.getInstance());
		}

		// Clickable links
		((TextView) vAbout.findViewById(R.id.about_title)).setMovementMethod(LinkMovementMethod.getInstance());

		// Display the correct version
		try {
			((TextView) vAbout.findViewById(R.id.version)).setText(getString(R.string.app_version,
					getPackageManager().getPackageInfo(getPackageName(), 0).versionName));
		} catch (NameNotFoundException ignored) {
		}

		// Prepare and show the dialog
		AlertDialog.Builder dlgBuilder = new AlertDialog.Builder(this);
		dlgBuilder.setTitle(R.string.app_name);
		dlgBuilder.setCancelable(true);
		dlgBuilder.setIcon(R.drawable.ic_launcher);
		dlgBuilder.setPositiveButton(R.string.common_button_ok, null);
		dlgBuilder.setView(vAbout);
		dlgBuilder.show();
	}


	@SuppressWarnings("deprecation")
	private static void loadApps(ProgressDialog dialog, MainActivity activity) {
		appList.clear();
		permUsage.clear();
		sharedUsers.clear();
		pkgSharedUsers.clear();

		PackageManager pm = activity.getPackageManager();
		List<PackageInfo> pkgs = activity.getPackageManager().getInstalledPackages(PackageManager.GET_PERMISSIONS);
		dialog.setMax(pkgs.size());
		int i = 1;
		for (PackageInfo pkgInfo : pkgs) {
			dialog.setProgress(i++);

			ApplicationInfo appInfo = pkgInfo.applicationInfo;
			if (appInfo == null)
				continue;

			appInfo.name = appInfo.loadLabel(pm).toString();
			appList.add(appInfo);

			String[] perms = pkgInfo.requestedPermissions;
			if (perms != null)
				for (String perm : perms) {
					Set<String> permUsers = permUsage.computeIfAbsent(perm, k -> new TreeSet<>());
					permUsers.add(pkgInfo.packageName);
				}

			if (pkgInfo.sharedUserId != null) {
				Set<String> sharedUserPackages = sharedUsers.computeIfAbsent(pkgInfo.sharedUserId, k -> new TreeSet<>());
				sharedUserPackages.add(pkgInfo.packageName);

				pkgSharedUsers.put(pkgInfo.packageName, pkgInfo.sharedUserId);
			}
		}

		appList.sort((lhs, rhs) -> {
			if (lhs.name == null) {
				return -1;
			} else if (rhs.name == null) {
				return 1;
			} else {
				return lhs.name.toUpperCase().compareTo(rhs.name.toUpperCase());
			}
		});
	}

	private static void prepareAppList(MainActivity activity) {
		AppListAdapter appListAdapter = new AppListAdapter(activity, appList);
		((ListView) activity.findViewById(R.id.lstApps)).setAdapter(appListAdapter);
		appListAdapter.getFilter().filter(nameFilter);

		SearchView search = optionsMenu.findItem(R.id.menu_searchApp).getActionView()
				.findViewById(R.id.menu_searchApp);

		search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

			@Override
			public boolean onQueryTextSubmit(String query) {
				nameFilter = query;
				appListAdapter.getFilter().filter(nameFilter);
				search.clearFocus();
				return false;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				nameFilter = newText;
				appListAdapter.getFilter().filter(nameFilter);
				return false;
			}

		});
	}

	private static AppListAdapter getListAdapter() {
		return (AppListAdapter) ((ListView) activityContext.findViewById(R.id.lstApps)).getAdapter();
	}

	private void appFilter() {
		AppListAdapter appListAdapter = getListAdapter();
		appListAdapter.getFilter().filter(nameFilter);

		Dialog filterDialog;
		Map<String, FilterItemComponent> filterComponents;

		filterDialog = new Dialog(this, R.style.LegacyDialog);
		filterDialog.setContentView(R.layout.filter_dialog);
		filterDialog.setTitle(R.string.filter_title);
		filterDialog.setCancelable(true);
		filterDialog.setOwnerActivity(this);

		LinearLayout entriesView = filterDialog.findViewById(R.id.filter_entries);
		filterComponents = new HashMap<>();
		for (SettingInfo setting : settings) {
			FilterItemComponent component = new FilterItemComponent(this, setting.label, null, null, null);
			component.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
			component.setFilterState(setting.filter);
			entriesView.addView(component);
			filterComponents.put(setting.settingKey, component);
		}

		((FilterItemComponent) filterDialog.findViewById(R.id.fltAppType)).setFilterState(filterAppType);
		((FilterItemComponent) filterDialog.findViewById(R.id.fltAppState)).setFilterState(filterAppState);
		((FilterItemComponent) filterDialog.findViewById(R.id.fltActive)).setFilterState(filterActive);

		// Block or unblock the details based on the Active setting
		enableFilterDetails(!FilterState.UNCHANGED.equals(filterActive), filterComponents);
		((FilterItemComponent) filterDialog.findViewById(R.id.fltActive)).
				setOnFilterChangeListener((item, state) -> enableFilterDetails(!FilterState.UNCHANGED.equals(state), filterComponents));

		// Close the dialog with the possible options
		filterDialog.findViewById(R.id.btnFilterCancel).setOnClickListener(v1 -> filterDialog.dismiss());
		filterDialog.findViewById(R.id.btnFilterClear).setOnClickListener(v12 -> {
			filterAppType = FilterState.ALL;
			filterAppState = FilterState.ALL;
			filterActive = FilterState.ALL;
			for (SettingInfo setting : settings)
				setting.filter = FilterState.ALL;

			filterDialog.dismiss();
			appListAdapter.getFilter().filter(nameFilter);
		});
		filterDialog.findViewById(R.id.btnFilterApply).setOnClickListener(v13 -> {
			filterAppType = ((FilterItemComponent) filterDialog.findViewById(R.id.fltAppType)).getFilterState();
			filterAppState = ((FilterItemComponent) filterDialog.findViewById(R.id.fltAppState)).getFilterState();
			filterActive = ((FilterItemComponent) filterDialog.findViewById(R.id.fltActive)).getFilterState();
			for (SettingInfo setting : settings)
				setting.filter = Objects.requireNonNull(filterComponents.get(setting.settingKey)).getFilterState();

			filterDialog.dismiss();
			appListAdapter.getFilter().filter(nameFilter);
		});

		filterDialog.show();
	}

	private void enableFilterDetails(boolean enable, Map<String, FilterItemComponent> filterComponents) {
		for (FilterItemComponent component : filterComponents.values())
			component.setEnabled(enable);
	}

	private void permissionFilter() {
		AppListAdapter appListAdapter = getListAdapter();
		appListAdapter.getFilter().filter(nameFilter);

		AlertDialog.Builder bld = new AlertDialog.Builder(this);
		bld.setCancelable(true);
		bld.setTitle(R.string.perms_filter_title);

		List<String> perms = new LinkedList<>(permUsage.keySet());
		Collections.sort(perms);
		List<PermissionInfo> items = new ArrayList<>();
		PackageManager pm = this.getPackageManager();
		for (String perm : perms) {
			try {
				items.add(pm.getPermissionInfo(perm, 0));
			} catch (NameNotFoundException e) {
				PermissionInfo unknownPerm = new PermissionInfo();
				unknownPerm.name = perm;
				items.add(unknownPerm);
			}
		}
		PermissionsListAdapter adapter = new PermissionsListAdapter(this, items, new HashSet<>(), false);
		bld.setAdapter(adapter, (dialog, which) -> {
			filterPermissionUsage = Objects.requireNonNull(adapter.getItem(which)).name;
			appListAdapter.getFilter().filter(nameFilter);
		});

		View permsView = View.inflate(this, R.layout.permission_search, null);
		((SearchView) permsView.findViewById(R.id.searchPermission)).setOnQueryTextListener(new SearchView.OnQueryTextListener() {

			@Override
			public boolean onQueryTextSubmit(String query) {
				adapter.getFilter().filter(query);
				permsView.findViewById(R.id.searchPermission).clearFocus();
				return false;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				adapter.getFilter().filter(newText);
				return false;
			}
		});
		//bld.setView(permsView);

		bld.setNegativeButton(R.string.common_button_cancel, (dialog, which) -> {
			filterPermissionUsage = null;
			appListAdapter.getFilter().filter(nameFilter);
		});

		AlertDialog dialog = bld.create();
		dialog.getListView().setFastScrollEnabled(true);

		dialog.show();
	}

	// Handle background loading of apps
	@SuppressWarnings("deprecation")
	private static void prepareAppsAdapter(MainActivity activity) {
		ProgressDialog dialog = new ProgressDialog(activity, R.style.Theme_Main_Dialog_Alert);
		dialog.setMessage(activity.getResources().getString(R.string.app_loading));
		dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		dialog.setCancelable(false);

		Observable.fromCallable(() -> {
			if (appList.size() == 0) {
				loadApps(dialog, activity);
			}
			return true;

		}).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
			.subscribe(new Observer<>() {
				@Override
				public void onSubscribe(@NonNull Disposable d) {
					dialog.show();
				}

				@Override
				public void onNext(@NonNull Boolean progress) {
				}

				@Override
				public void onError(@NonNull Throwable e) {
				}

				@Override
				public void onComplete() {
					prepareAppList(activity);
					dialog.dismiss();
				}
			});
	}

	/** Hold filter state and other info for each setting key */
	private static class SettingInfo {
		String settingKey;
		String label;
		FilterState filter;

		SettingInfo(String setting, String label) {
			this.settingKey = setting;
			this.label = label;
			filter = FilterState.ALL;
		}
	}


	private static class AppListFilter extends Filter {

		private final AppListAdapter adapter;
		private final Activity activityReference;

		AppListFilter(AppListAdapter adapter, Activity context) {
			super();
			this.adapter = adapter;
			activityReference = context;
		}

		@SuppressLint("WorldReadableFiles")
		@Override
		protected FilterResults performFiltering(CharSequence constraint) {
			// NOTE: this function is *always* called from a background thread, and
			// not the UI thread.

			ArrayList<ApplicationInfo> items;
			synchronized (this) {
				items = new ArrayList<>(appList);
			}
			try {
				//noinspection deprecation
				prefs = activityReference.getSharedPreferences(Common.PREFS, Context.MODE_WORLD_READABLE);
			} catch (SecurityException e) {
				Toasts.showToast(activityReference, Pair.of(e.getMessage(), 0), null, Toast.LENGTH_LONG);
				activityReference.finish();
			}

			FilterResults result = new FilterResults();
			if (constraint != null && constraint.length() > 0) {
				Pattern regexp = Pattern.compile(constraint.toString(), Pattern.LITERAL | Pattern.CASE_INSENSITIVE);
				items.removeIf(app -> !regexp.matcher(app.name == null ? "" : app.name).find()
						&& !regexp.matcher(app.packageName).find());
			}
			items.removeIf(app -> filteredOut(prefs, app));

			result.values = items;
			result.count = items.size();

			return result;
		}

		private boolean filteredOut(SharedPreferences prefs, ApplicationInfo app) {
			String packageName = app.packageName;
			boolean isUser = (app.flags & ApplicationInfo.FLAG_SYSTEM) == 0;

			// AppType = Overridden is used for USER apps
			if (filteredOut(isUser, filterAppType))
				return true;

			// AppState = Overridden is used for ENABLED apps
			if (filteredOut(app.enabled, filterAppState))
				return true;

			if (filteredOut(prefs.getBoolean(packageName + Common.PREF_ACTIVE, false), filterActive))
				return true;

			if (FilterState.UNCHANGED.equals(filterActive))
				// Ignore additional filters
				return false;

			for (SettingInfo setting : settings)
				if (filteredOut(prefs.contains(packageName + setting.settingKey), setting.filter))
					return true;

			if (filterPermissionUsage != null) {
				Set<String> pkgsForPerm = permUsage.get(filterPermissionUsage);
				return !Objects.requireNonNull(pkgsForPerm).contains(packageName);
			}

			return false;
		}

		private boolean filteredOut(boolean set, FilterState state) {
			if (state == null)
				return false;

			switch (state) {
			case UNCHANGED:
				return set;
			case OVERRIDDEN:
				return !set;
			default:
				return false;
			}
		}

		@SuppressWarnings("unchecked")
		@Override
		protected void publishResults(CharSequence constraint, FilterResults results) {
			// NOTE: this function is *always* called from the UI thread.
			filteredAppList = (ArrayList<ApplicationInfo>) results.values;
			adapter.notifyDataSetChanged();
			adapter.clear();
			for (int i = 0, l = filteredAppList.size(); i < l; i++) {
				adapter.add(filteredAppList.get(i));
			}
			adapter.notifyDataSetInvalidated();
		}
	}

	static class AppListViewHolder {
		TextView app_name;
		TextView app_package;
		ImageView app_icon;
	}

	static class AppListAdapter extends ArrayAdapter<ApplicationInfo> implements SectionIndexer {

		private final Map<String, Integer> alphaIndexer;
		private String[] sections;
		private final Filter filter;
		private final LayoutInflater inflater;
		private final Drawable defaultIcon;
		private final MainActivity mContext;

		AppListAdapter(MainActivity context, List<ApplicationInfo> items) {
			super(context, R.layout.app_list_item, new ArrayList<>(items));
			mContext = context;

			filteredAppList.addAll(items);

			filter = new AppListFilter(this, mContext);
			inflater = mContext.getLayoutInflater();

			defaultIcon = ContextCompat.getDrawable(mContext, android.R.drawable.sym_def_app_icon);

			alphaIndexer = new HashMap<>();
			for (int i = filteredAppList.size() - 1; i >= 0; i--) {
				ApplicationInfo app = filteredAppList.get(i);
				String appName = app.name;
				String firstChar;
				if (appName == null || appName.length() < 1) {
					firstChar = "@";
				} else {
					firstChar = appName.substring(0, 1).toUpperCase();
					if (firstChar.charAt(0) > 'Z' || firstChar.charAt(0) < 'A')
						firstChar = "@";
				}

				alphaIndexer.put(firstChar, i);
			}

			Set<String> sectionLetters = alphaIndexer.keySet();

			// create a list from the set to sort
			List<String> sectionList = new ArrayList<>(sectionLetters);

			Collections.sort(sectionList);

			sections = new String[sectionList.size()];

			sectionList.toArray(sections);
		}

		@NonNull
		@Override
		public View getView(int position, View convertView, @NonNull ViewGroup parent) {
			// Load or reuse the view for this row
			View row = convertView;
			AppListViewHolder holder;
			if (row == null) {
				row = inflater.inflate(R.layout.app_list_item, parent, false);
				holder = new AppListViewHolder();
				holder.app_name = row.findViewById(R.id.app_name);
				holder.app_package = row.findViewById(R.id.app_package);
				holder.app_icon = row.findViewById(R.id.app_icon);
				row.setTag(holder);
			} else {
				holder = (AppListViewHolder) row.getTag();
			}

			ApplicationInfo app = filteredAppList.get(position);

			holder.app_name.setText(app.name == null ? "" : app.name);
			holder.app_package.setTextColor(prefs.getBoolean(app.packageName + Common.PREF_ACTIVE, false)
					? Color.RED : ContextCompat.getColor(mContext, R.color.package_name));
			holder.app_package.setText(app.packageName);
			holder.app_icon.setImageDrawable(defaultIcon);

			if (app.enabled) {
				holder.app_name.setPaintFlags(holder.app_name.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
				holder.app_package.setPaintFlags(holder.app_package.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
			} else {
				holder.app_name.setPaintFlags(holder.app_name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
				holder.app_package.setPaintFlags(holder.app_package.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
			}

			imageLoadTask(mContext, app, holder);

			return row;
		}

		private static void imageLoadTask(MainActivity activity, ApplicationInfo appInfo, AppListViewHolder holder) {
			Observable.fromCallable(() -> appInfo.loadIcon(activity.getPackageManager()))
				.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
				    .subscribe(new Observer<>() {
						@Override
						public void onSubscribe(@NonNull Disposable d) { }

						@Override
						public void onNext(@NonNull Drawable appIcon) {
							holder.app_icon.setImageDrawable(appIcon);
						}

						@Override
						public void onError(@NonNull Throwable e) { }

						@Override
						public void onComplete() { }
					});
		}

		@Override
		public void notifyDataSetInvalidated() {
			alphaIndexer.clear();
			for (int i = filteredAppList.size() - 1; i >= 0; i--) {
				ApplicationInfo app = filteredAppList.get(i);
				String appName = app.name;
				String firstChar;
				if (appName == null || appName.length() < 1) {
					firstChar = "@";
				} else {
					firstChar = appName.substring(0, 1).toUpperCase();
					if (firstChar.charAt(0) > 'Z' || firstChar.charAt(0) < 'A')
						firstChar = "@";
				}
				alphaIndexer.put(firstChar, i);
			}

			Set<String> keys = alphaIndexer.keySet();
			Iterator<String> it = keys.iterator();
			ArrayList<String> keyList = new ArrayList<>();
			while (it.hasNext()) {
				keyList.add(it.next());
			}

			Collections.sort(keyList);
			sections = new String[keyList.size()];
			keyList.toArray(sections);

			super.notifyDataSetInvalidated();
		}

		@Override
		public int getPositionForSection(int section) {
			if (section >= sections.length)
				return filteredAppList.size() - 1;

			//noinspection ConstantConditions
			return alphaIndexer.get(sections[section]);
		}

		@Override
		public int getSectionForPosition(int position) {

			// Iterate over the sections to find the closest index
			// that is not greater than the position
			int closestIndex = 0;
			int latestDelta = Integer.MAX_VALUE;

			for (int i = 0; i < sections.length; i++) {

				//noinspection ConstantConditions
				int current = alphaIndexer.get(sections[i]);
				if (current == position) {
					// If position matches an index, return it immediately
					return i;
				} else if (current < position) {
					// Check if this is closer than the last index we inspected
					int delta = position - current;
					if (delta < latestDelta) {
						closestIndex = i;
						latestDelta = delta;
					}
				}
			}
			return closestIndex;
		}

		@Override
		public Object[] getSections() {
			return sections;
		}

		@NonNull
		@Override
		public Filter getFilter() {
			return filter;
		}
	}
}
