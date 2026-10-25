# Stage 3: accounts and sync

## Supabase setup

1. Create a Supabase project and leave email/password authentication enabled. Choose whether email confirmation is required; when enabled, users must confirm before signing in.
2. In the Supabase SQL Editor, run [`../supabase/migrations/20261025000000_stage3_sync.sql`](../supabase/migrations/20261025000000_stage3_sync.sql). It creates `user_profiles`, `tasks`, `daily_check_ins`, and `reminder_preferences`, indexes, timestamp triggers, grants, and separate SELECT/INSERT/UPDATE owner policies. Client deletes use `deleted_at` tombstones; clients are not granted hard-delete privileges.
3. In Authentication → URL Configuration, set the Site URL to the deployed web origin (for local development, `http://localhost:5173`). Add `http://localhost:5173/**`, your actual Windows web origin, and `steady://login-callback` to Redirect URLs. The current clients use email/password; the Android scheme is configured for confirmation/OTP links and future supported auth callbacks.
4. Copy `web/.env.example` to `web/.env.local`; set `VITE_SUPABASE_URL` and `VITE_SUPABASE_PUBLISHABLE_KEY` from the project API settings. In Android, copy `android/local.properties.example` to ignored `android/local.properties`, keep or set `sdk.dir`, and set `supabase.url` and `supabase.publishableKey`. Restart Vite/rebuild Android after configuration changes.
5. Use only a Supabase publishable key (legacy anon key is also accepted by Supabase). Never put a secret/service-role key in either client. Supabase publishable keys are public client identifiers; Postgres RLS is the data boundary.
6. In Table Editor, confirm RLS is enabled on all four tables. Test with two separate accounts: each must only see its own profile, tasks, check-ins, and preferences. The SQL grants authenticated clients only select/insert/update and the policy `auth.uid() = user_id` on each operation.

## Local-first behavior and merge rule

Room and IndexedDB remain the source of truth for immediate edits. Pending local task/check-in writes remain on-device until Supabase accepts them. Sign-in, app start, explicit sync, and network reconnect pull remote rows and retry queued writes. Failed/expired-auth requests leave the local copy intact and surface “Needs attention”; a later sign-in/reconnect retries. Account ownership is pinned on first sync; signing into another account is blocked from syncing that device's existing local data.

The client compares ISO `updated_at` timestamps. The strictly newer record wins; exact ties use the remote row to make devices converge. The database update trigger advances server timestamps monotonically. There is no field-by-field merge: concurrent edits to one task/check-in can replace the other whole record, so the most recently timestamped version wins. Deleted tasks retain their ID and `deleted_at` so another device can apply the deletion rather than resurrecting the task. The current app has no restore-from-tombstone UI.

`reminder_preferences` is included as a private schema destination for future sync expansion. Current Stage 3 sync covers tasks and daily check-ins. Reminder timing and delivery state continue to be device-local so notification behavior does not depend on the network. Auth passwords and task notes are never logged by the app; no notification content is uploaded.

## Validation checklist with a real project

- Create an account on Android and sign in on Windows using the same email.
- Create a task on Android while online; wait for the Android state to show Synced and confirm the task appears on Windows after sync.
- Edit the task on Windows; confirm the change appears on Android after launch/reconnect.
- Add a morning plan and evening reflection on one device; confirm they appear on the other.
- Make edits offline on each client, reconnect, and verify the timestamp rule and pending local state.
- Delete a task on one device; confirm it stays absent on the other after sync and a restart.
- Sign out, confirm local data remains, then sign back into the same account and sync.
- Inspect the SQL policies with two accounts and verify cross-account reads/writes are denied.

The automated tests do not substitute for this project-backed checklist. Until it is run against a configured project on both devices, cross-device sync is implemented but not verified end to end.
