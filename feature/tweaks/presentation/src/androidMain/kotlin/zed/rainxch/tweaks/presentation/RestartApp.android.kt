package zed.rainxch.tweaks.presentation

/**
 * No-op on Android — runtime language changes are applied via
 * `Activity.recreate()` from `MainActivity`, and the triggering
 * event (`OnAppLanguageChangeRequiresRestart`) is never emitted on
 * this platform. We keep an actual so common code compiles; if the
 * invariant ever breaks we'd rather silently skip than kill the
 * process.
 */
actual fun restartAppAfterLanguageChange() {
    // Intentionally empty.
}
