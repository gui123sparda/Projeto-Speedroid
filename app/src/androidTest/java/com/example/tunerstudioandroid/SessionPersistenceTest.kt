package com.example.tunerstudioandroid

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SessionPersistenceTest {
    @Test fun cleanSnapshotRemainsCleanAndPendingEditsSurvive() {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val folder=File(app.cacheDir,"session-test-${System.nanoTime()}").apply { mkdirs() }
        val context=object:ContextWrapper(app) {
            override fun getApplicationContext():Context = this
            override fun getFilesDir():File = folder
        }
        val original=StudioSession(context)
        original.saveSession()
        val clean=StudioSession(context)
        assertNull(clean.history.firstOrNull())
        assertFalse("Clean tune must permit first ECU download",clean.ecu.isDirty)
        original.ecu.set("pinLayout",3.0)
        original.saveSession()
        val edited=StudioSession(context)
        assertEquals(3.0,edited.ecu.get("pinLayout"),0.001)
        assertTrue(edited.ecu.isDirty)
        original.close(false);clean.close(false);edited.close(false)
        folder.deleteRecursively()
    }
}
