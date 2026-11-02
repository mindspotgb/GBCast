package com.flycast.emulator;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

public class TestDocumentsProvider extends DocumentsProvider {
    private static final String ROOT = "root";
    private static final String DIRECTORY = "root/games";
    private static final String ROOT_FILE = "root/readme.txt";
    private static final String GAME_FILE = "root/games/fixture.chd";
    private static final String DIRECTORY_MIME = DocumentsContract.Document.MIME_TYPE_DIR;
    private static final String[] DOCUMENT_COLUMNS = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
    };

    private File fixtureRoot;

    @Override
    public boolean onCreate() {
        fixtureRoot = new File(requireProviderContext().getCacheDir(), "android-storage-fixture");
        File games = fileFor(DIRECTORY);
        if (!games.exists() && !games.mkdirs()) return false;
        try {
            writeFixture(fileFor(ROOT_FILE), "GBCast SAF fixture\n");
            writeFixture(fileFor(GAME_FILE), "fixture-data");
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : new String[] {
                DocumentsContract.Root.COLUMN_ROOT_ID,
                DocumentsContract.Root.COLUMN_DOCUMENT_ID,
                DocumentsContract.Root.COLUMN_TITLE,
                DocumentsContract.Root.COLUMN_FLAGS
        });
        MatrixCursor.RowBuilder row = cursor.newRow();
        add(row, cursor, DocumentsContract.Root.COLUMN_ROOT_ID, ROOT);
        add(row, cursor, DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT);
        add(row, cursor, DocumentsContract.Root.COLUMN_TITLE, "GBCast test storage");
        add(row, cursor, DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_SUPPORTS_CREATE);
        return cursor;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : DOCUMENT_COLUMNS);
        includeDocument(cursor, documentId);
        return cursor;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder)
            throws FileNotFoundException {
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : DOCUMENT_COLUMNS);
        File directory = fileFor(parentDocumentId);
        File[] children = directory.listFiles();
        if (children == null) throw new FileNotFoundException(parentDocumentId);
        for (File child : children) includeDocument(cursor, parentDocumentId + "/" + child.getName());
        return cursor;
    }

    @Override public String createDocument(String parent, String mime, String name) throws FileNotFoundException {
        if (name.contains("/") || name.contains("\\") || name.equals("..")) throw new FileNotFoundException(name);
        String id = parent + "/" + name;
        File file = fileFor(id);
        try {
            if (DIRECTORY_MIME.equals(mime)) { if (!file.mkdir()) throw new java.io.IOException(); }
            else if (!file.createNewFile()) throw new java.io.IOException();
        } catch (java.io.IOException error) { throw new FileNotFoundException(name); }
        return id;
    }
    @Override public String renameDocument(String id, String name) throws FileNotFoundException {
        if (name.contains("/") || name.contains("\\") || name.equals("..")) throw new FileNotFoundException(name);
        String target = id.substring(0, id.lastIndexOf('/') + 1) + name;
        if (!fileFor(id).renameTo(fileFor(target))) throw new FileNotFoundException(name);
        return target;
    }
    @Override public void deleteDocument(String id) throws FileNotFoundException {
        if (!fileFor(id).delete()) throw new FileNotFoundException(id);
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        File file = fileFor(documentId);
        if (!file.isFile()) throw new FileNotFoundException(documentId);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public DocumentsContract.Path findDocumentPath(String parentDocumentId, String childDocumentId)
            throws FileNotFoundException {
        if (!isKnown(childDocumentId)) throw new FileNotFoundException(childDocumentId);
        if (ROOT.equals(childDocumentId)) {
            return new DocumentsContract.Path(ROOT, Collections.singletonList(ROOT));
        }
        if (DIRECTORY.equals(childDocumentId) || ROOT_FILE.equals(childDocumentId)) {
            return new DocumentsContract.Path(ROOT, Arrays.asList(ROOT, childDocumentId));
        }
        return new DocumentsContract.Path(ROOT, Arrays.asList(ROOT, DIRECTORY, GAME_FILE));
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        return !parentDocumentId.equals(documentId)
                && documentId.startsWith(parentDocumentId + "/") && isKnown(documentId);
    }

    private void includeDocument(MatrixCursor cursor, String documentId) throws FileNotFoundException {
        if (!isKnown(documentId)) throw new FileNotFoundException(documentId);
        File file = fileFor(documentId);
        MatrixCursor.RowBuilder row = cursor.newRow();
        add(row, cursor, DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId);
        add(row, cursor, DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName(documentId));
        add(row, cursor, DocumentsContract.Document.COLUMN_MIME_TYPE,
                file.isDirectory() ? DIRECTORY_MIME : "application/octet-stream");
        add(row, cursor, DocumentsContract.Document.COLUMN_FLAGS,
                DocumentsContract.Document.FLAG_SUPPORTS_DELETE | DocumentsContract.Document.FLAG_SUPPORTS_RENAME | (file.isDirectory() ? DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE : DocumentsContract.Document.FLAG_SUPPORTS_WRITE));
        add(row, cursor, DocumentsContract.Document.COLUMN_SIZE, file.length());
        add(row, cursor, DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified());
    }

    private File fileFor(String documentId) {
        if (ROOT.equals(documentId)) return fixtureRoot;
        if (!documentId.startsWith(ROOT + "/") || documentId.contains("..")) throw new IllegalArgumentException("Invalid document");
        return new File(fixtureRoot, documentId.substring((ROOT + "/").length()));
    }

    private boolean isKnown(String documentId) { return fileFor(documentId).exists(); }

    private static String displayName(String documentId) {
        int slash = documentId.lastIndexOf('/');
        return slash < 0 ? "GBCast test storage" : documentId.substring(slash + 1);
    }

    private static void writeFixture(File file, String contents) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(contents.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void add(MatrixCursor.RowBuilder row, MatrixCursor cursor, String column, Object value) {
        if (cursor.getColumnIndex(column) >= 0) row.add(column, value);
    }

    private android.content.Context requireProviderContext() {
        android.content.Context context = getContext();
        if (context == null) throw new IllegalStateException("Provider context unavailable");
        return context;
    }
}

