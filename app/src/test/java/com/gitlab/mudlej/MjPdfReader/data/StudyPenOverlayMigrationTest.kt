// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.data

import com.gitlab.mudlej.MjPdfReader.support.InMemorySharedPreferences
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyPenOverlayMigrationTest {
    private val pen = ConfigurableAction.STUDY_PEN.id
    private val exit = ConfigurableAction.EXIT_FULLSCREEN.id

    @Test
    fun defaultsAlreadyShowWriteRightAfterExit() {
        val preferences = Preferences(InMemorySharedPreferences())
        preferences.ensureStudyPenInFullScreenOverlayOnce()
        assertTrue(pen in preferences.getFullScreenOverlayActions())
        assertEquals(listOf(exit, pen), preferences.getFullScreenOverlayActionOrder().take(2))
    }

    @Test
    fun customisedMenusGainWriteOnceAfterExit() {
        val stored = InMemorySharedPreferences(
            mapOf(
                Preferences.fullScreenOverlayActionsKey to setOf(exit, ConfigurableAction.ROTATE.id),
                Preferences.fullScreenOverlayActionOrderKey to "${ConfigurableAction.ROTATE.id},$exit",
            )
        )
        val preferences = Preferences(stored)
        preferences.ensureStudyPenInFullScreenOverlayOnce()
        assertTrue(pen in preferences.getFullScreenOverlayActions())
        assertEquals(listOf(ConfigurableAction.ROTATE.id, exit, pen), preferences.getFullScreenOverlayActionOrder())

        // A later removal by the user is respected.
        preferences.setFullScreenOverlayActions(setOf(exit))
        preferences.ensureStudyPenInFullScreenOverlayOnce()
        assertFalse(pen in preferences.getFullScreenOverlayActions())
    }
}
