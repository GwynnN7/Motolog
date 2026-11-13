package com.gwynn7.motolog

import android.app.Application
import com.gwynn7.motolog.backup.RestoreJournal
import kotlinx.coroutines.runBlocking

class MotoLogApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Complete rollback before any Activity, ViewModel, or service opens a database.
        val journal = RestoreJournal(this)
        if (journal.hasPendingRestore()) runBlocking { journal.recover() }
    }
}
