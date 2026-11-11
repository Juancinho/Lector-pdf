// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader

import android.app.Application
import android.content.Context
import android.os.StrictMode
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.gitlab.mudlej.MjPdfReader.core.process.AppProcess
import com.gitlab.mudlej.MjPdfReader.data.AutoBackupScheduler
import com.gitlab.mudlej.MjPdfReader.data.OnlineDocumentStore
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.PrintController
import com.gitlab.mudlej.MjPdfReader.ui.reader.controls.PdfThemeController
import org.acra.ACRA

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (AppProcess.isPdfMetadata(this)) {
            return
        }
        if (!ACRA.isACRASenderServiceProcess()) {
            installDebugThreadPolicy()
            val preferences = Preferences(PreferenceManager.getDefaultSharedPreferences(this))
            AppCompatDelegate.setDefaultNightMode(PdfThemeController.interfaceNightMode(preferences))
            if (preferences.getAutoBackupEnabled()) {
                AutoBackupScheduler.ensureScheduled(this, preferences.getAutoBackupHour(), preferences.getAutoBackupMinute())
            }
            OnlineDocumentStore.sweepIncognito(this)
            PrintController.sweepStaging(this)
        }
    }

    private fun installDebugThreadPolicy() {
        if (!BuildConfig.DEBUG) {
            return
        }
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .penaltyLog()
                .build()
        )
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        if (AppProcess.isPdfMetadata(base)) {
            return
        }

        // MJ Study never initializes a remote crash reporter.
    }
}
