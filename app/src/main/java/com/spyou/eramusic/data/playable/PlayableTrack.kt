package com.spyou.eramusic.data.playable

import android.net.Uri

interface PlayableTrack {
    val trackId: String
    val trackTitle: String
    val trackArtist: String
    val trackArtworkUri: Uri?
}
