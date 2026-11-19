package com.environmentmonitor.app.recording

/**
 * When to ask for the notification permission (needed on Android 13 and newer). Asked once, when the
 * first session starts. A refusal never stops a session: it keeps recording, only the notification is hidden.
 */
object NotificationPermission {
    const val REQUIRED_FROM_SDK = 33

    fun shouldAsk(sdk: Int, granted: Boolean, alreadyAsked: Boolean): Boolean =
        sdk >= REQUIRED_FROM_SDK && !granted && !alreadyAsked
}
