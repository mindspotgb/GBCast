package com.flycast.emulator.shell

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import gb.frame.DataAccess
import gb.frame.DataFile
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DirectDataFolderTest {
    @Test fun unavailableSelectedFolderNeverFallsBackToAnOldLocalSave() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        System.loadLibrary("flycast")
        val tree = requireNotNull(DocumentFile.fromTreeUri(app, Uri.parse("content://app.gbcast.emulator.test.documents/tree/root")))
        val folder = requireNotNull(tree.createDirectory("unavailable-data-test"))
        val local = File(app.cacheDir, "stale-data-test").apply { mkdirs() }
        val stale = File(local, "save.bin").apply { writeText("old local save") }
        try {
            DataAccess.configure(app, mapOf(local to folder)) {}
            DataAccess.bindNative(arrayOf(local.path))
            assertTrue(folder.delete())
            val selected = DataFile(stale.path)
            assertFalse(selected.exists())
            try { selected.readText(); fail("Must not read the old local save") } catch (_: IllegalArgumentException) {}
            try { selected.writeText("new save"); fail("Must not write to old local storage") } catch (_: IllegalStateException) {}
            assertEquals("old local save", stale.readText())
            assertFalse(DataAccess.verifyNativeFileOperations(DataFile(local, "native-check").path))
            assertFalse(File(local, "native-check").exists())
        } finally {
            DataAccess.configure(app, emptyMap()) {}; DataAccess.bindNative(emptyArray())
            stale.delete(); local.delete(); folder.delete()
        }
    }
    @Test fun kotlinAndNativeUseSelectedDocumentsWithoutLocalMirror() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        System.loadLibrary("flycast")
        val tree = requireNotNull(DocumentFile.fromTreeUri(app, Uri.parse("content://app.gbcast.emulator.test.documents/tree/root")))
        val folder = requireNotNull(tree.createDirectory("direct-data-test"))
        val local = File(app.cacheDir, "unused-data-mirror")
        assertFalse(local.exists())
        try {
            DataAccess.configure(app, mapOf(local to folder)) { error(it) }
            DataAccess.bindNative(arrayOf(local.path))
            val file = DataFile(local, "nested/save.bin")
            file.parentFile!!.mkdirs()
            file.writeText("first")
            assertFalse(File(local, "nested/save.bin").exists())
            val nested = requireNotNull(folder.findFile("nested"))
            val saved = requireNotNull(nested.findFile("save.bin"))
            assertEquals("first", app.contentResolver.openInputStream(saved.uri)!!.bufferedReader().use { it.readText() })
            app.contentResolver.openOutputStream(saved.uri, "wt")!!.use { it.write("external edit".toByteArray()) }
            assertEquals("external edit", file.readText())
            assertTrue(DataAccess.verifyNativeFileOperations(DataFile(local, "native-check").path))
            assertFalse(local.exists())
            assertEquals(listOf("save.bin"), file.parentFile!!.listFiles()!!.map { it.name })
            try { DataFile(local, "../escape").writeText("bad"); fail("Traversal must be rejected") } catch (_: IllegalArgumentException) {}
            assertTrue(file.delete()); assertTrue(file.parentFile!!.delete())
        } finally {
            folder.listFiles().forEach { it.delete() }; folder.delete()
            DataAccess.configure(app, emptyMap()) {}
            DataAccess.bindNative(emptyArray())
        }
    }
}
