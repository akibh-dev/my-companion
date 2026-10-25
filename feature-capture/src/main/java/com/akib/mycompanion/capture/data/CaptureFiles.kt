package com.akib.mycompanion.capture.data

import com.akib.mycompanion.capture.domain.CaptureType
import java.io.File
import java.util.UUID
import javax.inject.Qualifier

/**
 * The private folder (`filesDir/captures/`) holding voice notes, photos and screenshots. Only the
 * app can read it; the camera app gets a one-off write grant through [CAPTURE_AUTHORITY_SUFFIX]'s
 * FileProvider for the photo it's taking.
 */
class CaptureFiles(val dir: File) {

    /** A new, empty file for a capture of [type] made at [now]. */
    fun newFile(type: CaptureType, now: Long): File {
        dir.mkdirs()
        val extension = type.extension ?: "bin"
        val random = UUID.randomUUID().toString().take(RANDOM_CHARS)
        return File(dir, "${type.name.lowercase()}-$now-$random.$extension")
    }

    /** The file called [name] in the folder (names never contain a path). */
    fun file(name: String): File = File(dir, File(name).name)

    private companion object {
        const val RANDOM_CHARS = 8
    }
}

/** The FileProvider authority is `<applicationId>` + this (see the module manifest). */
const val CAPTURE_AUTHORITY_SUFFIX = ".captures"

/** The cipher for capture text (`FieldCipher.keystore("captures")`). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CaptureCipher
