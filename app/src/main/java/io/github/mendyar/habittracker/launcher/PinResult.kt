package io.github.mendyar.habittracker.launcher

/** Outcome of asking the launcher to add a habit's icon or widget to the home screen. */
enum class PinResult {
    /** The launcher shows its confirmation. */
    REQUESTED,

    /** Sent to a pre-Android 8 launcher, which adds it without confirming or replying. */
    SENT,

    /** The habit already has one on the home screen; nothing was requested. */
    ALREADY_EXISTS,

    /** The launcher cannot add it. */
    UNSUPPORTED,
}
