# Stage 4: Android usage dashboard and app limits

## Android APIs and permission flow

- The dashboard uses `UsageStatsManager.queryUsageStats(INTERVAL_DAILY, start, end)` and reads Android’s aggregated `totalTimeInForeground` values. Android documents that these are interval aggregates and may be delayed or approximate.
- The manifest declares `android.permission.PACKAGE_USAGE_STATS`. This is special access: the app checks `AppOpsManager.OPSTR_GET_USAGE_STATS` and only opens `Settings.ACTION_USAGE_ACCESS_SETTINGS` after the user has opened Usage, read the disclosure, and enabled the switch. The app checks again when it returns from Settings.
- The app picker uses a targeted `MAIN` + `LAUNCHER` package-visibility query. It does not declare `QUERY_ALL_PACKAGES`. Apps without a launcher entry can be absent from the picker.
- Queries run only when the Usage dashboard opens, returns to the foreground, or the user taps refresh. There is no background monitoring or app blocking.
- The query window uses local midnight to the next local midnight, bounded at the current time. Because Android may expand a daily query to whole calendar buckets, the repository keeps only records whose bucket begins within the current local day and avoids adding duplicate records for one app. Android may update totals later; daily buckets are not guaranteed to be perfectly real time.

## Local data and privacy

Room stores the user's selected app names, optional daily thresholds, and daily totals for selected apps. It does not store raw usage events. None of these values are sent to Supabase or another service. Turning usage off removes every saved usage aggregate; the selected app list and thresholds remain on the device for a later opt-in. A threshold warning appears inside the dashboard on refresh/open and never prevents launching an app.

The feature does not read notifications, messages, browser contents, contacts, or location. It uses no accessibility service, foreground service, scheduled worker, or hidden polling.

## Automated tests

Run Android local unit tests from Android Studio with **Run `:app:testDebugUnitTest`** or from the repository's Android Gradle installation. The suite covers local-day boundaries including DST, daily bucket filtering and duplicate handling, access-state presentation, threshold comparisons, negative-duration normalization, and the deletion rule when tracking is disabled.

## Manual Android device checklist

1. Install the app on a physical Android device. Confirm the Usage tab is present and usage is off by default.
2. Open Usage and read the explanation. Confirm no system settings page opens until you enable the switch.
3. Enable it and grant Usage Access in Android Settings. Return to Steady, refresh, tap **Add app**, search for an app, and confirm the picker shows its icon and name. Select it and confirm only selected apps appear on the Usage page.
4. Select an app, use it for a few minutes, return to Steady, and confirm a daily total appears. Compare with Android's own usage screen, allowing for update delay and rounding.
5. Set a low daily limit, use the selected app past it, then open or refresh Usage and confirm the in-app warning appears. Confirm the app still opens normally.
6. Turn off usage. Confirm the dashboard stops querying and saved totals disappear. Reopen Usage and confirm it remains off.
7. Re-enable the feature and deny or revoke Usage Access in system Settings. Confirm the app shows a helpful access-needed state and does not show old totals as current.
8. Check a device profile or managed-device policy where Usage Access is restricted, and a device where the Usage Access settings activity is unavailable if possible. Confirm the app gives a clear unavailable/restricted state and remains usable.
9. Change the device date across local midnight or validate the boundary in tests; confirm totals are labeled for the current local day.
10. Sign in to Supabase and confirm usage selections, thresholds, and totals do not appear in remote tables or on the web app.

Usage reporting has not been claimed as device-verified until this manual checklist is exercised on a real Android phone. Emulator results are not treated as device validation.
