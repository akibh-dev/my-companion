package com.akib.mycompanion.capture.data

import androidx.core.content.FileProvider

/**
 * Its own FileProvider class, so it can't clash in the merged manifest with another module's
 * `androidx.core.content.FileProvider`. Paths: `res/xml/capture_paths.xml`.
 */
class CaptureFileProvider : FileProvider()
