package com.github.kr328.clash

/** A recent same-code authority check can be shared with the Start action. */
internal fun canReuseAccessCheck(checkedCode: String, code: String, ageMillis: Long): Boolean =
    checkedCode == code && code.isNotBlank() && ageMillis in 0L..30_000L
