package com.example.jammate.utilities

object Constants {

    object App{
        const val PAGE_SIZE = 15
        const val MAX_DISTANCE_KM = 99999.0
    }
    object Activities {
        const val MAIN = "MAIN"
        const val CREATE_PROFILE = "CREATE_PROFILE"
    }

    object Extras {
        const val IS_EDIT_MODE = "IS_EDIT_MODE"
    }

    object PostTypes {
        const val JAM_SESSION = "jam"
        const val NORMAL_POST = "normal"
        const val BAND_MEMBER = "band_member"
    }

    object Lists {
        val instruments = listOf("Electric Guitar", "Drums", "Bass", "Vocals", "Piano", "Keys", "Saxophone", "Violin", "Trumpet", "Acoustic Guitar", "Cello")
        val genres = listOf("Rock", "Indie", "Jazz", "Blues", "Hip-Hop", "Pop", "Electronic", "Metal", "Folk", "R&B", "Funk")
    }

    object PostActions {
        const val LIKE = "LIKE"
        const val COMING = "COMING"
        const val APPLY = "apply"
    }

    // Unified tags for partial UI updates across adapters
    object Payloads {
        const val LIKE_UPDATE = "PAYLOAD_LIKE"
        const val ACTION_UPDATE = "PAYLOAD_ACTION"
        const val FOLLOW_UPDATE = "PAYLOAD_FOLLOW"
        const val COMMENT_UPDATE = "PAYLOAD_COMMENT"
    }
}