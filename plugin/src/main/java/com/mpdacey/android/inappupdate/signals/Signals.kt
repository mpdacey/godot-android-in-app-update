package com.mpdacey.android.inappupdate.signals

import org.godotengine.godot.Dictionary
import org.godotengine.godot.plugin.SignalInfo

fun getSignals(): MutableSet<SignalInfo> = mutableSetOf(
    UpdateSignals.updateFound,
    UpdateSignals.updateReady,
    UpdateSignals.updateFailed,
    UpdateSignals.updateDownloadStatusUpdated
)

object UpdateSignals {
    val updateFound = SignalInfo("updateFound", Boolean::class.javaObjectType)
    val updateReady = SignalInfo("updateReady")
    val updateFailed = SignalInfo("updateFailed", String::class.javaObjectType, Int::class.javaObjectType)
    val updateDownloadStatusUpdated = SignalInfo("updateDownloadStatus", Float::class.javaObjectType)
}