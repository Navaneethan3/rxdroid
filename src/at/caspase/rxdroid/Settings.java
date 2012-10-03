/**
 * Copyright (C) 2011 Joseph Lehner <joseph.c.lehner@gmail.com>
 *
 * This file is part of RxDroid.
 *
 * RxDroid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * RxDroid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with RxDroid.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 */

package at.caspase.rxdroid;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;
import at.caspase.rxdroid.db.Drug;
import at.caspase.rxdroid.db.Entries;
import at.caspase.rxdroid.db.Schedule;
import at.caspase.rxdroid.preferences.TimePeriodPreference.TimePeriod;
import at.caspase.rxdroid.util.Constants;
import at.caspase.rxdroid.util.DateTime;
import at.caspase.rxdroid.util.Util;
import at.caspase.rxdroid.util.WrappedCheckedException;

public final class Settings
{
	private static final String TAG = Settings.class.getName();
	private static final boolean LOGV = true;

	private static final String KEY_LAST_MSG_HASH = "last_msg_hash";
	//private static final String KEY_LAST_MSG_COUNT = "_last_msg_count";

	private static final String KEYS[] = { "time_morning", "time_noon", "time_evening", "time_night" };

	private static SharedPreferences sSharedPrefs = null;
	private static Context sContext;

	public static synchronized void init()
	{
		if(sContext == null)
		{
			sContext = GlobalContext.get();
			sSharedPrefs = PreferenceManager.getDefaultSharedPreferences(GlobalContext.get());
			sSharedPrefs.registerOnSharedPreferenceChangeListener(LISTENER);
		}
	}

	public static Set<String> getStringSet(String key) {
		return stringToStringSet(sSharedPrefs.getString(key, null));
	}

	public static void putStringSet(String key, Set<String> set) {
		sSharedPrefs.edit().putString(key, stringSetToString(set)).commit();
	}

	public static String getString(String key, String defValue) {
		return sSharedPrefs.getString(key, defValue);
	}

	public static void putString(String key, String value) {
		sSharedPrefs.edit().putString(key, value).commit();
	}

	public static Date getDate(String key)
	{
		String str = getString(key, null);
		if(str == null)
			return null;

		try
		{
			return DATE_FORMAT.parse(str);
		}
		catch(ParseException e)
		{
			throw new WrappedCheckedException(e);
		}
	}

	public static void putDate(String key, Date date) {
		putString(key, DATE_FORMAT.format(date));
	}

	public static boolean getBoolean(String key, boolean defaultValue) {
		return sSharedPrefs.getBoolean(key, defaultValue);
	}

	public static void putBoolean(String key, boolean value) {
		sSharedPrefs.edit().putBoolean(key, value).commit();
	}

	private static int[] HISTORY_AGE_IN_DAYS = {
		14,
		28,
		28 * 6,
		365,
		0
	};

	public static int getMaxHistoryAgeInDays()
	{
		int index = getListPreferenceValueIndex("db_max_history_age", 2);
		return HISTORY_AGE_IN_DAYS[index];
	}

	public static int filterNotificationDefaults(int defaults)
	{
		if(!sSharedPrefs.getBoolean("use_led", true))
			defaults ^= Notification.DEFAULT_LIGHTS;

		if(!sSharedPrefs.getBoolean("use_sound", true))
			defaults ^= Notification.DEFAULT_SOUND;

		if(!sSharedPrefs.getBoolean("use_vibrator", true))
			defaults ^= Notification.DEFAULT_VIBRATE;

		return defaults;
	}

	public static boolean hasLowSupplies(Drug drug)
	{
		if(!drug.isActive() || drug.getRefillSize() == 0 || drug.hasNoDoses())
			return false;

		final int minSupplyDays = Integer.parseInt(sSharedPrefs.getString("num_min_supply_days", "10"), 10);
		return Entries.getSupplyDaysLeftForDrug(drug, null) < minSupplyDays;
	}

