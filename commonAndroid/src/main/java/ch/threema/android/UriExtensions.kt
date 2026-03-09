package ch.threema.android

import android.net.Uri

fun Uri.isFileUri() =
    scheme?.equals("file", ignoreCase = true) == true
