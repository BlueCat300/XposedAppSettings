package ru.bluecat.android.xposed.mods.appsettings;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/*
 * Adapter to feed the list of permission entries
 */
public class PermissionsListAdapter extends ArrayAdapter<PermissionInfo> implements Filterable {

	private final Activity context;
	private final List<PermissionInfo> originalPermsList;
	private final Set<String> disabledPerms;
	private final boolean allowEdits;
	private final SharedPreferences prefs;
	private boolean canEdit;
	private Filter mFilter;

	public PermissionsListAdapter(Activity context, List<PermissionInfo> items, Set<String> disabledPerms,
								  boolean allowEdits, SharedPreferences prefs) {
		super(context, R.layout.app_permission_item, items);
		this.context = context;
		originalPermsList = new ArrayList<>(items);
		this.disabledPerms = disabledPerms;
		this.allowEdits = allowEdits;
		this.prefs = prefs;
		canEdit = false;
	}

	public void setCanEdit(boolean canEdit) {
		this.canEdit = canEdit;
	}

	private static class ViewHolder {
		TextView tvName;
		TextView tvDescription;
	}

	@NonNull
	public View getView(int position, View convertView, @NonNull ViewGroup parent) {
		View row = convertView;
		ViewHolder vHolder;
		if (row == null) {
			row = context.getLayoutInflater().inflate(R.layout.app_permission_item, parent, false);
			vHolder = new ViewHolder();
			vHolder.tvName = row.findViewById(R.id.perm_name);
			vHolder.tvDescription = row.findViewById(R.id.perm_description);
			row.setTag(vHolder);
		} else {
			vHolder = (ViewHolder) row.getTag();
		}

		PermissionInfo perm = getItem(position);
		PackageManager pm = context.getPackageManager();

		CharSequence label = Objects.requireNonNull(perm).loadLabel(pm);
		if (!label.equals(perm.name)) {
			label = perm.name + " (" + label + ")";
		}

		vHolder.tvName.setText(label);
		CharSequence description = perm.loadDescription(pm);
		description = (description == null) ? "" : description.toString().trim();
		if (description.length() == 0)
			description = context.getString(R.string.perms_nodescription);
		vHolder.tvDescription.setText(description);
		switch (perm.protectionLevel) {
		case PermissionInfo.PROTECTION_DANGEROUS:
			vHolder.tvDescription.setTextColor(Color.RED);
			break;
		case PermissionInfo.PROTECTION_SIGNATURE:
			vHolder.tvDescription.setTextColor(Color.GREEN);
			break;
		case PermissionInfo.PROTECTION_SIGNATURE_OR_SYSTEM:
			vHolder.tvDescription.setTextColor(Color.YELLOW);
			break;
		default:
			int descBaseColor = R.color.permission_desc;
			if (ThemeUtil.isNightTheme(context, prefs)) descBaseColor = R.color.permission_desc_dark;
			vHolder.tvDescription.setTextColor(ContextCompat.getColor(context, descBaseColor));
			break;
		}

		vHolder.tvName.setTag(perm.name);
		if (disabledPerms.contains(perm.name)) {
			vHolder.tvName.setPaintFlags(vHolder.tvName.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
			vHolder.tvName.setTextColor(Color.MAGENTA);
		} else {
			vHolder.tvName.setPaintFlags(vHolder.tvName.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
			if (!ThemeUtil.isNightTheme(context, prefs)) vHolder.tvName.setTextColor(Color.BLACK);
			else vHolder.tvName.setTextColor(Color.WHITE);
		}
		if (allowEdits) {
			row.setOnClickListener(v -> {
				if (!canEdit) {
					return;
				}

				TextView tv = v.findViewById(R.id.perm_name);
				if ((tv.getPaintFlags() & Paint.STRIKE_THRU_TEXT_FLAG) != 0) {
					//noinspection SuspiciousMethodCalls
					disabledPerms.remove(tv.getTag());
					tv.setPaintFlags(tv.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
					if (!ThemeUtil.isNightTheme(context, prefs)) tv.setTextColor(Color.BLACK);
					else tv.setTextColor(Color.WHITE);
				} else {
					disabledPerms.add((String) tv.getTag());
					tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
					tv.setTextColor(Color.MAGENTA);
				}
			});
		}

		return row;
	}

	@NonNull
	@Override
	public Filter getFilter() {
		if (mFilter == null) {
			mFilter = new CustomFilter();
		}
		return mFilter;
	}

	/* Filter permissions by name, label or description based on contained text */
	private class CustomFilter extends Filter {

		private boolean matches(CharSequence value, CharSequence filter) {
			return (value != null && value.toString().toLowerCase().contains(filter));
		}

		@Override
		protected FilterResults performFiltering(CharSequence constraint) {
			FilterResults result = new FilterResults();
			ArrayList<PermissionInfo> items = new ArrayList<>();
			if (constraint == null || constraint.length() == 0) {
				items.addAll(originalPermsList);
			} else {
				String findText = constraint.toString().toLowerCase();
				PackageManager pm = context.getPackageManager();
				for (PermissionInfo p : originalPermsList) {
					if (matches(p.name, findText) || matches(p.loadLabel(pm), findText) || matches(p.loadDescription(pm), findText)) {
						items.add(p);
					}
				}
			}
			result.values = items;
			result.count = items.size();
			return result;
		}

		@SuppressWarnings("unchecked")
		@Override
		protected void publishResults(CharSequence constraint, FilterResults results) {
			clear();
			addAll((List<PermissionInfo>) results.values);
			notifyDataSetChanged();
		}

	}

}
