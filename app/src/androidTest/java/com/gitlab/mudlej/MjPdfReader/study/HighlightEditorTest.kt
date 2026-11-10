// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study

import android.content.Intent
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.study.annotation.HighlightStyle
import com.gitlab.mudlej.MjPdfReader.study.annotation.HighlightStyleStore
import com.gitlab.mudlej.MjPdfReader.study.ui.HighlightStyleDialog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HighlightEditorTest {
    private fun launch() = ActivityScenario.launch<StudyPreviewActivity>(Intent(
        InstrumentationRegistry.getInstrumentation().targetContext, StudyPreviewActivity::class.java))
    private fun editor(activity: StudyPreviewActivity) =
        activity.supportFragmentManager.findFragmentByTag(HighlightStyleDialog.RESULT) as HighlightStyleDialog

    @Test fun invalidDraftSurvivesRecreationAndCancelDoesNotWrite() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = HighlightStyleStore(PreferenceManager.getDefaultSharedPreferences(context))
        val before = store.current
        launch().use { scenario ->
            scenario.onActivity { activity ->
                HighlightStyleDialog.newInstance().showNow(activity.supportFragmentManager, HighlightStyleDialog.RESULT)
                val dialog = editor(activity).requireDialog() as AlertDialog
                dialog.findViewById<EditText>(R.id.study_hex_input)!!.setText("#XX")
                assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val dialog = editor(activity).requireDialog() as AlertDialog
                assertEquals("#XX", dialog.findViewById<EditText>(R.id.study_hex_input)!!.text.toString())
                assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
        }
        assertEquals(before, store.current)
    }

    @Test fun customColorCommitsAndSynchronizesRgbFields() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        launch().use { scenario ->
            scenario.onActivity { activity ->
                HighlightStyleDialog.newInstance().showNow(activity.supportFragmentManager, HighlightStyleDialog.RESULT)
                val dialog = editor(activity).requireDialog() as AlertDialog
                dialog.findViewById<EditText>(R.id.study_hex_input)!!.setText("#193A7E")
                assertEquals("25", dialog.findViewById<EditText>(R.id.study_red_input)!!.text.toString())
                assertEquals("58", dialog.findViewById<EditText>(R.id.study_green_input)!!.text.toString())
                assertEquals("126", dialog.findViewById<EditText>(R.id.study_blue_input)!!.text.toString())
                dialog.findViewById<com.google.android.material.slider.Slider>(R.id.study_opacity_slider)!!.value = 73f
                assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.File(context.filesDir, "study-editor.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            scenario.onActivity { activity ->
                (editor(activity).requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
        }
        assertEquals(HighlightStyle(0x193A7E, 73).argb,
            HighlightStyleStore(PreferenceManager.getDefaultSharedPreferences(context)).current.argb)
    }
}
