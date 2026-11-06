package io.github.mendyar.habittracker.data

/**
 * Rules for the sessions of duration habits, shared by icons, widgets and the app:
 *  - a tap with no session running starts one ("Starting");
 *  - a tap within [GRACE_MS] of the start changes nothing ("Already Started");
 *  - a later tap finishes it ("Ended", with its length);
 *  - a tap within [GRACE_MS] of the end changes nothing ("Already Ended", the same length);
 *  - after that, a tap starts a new session.
 */
object Sessions {
    /** Taps this soon after a session started or ended are double taps: nothing changes. */
    const val GRACE_MS = 3_000L

    /** A session running longer than this when tapped was probably forgotten: ask first. */
    const val LONG_RUNNING_MS = 3 * 3_600_000L
}
