package com.calmapps.calmmusic.data

enum class StreamingProvider {
    APPLE_MUSIC,
    YOUTUBE,
    ;

    companion object {
        fun fromStored(value: String?): StreamingProvider = when (value) {
            "YOUTUBE" -> YOUTUBE
            else -> APPLE_MUSIC
        }

        fun toStored(provider: StreamingProvider): String = when (provider) {
            APPLE_MUSIC -> "APPLE_MUSIC"
            YOUTUBE -> "YOUTUBE"
        }
    }
}
