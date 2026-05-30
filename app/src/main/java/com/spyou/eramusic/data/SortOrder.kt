package com.spyou.eramusic.data

/** How the song library is ordered. Persisted by ordinal in DataStore. */
enum class SortOrder(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    DATE_ADDED("Date added"),
    DURATION("Duration"),
}