	public static long getMillisUntilDoseTimeBegin(Calendar time, int doseTime) {
		return getMillisUntilDoseTimeBeginOrEnd(time, doseTime, FLAG_GET_MILLIS_UNTIL_BEGIN);
	}

	public static long getMillisUntilDoseTimeEnd(Calendar time, int doseTime) {
		return getMillisUntilDoseTimeBeginOrEnd(time, doseTime, 0);
	}

	public static long getAlarmTimeout()
	{
		// FIXME

		if(!sSharedPrefs.getBoolean("debug_snooze_time_short", false))
			return 1800 * 1000;

		return 10000;
	}

	public static long getDoseTimeBeginOffset(int doseTime) {
		return getDoseTimeBegin(doseTime).getTime();
	}

	public static long getDoseTimeEndOffset(int doseTime) {
		return getDoseTimeEnd(doseTime).getTime();
	}

	public static long getTrueDoseTimeEndOffset(int doseTime)
	{
		final long doseTimeBeginOffset = getDoseTimeBeginOffset(doseTime);
		long doseTimeEndOffset = getDoseTimeEndOffset(doseTime);

		if(doseTimeEndOffset < doseTimeBeginOffset)
			doseTimeEndOffset += Constants.MILLIS_PER_DAY;

		return doseTimeEndOffset;
	}

	public static boolean hasWrappingDoseTimeNight() {
		return getDoseTimeEndOffset(Drug.TIME_NIGHT) != getTrueDoseTimeEndOffset(Drug.TIME_NIGHT);
	}

	public static DumbTime getDoseTimeBegin(int doseTime)
	{
		final TimePeriod p = getTimePeriodPreference(doseTime);
		return p == null ? null : p.getBegin();
	}

	public static DumbTime getDoseTimeEnd(int doseTime)
	{
		final TimePeriod p = getTimePeriodPreference(doseTime);
		return p == null ? null : p.getEnd();
	}

	/*public DumbTime getTimePreference(String key)
	{
		if(key == null)
			return null;

		String value = sSharedPrefs.getString(key, null);
		if(value == null)
		{
			int resId = sApplicationContext.getResources().getIdentifier("at.caspase.rxdroid:string/pref_default_" + key, null, null);
			if(resId != 0)
				value = sApplicationContext.getString(resId);
			else
				value = "00:30";
		}

		return DumbTime.fromString(value);
	}*/

	public static TimePeriod getTimePeriodPreference(int doseTime)
	{
		final String key = KEYS[doseTime];

		String value = sSharedPrefs.getString(key, null);
		if(value == null)
		{
			int resId = sContext.getResources().
				getIdentifier("at.caspase.rxdroid:string/pref_default_" + key, null, null);

			if(resId == 0 || (value = sContext.getString(resId)) == null)
				throw new IllegalStateException("No default value for time preference " + key + " in strings.xml");

			if(LOGV) Log.i(TAG, "Persisting preference: " + key + "=" + value);
			sSharedPrefs.edit().putString(key, value).commit();
		}

		return TimePeriod.fromString(value);
	}

	public static class DoseTimeInfo
	{
		private static final ThreadLocal<DoseTimeInfo> INSTANCES = new ThreadLocal<Settings.DoseTimeInfo>() {

			@Override
			protected DoseTimeInfo initialValue()
			{
				return new DoseTimeInfo();
			}

		};

		public Calendar currentTime() {
			return mCurrentTime;
		}

		public Date activeDate() {
			return mActiveDate;
		}

		/*public Date nextDoseTimeDate() {
			return mNextDoseTimeDate;
		}*/

		public int activeDoseTime() {
			return mActiveDoseTime;
		}

		public int nextDoseTime() {
			return mNextDoseTime;
		}

		private Calendar mCurrentTime;
		private Date mActiveDate;
		private Date mNextDoseTimeDate = null;
		private int mActiveDoseTime;
		private int mNextDoseTime;

		private DoseTimeInfo() {}
	}

