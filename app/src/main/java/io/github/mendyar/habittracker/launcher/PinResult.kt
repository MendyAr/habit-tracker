package io.github.mendyar.habittracker.launcher

/** Outcome of asking the launcher to add a habit's widget to the home screen. */
enum class PinResult {
    /** The launcher shows its confirmation. */
    REQUESTED,

    /** The habit already has one on the home screen; nothing was requested. */
    ALREADY_EXISTS,

    /** The launcher cannot add it. */
    UNSUPPORTED,
}
