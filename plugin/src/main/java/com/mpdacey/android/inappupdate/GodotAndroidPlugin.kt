package com.mpdacey.android.inappupdate

import android.R
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.mpdacey.android.inappupdate.signals.UpdateSignals.updateDownloadStatusUpdated
import com.mpdacey.android.inappupdate.signals.UpdateSignals.updateFailed
import com.mpdacey.android.inappupdate.signals.UpdateSignals.updateFound
import com.mpdacey.android.inappupdate.signals.UpdateSignals.updateReady
import org.godotengine.godot.Godot
import org.godotengine.godot.GodotActivity
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot
import com.mpdacey.android.inappupdate.signals.getSignals
import org.godotengine.godot.gl.GodotRenderer
import org.godotengine.godot.plugin.GodotPluginRegistry

class GodotAndroidPlugin(godot: Godot): GodotPlugin(godot) {

    private val DAYS_FOR_FLEXIBLE_UPDATE = 7

    private lateinit var appUpdateManager: AppUpdateManager
    private lateinit var activityResultLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var installListener: InstallStateUpdatedListener

    override fun getPluginName() = BuildConfig.GODOT_PLUGIN_NAME

    override fun getPluginSignals(): Set<SignalInfo?> {
        return getSignals()
    }

    @UsedByGodot
    fun appReopened() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                // If an in-app update is already running, resume the update.
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    activityResultLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build())
            }

            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADING) {
                val bytesDownloaded = appUpdateInfo.bytesDownloaded()
                val totalBytesToDownload = appUpdateInfo.totalBytesToDownload()
                emitSignal(
                    updateDownloadStatusUpdated.name,
                    bytesDownloaded.toFloat() / totalBytesToDownload
                )
            }

            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                emitSignal(
                    updateReady.name
                )
            }
        }
    }

    /**
     * Example showing how to declare a method that's used by Godot.
     *
     * Shows a 'Hello World' toast.
     */
    @UsedByGodot
    fun helloWorld() {
        runOnHostThread {
            Toast.makeText(context, "Hello!", Toast.LENGTH_SHORT).show()

            val snackbar: Snackbar = Snackbar.make(
                activity!!.findViewById<View>(R.id.content).rootView,
                "Snek bah",
                Snackbar.LENGTH_INDEFINITE
            )
//            snackbar.setAction("HELP ME") {
//                Toast.makeText(context, "Hello again!", Toast.LENGTH_SHORT).show()
//            }
//            snackbar.show()

//            Snackbar.make(
//                activity!!.findViewById(R.id.content),
//                "An update has just been downloaded.",
//                Snackbar.LENGTH_INDEFINITE
//            ).apply {
//                setAction("Test") { Toast.makeText(context, "Hello!", Toast.LENGTH_SHORT).show() }
//                show()
//            }
        }
    }

    @UsedByGodot
    fun checkForUpdate() {
        appUpdateManager = AppUpdateManagerFactory.create(activity!!)

        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.updatePriority() >= 4 /* high priority */
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE))
            {
                emitSignal(
                    updateFound.name,
                    true
                )
            }

            else if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && (appUpdateInfo.clientVersionStalenessDays() ?: -1) >= DAYS_FOR_FLEXIBLE_UPDATE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE))
            {
                emitSignal(
                    updateFound.name,
                    true
                )
            }

            else if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE))
            {
                emitSignal(
                    updateFound.name,
                    false
                )
            }
        }
    }

    @UsedByGodot
    fun setImmediateUpdate() {
        installListener = InstallStateUpdatedListener { state ->
            if (state.installStatus() == InstallStatus.DOWNLOADED) {
                installUpdate()
            }
        }

        appUpdateManager.registerListener(installListener)

        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                activityResultLauncher,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
            )
        }

        (activity!! as GodotActivity).registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result: ActivityResult ->
            // handle callback
            if (result.resultCode != RESULT_OK) {
                Log.v(pluginName, "Update flow failed! Result code: " + result.resultCode);
                // If the update is canceled or fails,
                // you can request to start the update again.
                emitSignal(
                    updateFailed.name,
                    "Update flow failed! Result code: " + result.resultCode,
                    result.resultCode
                )
            }
        }

        appUpdateManager.unregisterListener(installListener)
    }

    @UsedByGodot
    fun setFlexibleUpdate() {
        installListener = InstallStateUpdatedListener{ state ->
            if (state.installStatus() == InstallStatus.DOWNLOADING) {
                val bytesDownloaded = state.bytesDownloaded()
                val totalBytesToDownload = state.totalBytesToDownload()
                emitSignal(
                    updateDownloadStatusUpdated.name,
                    bytesDownloaded.toFloat() / totalBytesToDownload
                )
            }

            if (state.installStatus() == InstallStatus.DOWNLOADED) {
                emitSignal(
                    updateReady.name
                )
                appUpdateManager.unregisterListener(installListener)
            }
        }

        (activity!! as GodotActivity).registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result: ActivityResult ->
            // handle callback
            if (result.resultCode != RESULT_OK) {
                Log.v(pluginName, "Update flow failed! Result code: " + result.resultCode);
                // If the update is canceled or fails,
                // you can request to start the update again.
                emitSignal(
                    updateFailed.name,
                    "Update flow failed! Result code: " + result.resultCode,
                    result.resultCode
                )
            }
        }
    }

    @UsedByGodot
    fun installUpdate() {
        appUpdateManager.completeUpdate()
    }

    // This function will not work. It either does nothing or crashes. Feels bad man.
    // An in game notification is required until I figure out how to fix this :(
    @Deprecated("This function will not work. It either does nothing or crashes. Feels bad man.")
    fun popupSnackbarForCompleteUpdate() {
        Snackbar.make(
            (activity!! as GodotActivity).findViewById(R.id.content),
            "An update has just been downloaded.",
            Snackbar.LENGTH_INDEFINITE
        ).apply {
            setAction("RESTART") { appUpdateManager.completeUpdate() }
            setActionTextColor(ContextCompat.getColor(context, R.color.system_primary_light))
            show()
        }
    }
}