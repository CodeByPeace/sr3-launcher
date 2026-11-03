package com.sr3launcher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/** Отдаёт установщику скачанный APK обновления (один файл в кэше лаунчера). */
public final class ApkProvider extends ContentProvider {
    static File apk(android.content.Context c) { return new File(new File(c.getCacheDir(), "update"), "update.apk"); }

    static Uri uri(android.content.Context c) {
        return Uri.parse("content://" + c.getPackageName() + ".files/update.apk");
    }

    @Override public boolean onCreate() { return true; }

    @Override public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
        File f = apk(getContext());
        if (!f.exists()) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri u, String[] proj, String sel, String[] args, String order) {
        File f = apk(getContext());
        MatrixCursor c = new MatrixCursor(new String[] {"_display_name", "_size"});
        c.addRow(new Object[] {"update.apk", f.length()});
        return c;
    }

    @Override public String getType(Uri u) { return "application/vnd.android.package-archive"; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
