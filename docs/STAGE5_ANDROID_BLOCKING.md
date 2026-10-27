# Stage 5 — Android app limits and final hardening

## Distribution assumption

The repository currently documents local Android Studio use and `installDebug` on a connected device. No Google Play publication is assumed. If preparing a Play release, review the [Google Play Accessibility API policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en), complete the Accessibility declaration, present the prominent disclosure and affirmative consent in the app, and include the required use explanation in the store listing. Do not mark this accountability app as an accessibility tool. A policy-compliant design does not guarantee Play approval.

## How the blocker works

- The feature is off by default. The user visits **Limits**, selects apps and a rule, reads the disclosure, confirms, and then enables Steady in Android Accessibility settings. The service is not started by Steady before these user actions.
- Android's `AccessibilityService` receives only `TYPE_WINDOW_STATE_CHANGED` events. The service checks the transient foreground package name against the selected package list. It does not inspect accessibility nodes, screen text, window content, screenshots or notifications, and it does not perform clicks or gestures. No package event is logged, stored or synced.
- When a selected app matches an active rule, Steady displays a native `TYPE_ACCESSIBILITY_OVERLAY` with pause, break, emergency bypass and turn-off controls. Steady itself and configured essential system packages remain allowed. The app chooser omits common Settings, phone and permission-controller apps; users can add exceptions.
- The task rule releases when every selected task for today is complete, skipped, deleted or no longer scheduled today. The time rule releases at the configured local time. Pause expires after 15 minutes or the selected break duration; emergency bypass ends at the next local midnight.
- The blocker is an accountability aid, not a security boundary. It can be bypassed, paused, disabled in Android settings or uninstalled. Accessibility permission may be revoked and Android may stop services under system or battery rules. If the service is not connected, use **Limits → Refresh**, open Accessibility settings, and toggle Steady access off and back on. No boot receiver or hidden persistence is used.

## Android and Google policy references

- Android service setup and configuration: [Accessibility services](https://developer.android.com/guide/topics/ui/accessibility/service).
- Overlay window type: [`TYPE_ACCESSIBILITY_OVERLAY`](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY).
- Google Play: [Accessibility API policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en) and [Accessibility API declaration overview](https://support.google.com/googleplay/android-developer/answer/18258653?hl=en).

The service sets `canRetrieveWindowContent` and `canPerformGestures` to false. The XML configuration subscribes only to window-state changes. Android system UI labels and exact setting locations vary by manufacturer and OS release.

## Reminder behavior

Android reminders use local WorkManager jobs and notifications. Existing quiet-hours, daily-limit, snooze and stop-for-day rules still apply. A delivered reminder is recorded locally with its timestamp and reason; the visible history is bounded. Notifications can be delayed by Android battery and background scheduling. Reminder content is not uploaded.

## Automated checks

Run `gradle -p android testDebugUnitTest`. `BlockingRulesTest` covers selected/exception/essential apps, task completion and skip release, break-time release, pause and bypass expiry, disabled/stale rules, and service recovery states. These tests do not exercise Android's Accessibility overlay or prove device-level behavior.

## Manual real-device checklist

Use the target phone and a nonessential test app. Keep a path to Android Settings available.

1. Install/run Steady; confirm **Limits** shows blocking off. Open the disclosure and confirm it names the permission and foreground-package observation before sending you to Settings.
2. Dismiss the disclosure. Confirm no service is enabled. Reopen the feature, accept the disclosure, enable Steady under **Settings → Accessibility → Downloaded/Installed apps**, and return. Confirm the screen reports service readiness after refresh.
3. Select a harmless test app and today's task rule. Open the test app and verify the gentle overlay appears without repeated activity launches. Confirm Steady, Settings, launcher, phone/emergency calling UI, and permission settings remain usable.
4. Mark selected tasks complete, then separately repeat with a task skipped. Confirm the selected app is allowed after all selected tasks leave Planned state.
5. Re-enable the rule and test **Pause 15 minutes**, **Take a break**, and **Emergency bypass**. Confirm each control releases access for the stated period and blocking resumes only when its timer expires, if the rule remains active.
6. Add a user exception and confirm that app remains available. Turn blocking off from the overlay and from **Limits**; confirm the service is disabled and no later overlay appears.
7. While enabled, revoke Steady's Accessibility access in system Settings. Open Limits and confirm it reports missing access and offers recovery. Restore access and verify recovery after refresh. Also inspect behavior after Android stops the service; use the same recovery path.
8. Restart the phone and update/reinstall the app using the intended distribution route. Confirm actual Android behavior and recovery; do not assume permission persistence. Verify the user can still open Settings and disable access.
9. Uninstall Steady and confirm selected apps remain accessible. This demonstrates that the blocker is not a security boundary.
10. Configure reminders and exercise quiet hours, daily limit, snooze, stop-for-today and reminder history; confirm history explains each delivered nudge.

No target-device verification is claimed until these steps have been run on the actual phone. Android/OEM versions can affect Accessibility service restart and notification timing.
