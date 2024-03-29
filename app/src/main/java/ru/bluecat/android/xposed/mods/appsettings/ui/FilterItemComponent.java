package ru.bluecat.android.xposed.mods.appsettings.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import ru.bluecat.android.xposed.mods.appsettings.R;

/**
 * Composite component that displays a header and a triplet of radio buttons for
 * selection of All / Overridden / Unchanged settings for each parameter
 */
public class FilterItemComponent extends LinearLayout {

	private OnFilterChangeListener listener;

	/** Constructor for designer instantiation */
	public FilterItemComponent(Context context, AttributeSet attrs) {
		super(context, attrs);

		LayoutInflater.from(context).inflate(R.layout.filter_item, this);

		TypedArray atts = context.obtainStyledAttributes(attrs, R.styleable.FilterItemComponent);

		// Load label values, if any
		setLabel(R.id.txtFilterName, atts.getString(R.styleable.FilterItemComponent_label));
		setLabel(R.id.radAll, atts.getString(R.styleable.FilterItemComponent_all_label));
		setLabel(R.id.radOverridden, atts.getString(R.styleable.FilterItemComponent_overridden_label));
		setLabel(R.id.radUnchanged, atts.getString(R.styleable.FilterItemComponent_unchanged_label));
		atts.recycle();

		setupListener();
	}

	/** Constructor for programmatic instantiation */
	public FilterItemComponent(Context context, String filterName, String labelAll, String labelOverriden, String labelUnchanged) {
		super(context);

		LayoutInflater.from(context).inflate(R.layout.filter_item, this);

		// Load label values, if any
		setLabel(R.id.txtFilterName, filterName);
		setLabel(R.id.radAll, labelAll);
		setLabel(R.id.radOverridden, labelOverriden);
		setLabel(R.id.radUnchanged, labelUnchanged);

		setupListener();
	}

	private void setupListener() {
		// Notify any listener of changes in the selected option
		((RadioGroup) findViewById(R.id.radOptions)).setOnCheckedChangeListener((group, checkedId) -> {
			if (listener != null) {
				if (checkedId == R.id.radOverridden) {
					listener.onFilterChanged(this, FilterState.OVERRIDDEN);
				} else if (checkedId == R.id.radUnchanged) {
					listener.onFilterChanged(this, FilterState.UNCHANGED);
				} else {
					listener.onFilterChanged(this, FilterState.ALL);
				}
			}
		});
	}

	/*
	 * Update the label of a view id, if non-null
	 */
	private void setLabel(int id, CharSequence value) {
		TextView label = findViewById(id);
		if (label != null && value != null) {
			label.setText(value);
		}
	}

	/**
	 * Enable or disable all the items within this compound component
	 */
	@Override
	public void setEnabled(boolean enabled) {
		findViewById(R.id.radOptions).setEnabled(enabled);
		findViewById(R.id.radAll).setEnabled(enabled);
		findViewById(R.id.radOverridden).setEnabled(enabled);
		findViewById(R.id.radUnchanged).setEnabled(enabled);
	}

	/**
	 * Check if this compound component is enabled
	 */
	@Override
	public boolean isEnabled() {
		return findViewById(R.id.radOptions).isEnabled();
	}

	/**
	 * Get currently selected filter option
	 */
	public FilterState getFilterState() {
		int id = (((RadioGroup) findViewById(R.id.radOptions)).getCheckedRadioButtonId());
		if (id == R.id.radOverridden) {
			return FilterState.OVERRIDDEN;
		} else if (id == R.id.radUnchanged) {
			return FilterState.UNCHANGED;
		} else {
			return FilterState.ALL;
		}
	}

	/**
	 * Activate one of the 3 options as the selected one
	 */
	public void setFilterState(FilterState state) {
		// Handle null values and use the default "All"
		if (state == null)
			state = FilterState.ALL;

		switch (state) {
			case OVERRIDDEN -> ((RadioGroup) findViewById(R.id.radOptions)).check(R.id.radOverridden);
			case UNCHANGED -> ((RadioGroup) findViewById(R.id.radOptions)).check(R.id.radUnchanged);
			default -> ((RadioGroup) findViewById(R.id.radOptions)).check(R.id.radAll);
		}
	}

	/**
	 * Register a listener to be notified when the selection changes
	 */
	public void setOnFilterChangeListener(OnFilterChangeListener listener) {
		this.listener = listener;
	}

	/**
	 * Interface for listeners that will be notified of selection changes
	 */
	public interface OnFilterChangeListener {
		/**
		 * Notification that this filter item has changed to a new selected state
		 */
		void onFilterChanged(FilterItemComponent item, FilterState state);
	}

	/**
	 * Possible values for the filter state: All / Overridden / Unchanged
	 */
	public enum FilterState {
		ALL, OVERRIDDEN, UNCHANGED
	}
}