	public static DoseTimeInfo getDoseTimeInfo()
	{
		/*final DoseTimeInfo dtInfo = DoseTimeInfo.INSTANCES.get();

		dtInfo.mCurrentTime = DateTime.nowCalendar();
		dtInfo.mActiveDate = getActiveDate(dtInfo.mCurrentTime);
		dtInfo.mActiveDoseTime = getActiveDoseTime(dtInfo.mCurrentTime);
		dtInfo.mNextDoseTime = getNextDoseTime(dtInfo.mCurrentTime);

		final Date currentDate = DateTime.getDatePart(dtInfo.mCurrentTime).getTime();

		if(dtInfo.mActiveDoseTime == Schedule.TIME_INVALID && dtInfo.mNextDoseTime == Schedule.TIME_MORNING)
		{
			// If active date is equal to the current date, we're somewhere before
			// midnight on that date and thus need to adjust mNextDoseTimeDate.

			if(dtInfo.mActiveDate.equals(currentDate))
				dtInfo.mNextDoseTimeDate = DateTime.add(currentDate, Calendar.DAY_OF_MONTH, 1);
		}

		if(dtInfo.mNextDoseTimeDate == null)
			dtInfo.mNextDoseTimeDate = currentDate;

		return dtInfo;*/

		return getDoseTimeInfo(DateTime.nowCalendar());
	}

	public static DoseTimeInfo getDoseTimeInfo(Calendar currentTime)
	{
		final DoseTimeInfo dtInfo = DoseTimeInfo.INSTANCES.get();

		dtInfo.mCurrentTime = currentTime;
		dtInfo.mActiveDate = getActiveDate(dtInfo.mCurrentTime);
		dtInfo.mActiveDoseTime = getActiveDoseTime(dtInfo.mCurrentTime);
		dtInfo.mNextDoseTime = getNextDoseTime(dtInfo.mCurrentTime);

		final Date currentDate = DateTime.getDatePartMutable(dtInfo.mCurrentTime).getTime();

		if(dtInfo.mActiveDoseTime == Schedule.TIME_INVALID && dtInfo.mNextDoseTime == Schedule.TIME_MORNING)
		{
			// FIXME problem lies here, as mActiveDoseTime could also be TIME_NIGHT

			// If active date is equal to the current date, we're somewhere before
			// midnight on that date and thus need to adjust mNextDoseTimeDate.

			if(dtInfo.mActiveDate.equals(currentDate))
				dtInfo.mNextDoseTimeDate = DateTime.add(currentDate, Calendar.DAY_OF_MONTH, 1);
		}

		if(dtInfo.mNextDoseTimeDate == null)
			dtInfo.mNextDoseTimeDate = currentDate;

		return dtInfo;
	}

	public static Date getActiveDate(Calendar time)
	{
		final Calendar activeDate = DateTime.getDatePartMutable(time);
		final int activeDoseTime = getActiveDoseTime(time);

		if(activeDoseTime == Drug.TIME_NIGHT && hasWrappingDoseTimeNight())
		{
			final DumbTime end = new DumbTime(getDoseTimeEndOffset(Drug.TIME_NIGHT));
			if(DateTime.isWithinRange(time, Constants.MIDNIGHT, end))
				activeDate.add(Calendar.DAY_OF_MONTH, -1);
		}

		return activeDate.getTime();
	}



	public static boolean isBeforeDoseTimeNightWrap(DoseTimeInfo dtInfo)
	{
		if(!hasWrappingDoseTimeNight())
			throw new IllegalStateException("!hasWrappingDoseTimeNight()");

		if(dtInfo.mActiveDoseTime != Schedule.TIME_NIGHT)
			throw new IllegalStateException("dtInfo.activeDoseTime != Schedule.TIME_NIGHT");

		final long endOfNightOffset = getDoseTimeEndOffset(Schedule.TIME_NIGHT);
		final long currentTimeOffset = DateTime.getOffsetFromMidnight(dtInfo.mCurrentTime);

		return currentTimeOffset > endOfNightOffset;
	}



