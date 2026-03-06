package com.rpeters.cinefintv.data.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.IOException

/**
 * Utility helpers for creating and finalising MediaStore entries without relying on
 * legacy external storage permissions.
 */
object MediaStoreSaver {

    /**
     * Create (or reserve) a MediaStore entry inside the public Downloads collection.
     *
     * @throws IOException if the MediaStore item could not be created.
     */
    @Throws(IOException::class)
    fun prepareDownload(
        context: Context,
        fileName: String,
        relativePath: String,
        mimeType: String,
        markPending: Boolean = false,
    ): Uri {
        ensureScopedStorageSupported()

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            if (markPending) {
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            throw UnsupportedOperationException("MediaStoreSaver requires scoped storage support")
        }

        return insert(context.contentResolver, collection, values)
    }

    /**
     * Mark a MediaStore entry as fully written, clearing the pending flag if it was set.
     */
    fun finalizePending(context: Context, uri: Uri) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.IS_PENDING, 0)
        }
        context.contentResolver.update(uri, values, null, null)
    }

    private fun ensureScopedStorageSupported() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw UnsupportedOperationException("MediaStoreSaver requires scoped storage support")
        }
    }

    private fun insert(resolver: ContentResolver, collection: Uri, values: ContentValues): Uri {
        return resolver.insert(collection, values)
            ?: throw IOException("Failed to create MediaStore entry")
    }
}
