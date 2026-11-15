package dev.safro.encore

import androidx.core.content.FileProvider

/** Hands a downloaded update to Android's package installer (its own class, so it can't clash with plugins'). */
class UpdateFileProvider : FileProvider()
