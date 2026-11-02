// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home.scan

import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanDiscoveryBatchTest {
    @Test fun flushedBatchPreservesDiscoveryOrder() = runBlocking {
        val events = mutableListOf<ScanEvent>()
        val batch = ScanBatch(12, DiscoverySource.MEDIA_STORE, events::add, this)
        val z = ScannedPdfEntry("/books/z.pdf", 7, 1_000, null)
        val a = z.copy(path = "/books/a.pdf")
        try {
            batch.add(z)
            batch.add(a)
            batch.flush()

            assertEquals(listOf(ScanEvent.DiscoveryBatch(12, DiscoverySource.MEDIA_STORE, listOf(z, a))), events)
        } finally {
            batch.close()
        }
    }

    @Test fun closeDiscardsPendingRowsAndStopsTheirTimer() = runBlocking {
        val events = mutableListOf<ScanEvent>()
        val batch = ScanBatch(12, DiscoverySource.MEDIA_STORE, events::add, this)
        batch.add(ScannedPdfEntry("/books/book.pdf", 7, 1_000, null))

        batch.close()
        delay(450)

        assertTrue(events.isEmpty())
    }

    @Test fun closeWaitsForSuspendedTimerEmissionToBeCancelled() = runBlocking {
        val emitting = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val batch = ScanBatch(12, DiscoverySource.MEDIA_STORE, {
            emitting.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                stopped.complete(Unit)
            }
        }, this)
        try {
            batch.add(ScannedPdfEntry("/books/book.pdf", 7, 1_000, null))
            withTimeout(2_000) { emitting.await() }

            withTimeout(2_000) { batch.close() }

            assertTrue(stopped.isCompleted)
        } finally {
            batch.close()
        }
    }

    @Test fun timerEmissionFailurePropagatesToItsDiscoveryScope() = runBlocking {
        val failure = IllegalStateException("emitter failed")
        var thrown: Exception? = null
        try {
            withTimeout(2_000) {
                coroutineScope {
                    val batch = ScanBatch(12, DiscoverySource.MEDIA_STORE, { throw failure }, this)
                    try {
                        batch.add(ScannedPdfEntry("/books/book.pdf", 7, 1_000, null))
                        awaitCancellation()
                    } finally {
                        batch.close()
                    }
                }
            }
        } catch (error: Exception) {
            thrown = error
        }

        assertEquals(IllegalStateException::class.java, thrown?.javaClass)
        assertEquals("emitter failed", thrown?.message)
    }
}
