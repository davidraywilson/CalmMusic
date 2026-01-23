package com.calmapps.calmmusic.data

enum class StreamingProvider {
    APPLE_MUSIC,
    YOUTUBE,
    ;

    companion object {
        fun fromStored(value: String?): StreamingProvider = when (value) {
            "APPLE_MUSIC" -> APPLE_MUSIC
            else -> YOUTUBE
        }

        fun toStored(provider: StreamingProvider): String = when (provider) {
            APPLE_MUSIC -> "APPLE_MUSIC"
            YOUTUBE -> "YOUTUBE"
        }
    }
}