	@SuppressWarnings("deprecation")
	public static Date getActiveDate() {
		return getActiveDate(DateTime.nowCalendarMutable());
	}

	public static int getActiveDoseTime(Calendar time)
	{
		for(int doseTime : Constants.DOSE_TIMES)
		{
			if(DateTime.isWithinRange(time, getDoseTimeBegin(doseTime), getDoseTimeEnd(doseTime)))
				return doseTime;
		}

		return Schedule.TIME_INVALID;
	}

	public static int getNextDoseTime(Calendar time) {
		return getNextDoseTime(time, false);
	}

	public static int getNextDoseTime(Calendar time, boolean useNextDayOffsets)
	{
		int retDoseTime = -1;
		long smallestDiff = 0;

		//Log.d(TAG, "getNextDoseTime: time=" + time);

		for(int doseTime : Constants.DOSE_TIMES)
		{
			long diff = getMillisUntilDoseTimeBegin(time, doseTime);
			if(useNextDayOffsets)
				diff += Constants.MILLIS_PER_DAY;

			//Log.d(TAG, "  diff " + diff + " for doseTime=" + doseTime);

			if(diff > 0 && (smallestDiff == 0 || diff < smallestDiff))
			{
				smallestDiff = diff;
				retDoseTime = doseTime;
			}
		}

		if(retDoseTime == -1)
		{
			if(!useNextDayOffsets)
				return getNextDoseTime(time, true);

			throw new IllegalStateException("retDoseTime == -1");
		}

		//Log.d(TAG, "  retDoseTime=" + retDoseTime);
		return retDoseTime;
	}

	public static int getLastNotificationMessageHash() {
		return sSharedPrefs.getInt(KEY_LAST_MSG_HASH, 0);
	}

	public static void setLastNotificationMessageHash(int messageHash)
	{
		Editor editor = sSharedPrefs.edit();
		editor.putInt(KEY_LAST_MSG_HASH, messageHash);
		editor.commit();
	}

	/*public int getLastNotificationCount() {
		return sSharedPrefs.getInt(KEY_LAST_MSG_COUNT, 0);
	}

	public void setLastNotificationCount(int notificationCount)
	{
		if(notificationCount < 0 || notificationCount > 3)
			throw new IllegalArgumentException();

		Editor editor = sSharedPrefs.edit();
		editor.putInt(KEY_LAST_MSG_COUNT, notificationCount);
		editor.commit();
	}*/

	public static int getListPreferenceValueIndex(String key, int defValue)
	{
		String valueStr = sSharedPrefs.getString(key, null);
		return valueStr != null ? Integer.parseInt(valueStr, 10) : defValue;
	}

	public static int getNotificationDefaultsXorMask()
	{
		int mask = 0;

		if(!sSharedPrefs.getBoolean("use_led", true))
			mask |= Notification.DEFAULT_LIGHTS;

		if(!sSharedPrefs.getBoolean("use_sound", true))
			mask |= Notification.DEFAULT_SOUND;

		if(!sSharedPrefs.getBoolean("use_vibrator", true))
			mask |= Notification.DEFAULT_VIBRATE;

		return mask;
	}

	public static String getDrugName(Drug drug)
	{
		final String name = drug.getName();
		// this should never happen unless there's a DB problem
		if(name == null || name.length() == 0)
			return "<???>";

		if(sSharedPrefs.getBoolean("privacy_scramble_names", false))
		{
			// We rot13 word by word and ignore those beginning with
			// a digit, so things like 10mg won't get converted to 10zt.

			final StringBuilder sb = new StringBuilder(name.length());
			for(String word : name.split(" "))
			{
				if(word.length() == 0 || Character.isDigit(word.charAt(0)))
					sb.append(word);
				else
					sb.append(Util.rot13(word));

				sb.append(" ");
			}

			return sb.toString();
		}

		return name;
	}

	private static final int FLAG_GET_MILLIS_UNTIL_BEGIN = 1;
	private static final int FLAG_DONT_CORRECT_TIME = 2;

