# Stage 3: accounts and sync

## Supabase setup

1. Create a Supabase project and leave email/password authentication enabled. Choose whether email confirmation is required; when enabled, users must confirm before signing in.
2. In the Supabase SQL Editor, run [`../supabase/migrations/20261025000000_stage3_sync.sql`](../supabase/migrations/20261025000000_stage3_sync.sql). It creates `user_profiles`, `tasks`, `daily_check_ins`, and `reminder_preferences`, indexes, timestamp triggers, grants, and separate SELECT/INSERT/UPDATE owner policies. Client deletes use `deleted_at` tombstones; clients are not granted hard-delete privileges.
3. In Authentication → URL Configuration, set the Site URL to the deployed web origin (for local development, `http://localhost:5173`). Add `http://localhost:5173/**`, your actual Windows web origin, and `steady://login-callback` to Redirect URLs.
4. Copy `web/.env.example` to `web/.env.local`; set `VITE_SUPABASE_URL` and `VITE_SUPABASE_PUBLISHABLE_KEY` from the project API settings. In Android, copy `android/local.properties.example` to ignored `android/local.properties`, keep or set `sdk.dir`, and set `supabase.url` and `supabase.publishableKey`. Restart Vite/rebuild Android after configuration changes.
5. Use only a Supabase publishable key (legacy anon key is also accepted by Supabase). Never put a secret/service-role key in either client. Supabase publishable keys are public client identifiers; Postgres RLS is the data boundary.
6. In Table Editor, confirm RLS is enabled on all four tables. Test with two separate accounts: each must only see its own profile, tasks, check-ins, and preferences. The SQL grants authenticated clients only select/insert/update and the policy `auth.uid() = user_id` on each operation.

## Google sign-in, account linking, and recovery

Google sign-in buttons are present on web and Android. In Supabase Dashboard, enable Google under Authentication → Providers. Create an OAuth web client in Google Cloud, set its authorized redirect URI to the callback URL shown by Supabase, then enter the client ID and secret in the Supabase provider settings. Do not copy the Google client secret into either app. Add each client origin / `steady://login-callback` to Supabase Redirect URLs. Configure Google OAuth consent screen and test accounts as required by Google. For account linking, enable **Manual Identity Linking** in Supabase Auth; users must already be signed in to link Google to an existing account. Google sign-in without linking may create or select a different account according to Supabase identity rules.

Forgot password sends a Supabase recovery email. Add the web origin and Android `steady://login-callback` to allowed redirects, and ensure the Supabase email template links preserve the recovery redirect. On Android, the app opens the recovery form after the deep link. On web, the Supabase `PASSWORD_RECOVERY` event opens the password update form. Supabase intentionally does not reveal whether the submitted email belongs to an account.

## Local-first behavior and merge rule

Room and IndexedDB remain the source of truth for immediate edits. Pending local task/check-in writes remain on-device until Supabase accepts them. Sign-in, app start, explicit sync, and network reconnect pull remote rows and retry queued writes. Failed/expired-auth requests leave the local copy intact and surface “Needs attention”; a later sign-in/reconnect retries. Account ownership is pinned on first sync; signing into another account is blocked from syncing that device's existing local data.

The client compares ISO `updated_at` timestamps. The strictly newer record wins; exact ties use the remote row to make devices converge. The database update trigger advances server timestamps monotonically. There is no field-by-field merge: concurrent edits to one task/check-in can replace the other whole record, so the most recently timestamped version wins. This is automatic last-write-wins conflict handling; there is no side-by-side conflict picker. Deleted tasks retain their ID and `deleted_at` so another device can apply the deletion rather than resurrecting the task. A user can undo a recent task deletion on either client; undo writes a newer restore so it syncs safely.

Each client now keeps a separate local task/check-in store for each signed-in Supabase user, plus a guest/local store. Switching accounts changes the active local scope before sync. On upgrade, existing local data is assigned to the account that previously owned sync on that installation; first-time unowned local data is claimed by the first account that signs in. Existing data created while accounts were previously mixed on one device cannot be attributed retroactively per task; the Android upgrade assigns that legacy set to the installation's previously saved sync owner, and web uses its saved `syncOwnerId` where available.

## Migration and backup checks

Android Room upgrades use explicit, additive migrations. Before distributing an update that changes entities, add a migration and exercise upgrade from every supported prior database version while preserving representative tasks, check-ins, reminders, and usage preferences. Keep Android encrypted backup snapshots and web browser storage exports before a major upgrade. Restore snapshots only on the intended app installation; Android's encrypted snapshot uses that installation's Keystore key. Supabase schema changes should be applied as versioned SQL migrations, then verify RLS and row ownership before clients are updated. The app does not currently run a cloud backup or automatically restore local data from Supabase after a local database is erased.

`reminder_preferences` is included as a private schema destination for future sync expansion. Current Stage 3 sync covers tasks and daily check-ins. Reminder timing and delivery state continue to be device-local so notification behavior does not depend on the network. Auth passwords and task notes are never logged by the app; no notification content is uploaded.

## Validation checklist with a real project

- Create an account on Android and sign in on Windows using the same email.
- Create distinct tasks and check-ins under accounts A and B on the same Android installation and same browser; switch accounts repeatedly and verify neither local list shows the other's records. Confirm signing out shows only guest/local records.
- Upgrade an Android installation with existing local data and a previously synced account; verify the records remain under that prior account after the Room v5-to-v6 migration.
- Create a task on Android while online; wait for the Android state to show Synced and confirm the task appears on Windows after sync.
- Edit the task on Windows; confirm the change appears on Android after launch/reconnect.
- Add a morning plan and evening reflection on one device; confirm they appear on the other.
- Make edits offline on each client, reconnect, and verify the timestamp rule and pending local state.
- Delete a task on one device; confirm it stays absent on the other after sync and a restart.
- Sign out, confirm local data remains, then sign back into the same account and sync.
- Test Google sign-in on both clients, link Google while signed in, then verify the linked account can sign in again.
- Request password recovery on each client, open the email on the same device/browser, set a new password, and sign in with it.
- Delete a task and use Undo before leaving the screen; confirm the restored task syncs to the other client.
- Upgrade an Android database containing sample records through the current Room migrations and confirm records remain present; export and reopen a backup on the same installation.
- Inspect the SQL policies with two accounts and verify cross-account reads/writes are denied.

The automated tests do not substitute for this project-backed checklist. Until it is run against a configured project on both devices, OAuth, recovery, account linking, and cross-device sync are implemented but not verified end to end. Android permission and blocker recovery checks still require the intended physical phone; use the Stage 4 and Stage 5 checklists.
