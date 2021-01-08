package ru.bluecat.android.xposed.mods.appsettings;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RecentTaskInfo;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SectionIndexer;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.lang.ref.WeakReference;
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

import ru.bluecat.android.xposed.mods.appsettings.FilterItemComponent.FilterState;
import ru.bluecat.android.xposed.mods.appsettings.settings.ApplicationSettings;
import ru.bluecat.android.xposed.mods.appsettings.settings.PermissionsListAdapter;

import static ru.bluecat.android.xposed.mods.appsettings.BackupActivity.restoreSuccessful;

public class MainActivity extends Activity {

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

	private static SharedPreferences prefs;

    @SuppressLint("WorldReadableFiles")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		setTitle(R.string.app_name);
		super.onCreate(savedInstanceState);
		try {
			//noinspection deprecation
			prefs = this.getSharedPreferences(Common.PREFS, Context.MODE_WORLD_READABLE);
		} catch (SecurityException ignored) {
			// The new XSharedPreferences is not enabled or module's not loading
			xposedNotActive(this);
			prefs = null;
			return;
		}
		restoreSuccessful = false;
		loadSettings();
		setContentView(R.layout.main);
		ListView list = findViewById(R.id.lstApps);
		registerForContextMenu(list);
		list.setOnItemClickListener((parent, view, position, id) -> {
			// Open settings activity when clicking on an application
			String pkgName = ((TextView) view.findViewById(R.id.app_package)).getText().toString();
			Intent i = new Intent(getApplicationContext(), ApplicationSettings.class);
			i.putExtra("package", pkgName);
			startActivityForResult(i, position);
		});
		refreshApps();
	}

	public static void xposedNotActive(Activity context) {
		AlertDialog.Builder dialog = new AlertDialog.Builder(context);
		dialog.setTitle(R.string.app_min_api_warning_title);
		dialog.setMessage(R.string.app_min_api_warning_message);
		dialog.setPositiveButton("Ok", (dialog1, id) -> {
			dialog1.dismiss();
			System.exit(0);
		});
		AlertDialog alert = dialog.create();
		alert.setCancelable(false);
		alert.setCanceledOnTouchOutside(false);
		alert.show();

		TextView msgText = alert.findViewById(android.R.id.message);
		if (msgText != null) {
			msgText.setMovementMethod(LinkMovementMethod.getInstance());
		}
	}

	@Override
	public void onDestroy () {
    	super.onDestroy();
		clearSearchQueryOnFinish();
	}

	private void clearSearchQueryOnFinish() {
		SearchView searchBar = this.findViewById(R.id.searchApp);
		if(searchBar.getQuery().length() != 0) {
			searchBar.setIconified(true);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		refreshAppsAfterImport();
	}

	@SuppressLint("WorldReadableFiles")
	private void refreshAppsAfterImport() {
		if (restoreSuccessful) {
			// Refresh preferences
			try {
				//noinspection deprecation
				prefs = this.getSharedPreferences(Common.PREFS, Context.MODE_WORLD_READABLE);
			} catch (SecurityException ignored) {
				// The new XSharedPreferences is not enabled or module's not loading
				xposedNotActive(this);
				prefs = null;
				return;
			}
			// Refresh listed apps (account for filters)
			MainActivity.AppListAdapter appListAdapter = (MainActivity.AppListAdapter) ((ListView) this.findViewById(R.id.lstApps)).getAdapter();
			appListAdapter.getFilter().filter(nameFilter);
			restoreSuccessful = false;
		}
	}

	private void loadSettings() {
		settings = new ArrayList<>();

		settings.add(new SettingInfo(Common.PREF_DPI, getString(R.string.settings_dpi)));
		settings.add(new SettingInfo(Common.PREF_FONT_SCALE, getString(R.string.settings_fontscale)));
		settings.add(new SettingInfo(Common.PREF_SCREEN, getString(R.string.settings_screen)));
		settings.add(new SettingInfo(Common.PREF_XLARGE, getString(R.string.settings_xlargeres)));
		settings.add(new SettingInfo(Common.PREF_SCREENSHOT, getString(R.string.settings_screenshot)));
		settings.add(new SettingInfo(Common.PREF_LOCALE, getString(R.string.settings_locale)));
		settings.add(new SettingInfo(Common.PREF_FULLSCREEN, getString(R.string.settings_fullscreen)));
		settings.add(new SettingInfo(Common.PREF_NO_TITLE, getString(R.string.settings_notitle)));
		settings.add(new SettingInfo(Common.PREF_SCREEN_ON, getString(R.string.settings_screenon)));
		settings.add(new SettingInfo(Common.PREF_ALLOW_ON_LOCKSCREEN, getString(R.string.settings_showwhenlocked)));
		settings.add(new SettingInfo(Common.PREF_RESIDENT, getString(R.string.settings_resident)));
		settings.add(new SettingInfo(Common.PREF_NO_FULLSCREEN_IME, getString(R.string.settings_nofullscreenime)));
		settings.add(new SettingInfo(Common.PREF_ORIENTATION, getString(R.string.settings_orientation)));
		settings.add(new SettingInfo(Common.PREF_INSISTENT_NOTIF, getString(R.string.settings_insistentnotif)));
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
			settings.add(new SettingInfo(Common.PREF_NO_BIG_NOTIFICATIONS, getString(R.string.settings_nobignotif)));
		}
		settings.add(new SettingInfo(Common.PREF_ONGOING_NOTIF, getString(R.string.settings_ongoingnotif)));
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
			settings.add(new SettingInfo(Common.PREF_NOTIF_PRIORITY, getString(R.string.settings_notifpriority)));
		}
		settings.add(new SettingInfo(Common.PREF_RECENTS_MODE, getString(R.string.settings_recents_mode)));
		settings.add(new SettingInfo(Common.PREF_MUTE, getString(R.string.settings_mute)));
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
			settings.add(new SettingInfo(Common.PREF_LEGACY_MENU, getString(R.string.settings_legacy_menu)));
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			settings.add(new SettingInfo(Common.PREF_RECENT_TASKS, getString(R.string.settings_recent_tasks)));
		}
		settings.add(new SettingInfo(Common.PREF_REVOKEPERMS, getString(R.string.settings_permissions)));
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		// Refresh the app that was just edited, if it's visible in the list
		ListView list = findViewById(R.id.lstApps);
		if (requestCode >= list.getFirstVisiblePosition() && requestCode <= list.getLastVisiblePosition()) {
			View v = list.getChildAt(requestCode - list.getFirstVisiblePosition());
			list.getAdapter().getView(requestCode, v, list);
		} else if (requestCode == Integer.MAX_VALUE) {
			list.invalidateViews();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_main, menu);
		updateMainMenuEntries(menu);
		return true;
	}

	private static void updateMainMenuEntries(Menu menu) {
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !isModActive()) {
			menu.findItem(R.id.menu_recents).setEnabled(false);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (prefs == null) {
			xposedNotActive(this);
		} else if (id == R.id.menu_refresh) {
			refreshApps();
		} else if (id == R.id.menu_recents) {
			showRecents();
		} else if(id == R.id.menu_backup) {
			BackupActivity.startBackupActivity(this, false);
		} else if (id == R.id.menu_restore) {
			BackupActivity.startBackupActivity(this, true);
		} else if (id == R.id.menu_about) {
			showAboutDialog();
		}
		return super.onOptionsItemSelected(item);
	}

	private void refreshApps() {
		appList.clear();
		// (re)load the list of apps in the background
		new PrepareAppsAdapter(this).execute();
	}

	private void showRecents() {
		ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		PackageManager pm = getPackageManager();

		final List<Map<String, Object>> data = new ArrayList<>();
		if (am != null) {
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
				Intent i = new Intent(getApplicationContext(), ApplicationSettings.class);
				i.putExtra("package", (String) data.get(which).get("package"));
				startActivityForResult(i, Integer.MAX_VALUE);
			}).show();
	}

	private void showAboutDialog() {
		View vAbout;
		vAbout = View.inflate(this, R.layout.about, null);

		// Warn if the module is not active
		if (!isModActive())
			vAbout.findViewById(R.id.about_notactive).setVisibility(View.VISIBLE);

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
		Builder dlgBuilder = new AlertDialog.Builder(this);
		dlgBuilder.setTitle(R.string.app_name);
		dlgBuilder.setCancelable(true);
		dlgBuilder.setIcon(R.drawable.ic_launcher);
		dlgBuilder.setPositiveButton(android.R.string.ok, null);
		dlgBuilder.setView(vAbout);
		dlgBuilder.show();
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		if (v.getId() == R.id.lstApps) {
			AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
			ApplicationInfo appInfo = filteredAppList.get(info.position);

			menu.setHeaderTitle(getPackageManager().getApplicationLabel(appInfo));
			getMenuInflater().inflate(R.menu.menu_app, menu);
			menu.findItem(R.id.menu_save).setVisible(false);

			ApplicationSettings.updateMenuEntries(getApplicationContext(), menu, appInfo.packageName);
		} else {
			super.onCreateContextMenu(menu, v, menuInfo);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		String pkgName = filteredAppList.get(info.position).packageName;
		if (item.getItemId() == R.id.menu_app_launch) {
			Intent LaunchIntent = getPackageManager().getLaunchIntentForPackage(pkgName);
			startActivity(LaunchIntent);
			return true;
		} else if (item.getItemId() == R.id.menu_app_settings) {
			startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkgName)));
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
			SearchView searchV = findViewById(R.id.searchApp);
			if (searchV.isShown()) {
				searchV.setIconified(false);
				return true;
			}
		}
		return super.onKeyUp(keyCode, event);
	}

	public static boolean isModActive() {
		return false;
	}

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
					Set<String> permUsers = permUsage.get(perm);
					if (permUsers == null) {
						permUsers = new TreeSet<>();
						permUsage.put(perm, permUsers);
					}
					permUsers.add(pkgInfo.packageName);
				}

			if (pkgInfo.sharedUserId != null) {
				Set<String> sharedUserPackages = sharedUsers.get(pkgInfo.sharedUserId);
				if (sharedUserPackages == null) {
					sharedUserPackages = new TreeSet<>();
					sharedUsers.put(pkgInfo.sharedUserId, sharedUserPackages);
				}
				sharedUserPackages.add(pkgInfo.packageName);

				pkgSharedUsers.put(pkgInfo.packageName, pkgInfo.sharedUserId);
			}
		}

		Collections.sort(appList, (lhs, rhs) -> {
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
		final AppListAdapter appListAdapter = new AppListAdapter(activity, appList);

		((ListView) activity.findViewById(R.id.lstApps)).setAdapter(appListAdapter);
		appListAdapter.getFilter().filter(nameFilter);
		((SearchView) activity.findViewById(R.id.searchApp)).setOnQueryTextListener(new SearchView.OnQueryTextListener() {

			@Override
			public boolean onQueryTextSubmit(String query) {
				nameFilter = query;
				appListAdapter.getFilter().filter(nameFilter);
				activity.findViewById(R.id.searchApp).clearFocus();
				return false;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				nameFilter = newText;
				appListAdapter.getFilter().filter(nameFilter);
				return false;
			}

		});

		activity.findViewById(R.id.btnFilter).setOnClickListener(new View.OnClickListener() {
			Dialog filterDialog;
			Map<String, FilterItemComponent> filterComponents;

			@Override
			public void onClick(View v) {
				// set up dialog
				filterDialog = new Dialog(activity);
				filterDialog.setContentView(R.layout.filter_dialog);
				filterDialog.setTitle(R.string.filter_title);
				filterDialog.setCancelable(true);
				filterDialog.setOwnerActivity(activity);

				LinearLayout entriesView = filterDialog.findViewById(R.id.filter_entries);
				filterComponents = new HashMap<>();
				for (SettingInfo setting : settings) {
					FilterItemComponent component = new FilterItemComponent(activity, setting.label, null, null, null);
					component.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
					component.setFilterState(setting.filter);
					entriesView.addView(component);
					filterComponents.put(setting.settingKey, component);
				}

				((FilterItemComponent) filterDialog.findViewById(R.id.fltAppType)).setFilterState(filterAppType);
				((FilterItemComponent) filterDialog.findViewById(R.id.fltAppState)).setFilterState(filterAppState);
				((FilterItemComponent) filterDialog.findViewById(R.id.fltActive)).setFilterState(filterActive);

				// Block or unblock the details based on the Active setting
				enableFilterDetails(!FilterState.UNCHANGED.equals(filterActive));
				((FilterItemComponent) filterDialog.findViewById(R.id.fltActive)).
						setOnFilterChangeListener((item, state) -> enableFilterDetails(!FilterState.UNCHANGED.equals(state)));

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

			private void enableFilterDetails(boolean enable) {
				for (FilterItemComponent component : filterComponents.values())
					component.setEnabled(enable);
			}
		});

		activity.findViewById(R.id.btnPermsFilter).setOnClickListener(v -> {

			Builder bld = new Builder(activity);
			bld.setCancelable(true);
			bld.setTitle(R.string.perms_filter_title);

			List<String> perms = new LinkedList<>(permUsage.keySet());
			Collections.sort(perms);
			List<PermissionInfo> items = new ArrayList<>();
			PackageManager pm = activity.getPackageManager();
			for (String perm : perms) {
				try {
					items.add(pm.getPermissionInfo(perm, 0));
				} catch (NameNotFoundException e) {
					PermissionInfo unknownPerm = new PermissionInfo();
					unknownPerm.name = perm;
					items.add(unknownPerm);
				}
			}
			PermissionsListAdapter adapter = new PermissionsListAdapter(activity, items, new HashSet<>(), false);
			bld.setAdapter(adapter, (dialog, which) -> {
				filterPermissionUsage = Objects.requireNonNull(adapter.getItem(which)).name;
				appListAdapter.getFilter().filter(nameFilter);
			});

			View permsView = View.inflate(activity, R.layout.permission_search, null);
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
			bld.setView(permsView);

			bld.setNegativeButton(android.R.string.cancel, (dialog, which) -> {
				filterPermissionUsage = null;
				appListAdapter.getFilter().filter(nameFilter);
			});

			AlertDialog dialog = bld.create();
			dialog.getListView().setFastScrollEnabled(true);

			dialog.show();
		});

	}

	// Handle background loading of apps
	private static class PrepareAppsAdapter extends AsyncTask<Void,Void,AppListAdapter> {
		ProgressDialog dialog;
		private final WeakReference<MainActivity> activityReference;

		PrepareAppsAdapter(MainActivity context) {
			activityReference = new WeakReference<>(context);
		}

		@Override
		protected void onPreExecute() {
			MainActivity activity = activityReference.get();
			dialog = new ProgressDialog(activity.findViewById(R.id.lstApps).getContext());
			dialog.setMessage(activity.getResources().getString(R.string.app_loading));
			dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			dialog.setCancelable(false);
			dialog.show();
		}

		@Override
		protected AppListAdapter doInBackground(Void... params) {
			if (appList.size() == 0) {
				loadApps(dialog, activityReference.get());
			}
			return null;
		}

		@Override
		protected void onPostExecute(final AppListAdapter result) {
			prepareAppList(activityReference.get());

			try {
				dialog.dismiss();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
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
			} catch (SecurityException ignored) {
				// The new XSharedPreferences is not enabled or module's not loading
				prefs = null;
			}

			FilterResults result = new FilterResults();
			if (constraint != null && constraint.length() > 0) {
				Pattern regexp = Pattern.compile(constraint.toString(), Pattern.LITERAL | Pattern.CASE_INSENSITIVE);
				for (Iterator<ApplicationInfo> i = items.iterator(); i.hasNext(); ) {
					ApplicationInfo app = i.next();
					if (!regexp.matcher(app.name == null ? "" : app.name).find()
							&& !regexp.matcher(app.packageName).find()) {
						i.remove();
					}
				}
			}
			for (Iterator<ApplicationInfo> i = items.iterator(); i.hasNext(); ) {
				ApplicationInfo app = i.next();
				if (prefs != null && filteredOut(prefs, app))
					i.remove();
			}

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

		AsyncTask<AppListViewHolder, Void, Drawable> imageLoader;
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
				holder.imageLoader.cancel(true);
			}

			final ApplicationInfo app = filteredAppList.get(position);

			holder.app_name.setText(app.name == null ? "" : app.name);
			holder.app_package.setTextColor(prefs.getBoolean(app.packageName + Common.PREF_ACTIVE, false)
					? Color.RED : Color.parseColor("#0099CC"));
			holder.app_package.setText(app.packageName);
			holder.app_icon.setImageDrawable(defaultIcon);

			if (app.enabled) {
				holder.app_name.setPaintFlags(holder.app_name.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
				holder.app_package.setPaintFlags(holder.app_package.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
			} else {
				holder.app_name.setPaintFlags(holder.app_name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
				holder.app_package.setPaintFlags(holder.app_package.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
			}

			holder.imageLoader = new ImageLoadTask(app, mContext).execute(holder);

			return row;
		}

		private static class ImageLoadTask extends AsyncTask<AppListViewHolder, Void, Drawable> {
			private AppListViewHolder v;
			private final ApplicationInfo app;
			private final WeakReference<MainActivity> activityReference;

			private ImageLoadTask(ApplicationInfo appInfo, MainActivity activity) {
				app = appInfo;
				activityReference = new WeakReference<>(activity);
			}

			@Override
			protected Drawable doInBackground(AppListViewHolder... params) {
				v = params[0];
				return app.loadIcon(activityReference.get().getPackageManager());
			}

			@Override
			protected void onPostExecute(Drawable result) {
				v.app_icon.setImageDrawable(result);
			}
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
