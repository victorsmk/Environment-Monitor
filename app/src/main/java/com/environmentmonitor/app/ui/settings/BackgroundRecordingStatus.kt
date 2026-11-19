package com.environmentmonitor.app.ui.settings

/** What Settings says about background recording, kept apart from Android so it can be tested. */
data class BackgroundRecordingStatus(
    val notificationsAllowed: Boolean,
    val batteryUnrestricted: Boolean,
) {
    val notificationsText: String
        get() = if (notificationsAllowed) {
            "Notifications: allowed. A running session shows a notification with a Stop button."
        } else {
            "Notifications: blocked. Recording still works, but you will not see the notification " +
                "that shows it is running."
        }

    val batteryText: String
        get() = if (batteryUnrestricted) {
            "Battery: not restricted. Android should leave the recording alone."
        } else {
            "Battery: optimised. Some phones (Samsung especially) may stop a long recording to " +
                "save power. Choose \"All apps\", find this app and select \"Don't optimise\"."
        }

    /** True when everything that helps a night-long recording is in place. */
    val allGood: Boolean get() = notificationsAllowed && batteryUnrestricted
}
