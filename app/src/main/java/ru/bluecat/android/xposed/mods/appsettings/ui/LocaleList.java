package ru.bluecat.android.xposed.mods.appsettings.ui;

import android.content.res.Resources;

import androidx.annotation.NonNull;

import java.text.Collator;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Manages a list of valid locales for the system
 */
public class LocaleList {

	/*
	 * From AOSP code - listing available languages to present to the user
	 */
	private static class LocaleInfo implements Comparable<LocaleInfo> {
		static Collator sCollator = Collator.getInstance();

		String label;
		Locale locale;

		LocaleInfo(String label, Locale locale) {
			this.label = label;
			this.locale = locale;
		}

		@NonNull
		@Override
		public String toString() {
			return this.label;
		}

		@Override
		public int compareTo(LocaleInfo another) {
			return sCollator.compare(this.label, another.label);
		}
	}

	private final String[] localeCodes;
	private final String[] localeDescriptions;

	public LocaleList(String defaultLabel) {
		String[] locales = Resources.getSystem().getAssets().getLocales();
		Arrays.sort(locales);
		int origSize = locales.length;
		LocaleInfo[] preprocess = new LocaleInfo[origSize];
		int finalSize = 0;
		for (String s : locales) {
			int len = s.length();
			if (len == 5) {
				String language = s.substring(0, 2);
				String country = s.substring(3, 5);
				Locale l = new Locale(language, country);

				if (finalSize == 0) {
					preprocess[finalSize++] = new LocaleInfo(toTitleCase(l.getDisplayLanguage(l)), l);
				} else {
					// check previous entry:
					// same lang and a country -> upgrade to full name and
					// insert ours with full name
					// diff lang -> insert ours with lang-only name
					if (preprocess[finalSize - 1].locale.getLanguage().equals(language)) {
						preprocess[finalSize - 1].label = toTitleCase(getDisplayName(preprocess[finalSize - 1].locale));
						preprocess[finalSize++] = new LocaleInfo(toTitleCase(getDisplayName(l)), l);
					} else {
						String displayName;
						if (s.equals("zz_ZZ")) {
							displayName = "Pseudo...";
						} else {
							displayName = toTitleCase(l.getDisplayLanguage(l));
						}
						preprocess[finalSize++] = new LocaleInfo(displayName, l);
					}
				}
			}
		}

		LocaleInfo[] localeInfos = new LocaleInfo[finalSize];
		System.arraycopy(preprocess, 0, localeInfos, 0, finalSize);
		Arrays.sort(localeInfos);

		localeCodes = new String[localeInfos.length + 1];
		localeDescriptions = new String[localeInfos.length + 1];
		localeCodes[0] = "";
		localeDescriptions[0] = defaultLabel;
		for (int i = 1; i < finalSize + 1; i++) {
			localeCodes[i] = getLocaleCode(localeInfos[i - 1].locale);
			localeDescriptions[i] = localeInfos[i - 1].label;
		}
	}

	private static String toTitleCase(String s) {
		if (s.length() == 0) {
			return s;
		}

		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	private static String getDisplayName(Locale loc) {
		return loc.getDisplayName(loc);
	}

	private static String getLocaleCode(Locale loc) {
		String result = loc.getLanguage();
		if (loc.getCountry().length() > 0)
			result += "_" + loc.getCountry();
		if (loc.getVariant().length() > 0)
			result += "_" + loc.getVariant();
		return result;
	}

	/**
	 * Retrieve the locale code at a specific position in the list.
	 */
	public String getLocale(int pos) {
		return localeCodes[pos];
	}

	/**
	 * Retrieve the position where the specified locale code is, or 0 if it was
	 * not found.
	 */
	public int getLocalePos(String locale) {
		for (int i = 1; i < localeCodes.length; i++) {
			if (localeCodes[i].equals(locale))
				return i;
		}
		return 0;
	}

	/**
	 * Retrieve an ordered list of the locale descriptions
	 */
	public List<String> getDescriptionList() {
		return Arrays.asList(localeDescriptions);
	}

}
