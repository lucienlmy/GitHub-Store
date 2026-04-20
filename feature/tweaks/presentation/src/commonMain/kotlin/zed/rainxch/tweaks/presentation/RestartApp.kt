package zed.rainxch.tweaks.presentation

/**
 * Platform hook for the "restart now" Snackbar action after a
 * language change on Desktop. Tries to spawn a fresh JVM with the
 * same command line as the current process and then exit — so the
 * user ends up in a freshly-started app with their new locale
 * applied by `DesktopApp.main`. If that isn't possible (IDE/Gradle
 * runs, sandbox restrictions, etc.) the implementation falls back
 * to a plain exit; the user's preference is already persisted, so
 * they just need to reopen the app manually.
 *
 * This should never be invoked on Android — `MainActivity` handles
 * runtime language changes via `Activity.recreate()`, and the
 * `OnAppLanguageChangeRequiresRestart` event that triggers this is
 * never emitted there. The Android actual is therefore a no-op for
 * safety.
 */
expect fun restartAppAfterLanguageChange()
