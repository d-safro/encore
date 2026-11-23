package dev.safro.encore.wear.playback

/** Where a skip of [byMs] from [positionMs] lands, as on the phone: never before the start; past the end the
 *  next song (null), or the very end of the last one. A length not known ([durationMs] 0): as far as asked. */
fun skipTarget(positionMs: Long, byMs: Long, durationMs: Long, hasNext: Boolean): Long? {
    val to = positionMs + byMs
    if (to <= 0) return 0
    if (durationMs <= 0 || to < durationMs) return to
    return if (hasNext) null else durationMs
}
