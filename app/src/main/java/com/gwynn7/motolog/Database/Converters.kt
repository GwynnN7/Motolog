package com.gwynn7.motolog.Database

import android.net.Uri
import androidx.room.TypeConverter
import androidx.core.net.toUri

class Converters {
    @TypeConverter
    fun getStringFromURI(uri: Uri?): String? {
        if (uri == null) return null
        return uri.toString()
    }

    @TypeConverter
    fun getURIFromString(string: String?): Uri? {
        if (string == null) return null
        return string.toUri()
    }
}
