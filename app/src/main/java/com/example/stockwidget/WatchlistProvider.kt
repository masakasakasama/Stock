package com.example.stockwidget

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * Read-only access to the in-app watchlist for sibling apps signed with
 * the same certificate (the Home launcher). Protected by the
 * signature-level permission declared in the manifest.
 */
class WatchlistProvider : ContentProvider() {

    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(arrayOf("symbol"))
        val ctx = context ?: return cursor
        StockPrefs.loadAppSymbols(ctx).forEach { cursor.addRow(arrayOf(it)) }
        return cursor
    }

    override fun getType(uri: Uri) = "vnd.android.cursor.dir/vnd.com.example.stockwidget.symbol"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0
    override fun delete(uri: Uri, s: String?, a: Array<String>?) = 0
}
