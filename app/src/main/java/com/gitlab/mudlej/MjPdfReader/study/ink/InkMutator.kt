// SPDX-License-Identifier: GPL-3.0-or-later
package com.gitlab.mudlej.MjPdfReader.study.ink

interface InkWriter {
    fun add(stroke: InkStroke): Boolean
    fun remove(stroke: InkStroke): Boolean
}

object InkMutator {
    /** Roll back completed operations if any step fails; never journal half a transform. */
    fun apply(writer: InkWriter, change: InkChange): Boolean {
        val removed = mutableListOf<InkStroke>(); val added = mutableListOf<InkStroke>()
        fun rollback() {
            added.asReversed().forEach { writer.remove(it) }
            removed.forEach { writer.add(it) }
        }
        try {
            for (stroke in change.before) {
                if (!writer.remove(stroke)) { rollback(); return false }
                removed.add(stroke)
            }
            for (stroke in change.after) {
                if (!writer.add(stroke)) { rollback(); return false }
                added.add(stroke)
            }
            return true
        } catch (_: RuntimeException) { rollback(); return false }
    }
}
