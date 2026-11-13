package com.gwynn7.motolog.Database

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes writes (including photos) with backup snapshots and restore replacement. */
object DataAccess {
    private val mutex = Mutex()
    suspend fun <T> write(block: suspend () -> T): T = mutex.withLock { block() }
}
