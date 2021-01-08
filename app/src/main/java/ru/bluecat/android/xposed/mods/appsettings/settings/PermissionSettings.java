package ru.bluecat.android.xposed.mods.appsettings.settings;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionInfo;
import android.graphics.Color;
import android.widget.ListView;
import android.widget.Switch;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import ru.bluecat.android.xposed.mods.appsettings.R;


/**
 * Manages a popup dialog for editing the Permission Revoking settings for a package
 */
public class PermissionSettings {
	private final Dialog dialog;

	private OnDismissListener onOkListener;
	private OnDismissListener onCancelListener;

	private boolean revokeActive;
	private final Set<String> disabledPerms;

	private final List<PermissionInfo> permsList = new LinkedList<>();

	/**
	 * Prepare a dialog for editing the permissions for the supplied package,
	 * with the provided owner activity and initial settings
	 */
	PermissionSettings(Activity owner, String pkgName, boolean revoking, Set<String> disabledPermissions) throws NameNotFoundException {
		dialog = new Dialog(owner);
		dialog.setContentView(R.layout.permissions_dialog);
		dialog.setTitle(R.string.perms_title);
		dialog.setCancelable(true);
		dialog.setOwnerActivity(owner);

		revokeActive = revoking;
		if (disabledPermissions != null)
			disabledPerms = new HashSet<>(disabledPermissions);
		else
			disabledPerms = new HashSet<>();

		Switch swtRevoke = dialog.findViewById(R.id.swtRevokePerms);
		swtRevoke.setChecked(revokeActive);

		// Load the list of permissions for the package and present them
		loadPermissionsList(pkgName);

		final PermissionsListAdapter appListAdapter = new PermissionsListAdapter(owner, permsList, disabledPerms, true);
		appListAdapter.setCanEdit(revokeActive);
		((ListView) dialog.findViewById(R.id.lstPermissions)).setAdapter(appListAdapter);

		// Track changes to the Revoke checkbox to lock or unlock the list of
		// permissions
		swtRevoke.setOnCheckedChangeListener((buttonView, isChecked) -> {
			revokeActive = isChecked;
			dialog.findViewById(R.id.lstPermissions).setBackgroundColor(revokeActive ? Color.BLACK : Color.DKGRAY);
			appListAdapter.setCanEdit(revokeActive);
		});
		dialog.findViewById(R.id.lstPermissions).setBackgroundColor(revokeActive ? Color.BLACK : Color.DKGRAY);

		dialog.findViewById(R.id.btnPermsCancel).setOnClickListener(v -> {
			if (onCancelListener != null)
				onCancelListener.onDismiss(this);
			dialog.dismiss();
		});
		dialog.findViewById(R.id.btnPermsOk).setOnClickListener(v -> {
			if (onOkListener != null)
				onOkListener.onDismiss(this);
			dialog.dismiss();
		});
	}

	/**
	 * Display the editor dialog
	 */
	void display() {
		dialog.show();
	}

	/**
	 * Register a listener to be invoked when the editor is dismissed with the
	 * Ok button
	 */
	void setOnOkListener(OnDismissListener listener) {
		onOkListener = listener;
	}

	/**
	 * Register a listener to be invoked when the editor is dismissed with the
	 * Cancel button
	 */
	public void setOnCancelListener(OnDismissListener listener) {
		onCancelListener = listener;
	}

	/**
	 * Get the state of the Active switch
	 */
	boolean getRevokeActive() {
		return revokeActive;
	}

	/**
	 * Get the list of permissions in the disabled state
	 */
	Set<String> getDisabledPermissions() {
		return new HashSet<>(disabledPerms);
	}

	/*
	 * Populate the list of permissions requested by this package
	 */
	@SuppressLint("DefaultLocale")
	private void loadPermissionsList(String pkgName) throws NameNotFoundException {
		permsList.clear();

		PackageManager pm = dialog.getContext().getPackageManager();
		PackageInfo pkgInfo = pm.getPackageInfo(pkgName, PackageManager.GET_PERMISSIONS);
		if (pkgInfo.sharedUserId != null) {
			Switch swtRevoke = dialog.findViewById(R.id.swtRevokePerms);
			swtRevoke.setText(R.string.perms_shared_warning);
			swtRevoke.setTextColor(Color.RED);
		}
		String[] permissions = pkgInfo.requestedPermissions;
		if (permissions == null) {
			permissions = new String[0];
		}
		for (String perm : permissions) {
			try {
				permsList.add(pm.getPermissionInfo(perm, 0));
			} catch (NameNotFoundException e) {
				PermissionInfo unknownPerm = new PermissionInfo();
				unknownPerm.name = perm;
				permsList.add(unknownPerm);
			}
		}

		Collections.sort(permsList, (lhs, rhs) -> {
			if (lhs.name == null) {
				return -1;
			} else if (rhs.name == null) {
				return 1;
			} else {
				return lhs.name.toUpperCase().compareTo(rhs.name.toUpperCase());
			}
		});
	}

	/**
	 * Interface for the listeners of Ok/Cancel dismiss actions
	 */
	public interface OnDismissListener {

		void onDismiss(PermissionSettings obj);
	}

}
