package com.termux.styling

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri

const val CONTENT_URI_AUTHORITY = "com.termux.styling.fileprovider"
const val CONTENT_URI_PREFIX = "content://$CONTENT_URI_AUTHORITY"

/**
 * See https://developer.android.com/reference/androidx/core/content/FileProvider
 */
class TermuxStyleFileProvider: ContentProvider() {
    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return null
    }

    override fun getType(uri: Uri): String {
        return "application/octet-stream"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("No external inserts")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("No external deletes")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException("No external updates")
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val path = uri.path?.substring(1)
        if (path != null) {
            if (path.endsWith("/Default")) {
                return null
            }
            return context?.assets?.openFd(path)
        }
        return null
    }

}
