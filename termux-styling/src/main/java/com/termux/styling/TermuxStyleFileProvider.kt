package com.termux.styling

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

const val CONTENT_URI_PREFIX = "content://com.termux.styling.fileprovider"

// https://developer.android.com/guide/topics/providers/content-provider-creating#kotlin
val URI_MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
    /*
     * The calls to addURI() go here for all the content URI patterns that the provider
     * recognizes. For this snippet, only the calls for table 3 are shown.
     */

    /*
     * Sets the code for a single row to 2. In this case, the # wildcard is
     * used. content://com.example.app.provider/table3/3 matches, but
     * content://com.example.app.provider/table3 doesn't.
     */
    addURI("com.termux.styling.fileprovider", "files/#", 1)
}


/**
 * See https://developer.android.com/reference/androidx/core/content/FileProvider
 */
class TermuxStyleFileProvider: ContentProvider() {
    override fun onCreate(): Boolean {
        TODO("Not yet implemented")
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        Log.e("termux", "query: $uri");
        var localSortOrder: String = sortOrder ?: ""
        var localSelection: String = selection ?: ""
        when (URI_MATCHER.match(uri)) {
            1 -> {  // If the incoming URI was for a single row
                /*
                 * Because this URI was for a single row, the _ID value part is
                 * present. Get the last path segment from the URI; this is the _ID value.
                 * Then, append the value to the WHERE clause for the query.
                 */
                localSelection += "_ID ${uri?.lastPathSegment}"
                Log.e("termux", "path: ${uri.path}")
            }
            else -> { // If the URI isn't recognized,
                Log.e("termux", "Unhandled: type: " + URI_MATCHER.match(uri))
                // do some error handling here
            }
        }

        // Call the code to actually do the query
        return null
    }

    override fun getType(uri: Uri): String? {
        TODO("Not yet implemented")
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        TODO("Not yet implemented")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        TODO("Not yet implemented")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        TODO("Not yet implemented")
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        Log.e("termux", "openAssetFile: $uri")
        val am = context?.assets
        val fileName = uri.lastPathSegment
        if (fileName != null) {
            return context?.assets?.openFd(fileName)
        }
        return null
    }

}