	private static long getMillisUntilDoseTimeBeginOrEnd(Calendar time, int doseTime, int flags)
	{
		final long doseTimeOffsetMillis = (flags & FLAG_GET_MILLIS_UNTIL_BEGIN) != 0 ?
					getDoseTimeBeginOffset(doseTime) : getDoseTimeEndOffset(doseTime);

		/*final long doseTimeOffsetMillis;
		if((flags & FLAG_GET_MILLIS_UNTIL_BEGIN) != 0)
			doseTimeOffsetMillis = getDoseTimeBeginOffset(doseTime);
		else
			doseTimeOffsetMillis = getDoseTimeEndOffset(doseTime);*/

		final DumbTime doseTimeOffset = new DumbTime(doseTimeOffsetMillis);
		final Calendar target = DateTime.getDatePartMutable(time);

		// simply adding the millisecond offset is tempting, but leads to errors
		// when the DST begins/ends in this interval

		target.set(Calendar.HOUR_OF_DAY, doseTimeOffset.getHours());
		target.set(Calendar.MINUTE, doseTimeOffset.getMinutes());
		target.set(Calendar.SECOND, doseTimeOffset.getSeconds());

		if(target.getTimeInMillis() < time.getTimeInMillis() && (flags & FLAG_DONT_CORRECT_TIME) == 0)
			target.add(Calendar.DAY_OF_MONTH, 1);

		return target.getTimeInMillis() - time.getTimeInMillis();
	}

	// converts the string set [ "foo", "bar", "foobar", "barz" ] to the following string:
	// 4:3:foo3:bar6:foobar4:barz

	private static String stringSetToString(Set<String> set)
	{
		final StringBuilder sb = new StringBuilder();
		sb.append(set.size() + ":");

		for(String str : set)
			sb.append(str.length() + ":" + str);

		return sb.toString();
	}

	private static Set<String> stringToStringSet(String str)
	{
		final HashSet<String> stringSet = new HashSet<String>();

		if(str == null || str.length() == 0)
			return stringSet;

		Log.d(TAG, "stringToStringSet: str=" + str);

		SizePrefix info = getSizePrefix(str, 0);
		if(info.size == 0)
			return stringSet;

		final int size = info.size;

		int end = info.firstCharPos;

		for(int i = 0; i != size; ++i)
		{
			info = getSizePrefix(str, end);
			end = info.firstCharPos + info.size;
			stringSet.add(str.substring(info.firstCharPos, end));
		}

		return stringSet;
	}

	private static class SizePrefix
	{
		int size;
		int firstCharPos;
	}

	private static SizePrefix getSizePrefix(String str, int pos)
	{
		final StringBuilder sb = new StringBuilder();
		int i = pos;

		//Log.d(TAG, "getSizePrefix: pos=" + pos);
		//Log.d(TAG, "  str=" + str.substring(pos, pos + 5) + "...");

		for(; i != str.length() && str.charAt(i) != ':'; ++i)
		{
			final char ch = str.charAt(i);
			if(!Character.isDigit(ch))
				throw new IllegalArgumentException("Unexpected non-digit char at pos=" + i);

			sb.append(ch);
		}

		if(sb.length() == 0)
			throw new IllegalArgumentException("Unexpected token at pos=" + pos + " (" + str + ")");

		final SizePrefix prefix = new SizePrefix();
		prefix.size = Integer.parseInt(sb.toString());
		prefix.firstCharPos = i + 1;
		return prefix;
	}

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

	private static final OnSharedPreferenceChangeListener LISTENER = new OnSharedPreferenceChangeListener() {

		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPrefs, String key)
		{
			if(Theme.KEY.equals(key))
			{
				Theme.clearAttributeCache();

				Toast.makeText(sContext, R.string._toast_theme_changed, Toast.LENGTH_LONG).show();

				final PackageManager pm = sContext.getPackageManager();
				final Intent intent = pm.getLaunchIntentForPackage(sContext.getPackageName());
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
				GlobalContext.startActivity(intent);
			}
		}
	};


	private Settings() {}
}
