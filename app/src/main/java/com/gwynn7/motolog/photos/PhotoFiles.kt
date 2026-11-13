package com.gwynn7.motolog.photos

import java.io.File

/** Database paths from backups are untrusted. Resolve only direct children of our own folder. */
internal object PhotoFiles {
    fun resolve(directory: File, path: String): File? {
        val source = File(path)
        if (source.parentFile?.name != directory.name || source.name in listOf(".", "..")) return null
        val candidate = File(directory, source.name)
        return candidate.takeIf { it.canonicalFile.parentFile == directory.canonicalFile }
    }

    fun dimensions(width: Int, height: Int, limit: Int): Pair<Int, Int> {
        require(width > 0 && height > 0 && limit > 0)
        val scale = minOf(1.0, limit.toDouble() / maxOf(width, height))
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }
}
