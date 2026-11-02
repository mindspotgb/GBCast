package com.flycast.emulator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.os.ParcelFileDescriptor;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public class AndroidStorageTest {
    private static final String TREE_URI =
            "content://app.gbcast.emulator.test.documents/tree/root";
    private static final String ROOT_DOCUMENT_URI = TREE_URI + "/document/root";

    @Test
    public void storageOperationsUseSelfContainedSafFixture() {
        try (ActivityScenario<NativeGLActivity> scenario = ActivityScenario.launch(NativeGLActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    AndroidStorage storage = activity.getStorage();
                    storage.setStorageDirectories(Collections.singletonList(TREE_URI));

                    assertTrue(storage.exists(TREE_URI));
                    FileInfo rootInfo = storage.getFileInfo(TREE_URI);
                    assertEquals(TREE_URI, rootInfo.getPath());
                    assertTrue(rootInfo.isDirectory());
                    assertNotEquals(0, rootInfo.getUpdateTime());
                    assertEquals("", storage.getParentUri(TREE_URI));

                    FileInfo[] rootChildren = storage.listContent(TREE_URI);
                    assertEquals(2, rootChildren.length);
                    FileInfo directory = find(rootChildren, "games");
                    FileInfo rootFile = find(rootChildren, "readme.txt");

                    assertTrue(directory.isDirectory());
                    assertFalse(rootFile.isDirectory());
                    assertEquals(directory.getPath(), storage.getSubPath(TREE_URI, "games"));
                    assertEquals(rootFile.getPath(), storage.getSubPath(TREE_URI, "readme.txt"));
                    assertTrue(storage.exists(directory.getPath()));
                    assertTrue(storage.exists(rootFile.getPath()));

                    FileInfo directoryInfo = storage.getFileInfo(directory.getPath());
                    assertEquals("games", directoryInfo.getName());
                    assertTrue(directoryInfo.isDirectory());
                    assertNotEquals(0, directoryInfo.getUpdateTime());
                    assertEquals(ROOT_DOCUMENT_URI, storage.getParentUri(directory.getPath()));

                    FileInfo[] directoryChildren = storage.listContent(directory.getPath());
                    assertEquals(1, directoryChildren.length);
                    FileInfo game = directoryChildren[0];
                    assertEquals("fixture.chd", game.getName());
                    assertFalse(game.isDirectory());
                    assertEquals(game.getPath(), storage.getSubPath(directory.getPath(), game.getName()));
                    assertEquals(directory.getPath(), storage.getParentUri(game.getPath()));
                    assertTrue(storage.exists(game.getPath()));

                    FileInfo gameInfo = storage.getFileInfo(game.getPath());
                    assertEquals(game.getPath(), gameInfo.getPath());
                    assertEquals(game.getName(), gameInfo.getName());
                    assertFalse(gameInfo.isDirectory());
                    assertNotEquals(0, gameInfo.getUpdateTime());

                    int fd = storage.openFile(game.getPath(), "r");
                    assertNotEquals(-1, fd);
                    ParcelFileDescriptor.adoptFd(fd).close();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static FileInfo find(FileInfo[] files, String name) {
        for (FileInfo file : files) {
            if (name.equals(file.getName())) return file;
        }
        throw new AssertionError("Missing SAF fixture entry: " + name);
    }
}
