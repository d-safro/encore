package dev.safro.encore

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Lets Android Auto show the covers kept with the downloads (files/covers, see saved_covers.dart), which
 * it can't read from the app's own folder. Read-only, and only those pictures: names are their checksum.
 */
class CoverArtProvider : ContentProvider() {
    private val pictureName = Regex("[0-9a-f]{32}\\.jpg")

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Covers can only be read")
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        if (!pictureName.matches(name)) throw FileNotFoundException(name)
        val f = File(File(context!!.filesDir, "covers"), name)
        if (!f.isFile) throw FileNotFoundException(name)
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "image/jpeg"
    override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<String>?) = 0
    override fun delete(uri: Uri, s: String?, a: Array<String>?) = 0
}
