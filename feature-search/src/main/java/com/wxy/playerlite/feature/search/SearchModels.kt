package com.wxy.playerlite.feature.search

internal enum class SearchPageMode {
    HOT,
    SUGGEST,
    RESULT
}

enum class SearchResultType(
    val typeCode: Int,
    val payloadKeys: List<String>,
    val displayLabel: String
) {
    SONG(typeCode = 1, payloadKeys = listOf("songs"), displayLabel = "单曲"),
    ALBUM(typeCode = 10, payloadKeys = listOf("albums"), displayLabel = "专辑"),
    ARTIST(typeCode = 100, payloadKeys = listOf("artists"), displayLabel = "歌手"),
    PLAYLIST(typeCode = 1000, payloadKeys = listOf("playlists"), displayLabel = "歌单"),
    USER(typeCode = 1002, payloadKeys = listOf("userprofiles", "userProfiles"), displayLabel = "用户"),
    MV(typeCode = 1004, payloadKeys = listOf("mvs"), displayLabel = "MV"),
    LYRIC(typeCode = 1006, payloadKeys = listOf("songs", "lyrics"), displayLabel = "歌词"),
    RADIO(typeCode = 1009, payloadKeys = listOf("djRadios", "djradios"), displayLabel = "电台"),
    VIDEO(typeCode = 1014, payloadKeys = listOf("videos"), displayLabel = "视频"),
    COMPOSITE(typeCode = 1018, payloadKeys = emptyList(), displayLabel = "综合"),
    VOICE(typeCode = 2000, payloadKeys = listOf("resources", "voices", "resourceExtInfos"), displayLabel = "声音");
}

internal data class SearchUiState(
    val query: String = "",
    val pageMode: SearchPageMode = SearchPageMode.HOT,
    val historyKeywords: List<String> = emptyList(),
    val hotState: SearchHotUiState = SearchHotUiState.Loading,
    val suggestState: SearchSuggestUiState = SearchSuggestUiState.Idle,
    val resultStatesByType: Map<SearchResultType, SearchResultUiState> = emptyMap(),
    val lastSubmittedQuery: String = "",
    val selectedResultType: SearchResultType = SearchResultType.SONG,
    val availableResultTypes: List<SearchResultType> = SearchResultType.entries
) {
    val resultState: SearchResultUiState
        get() = resultStateFor(selectedResultType)

    fun resultStateFor(type: SearchResultType): SearchResultUiState {
        return resultStatesByType[type] ?: SearchResultUiState.Idle
    }
}

internal sealed interface SearchHotUiState {
    data object Loading : SearchHotUiState

    data class Content(
        val items: List<SearchHotKeywordUiModel>
    ) : SearchHotUiState

    data class Error(
        val message: String
    ) : SearchHotUiState
}

internal sealed interface SearchSuggestUiState {
    data object Idle : SearchSuggestUiState
    data object Loading : SearchSuggestUiState

    data class Content(
        val items: List<SearchSuggestionUiModel>
    ) : SearchSuggestUiState

    data class Error(
        val message: String
    ) : SearchSuggestUiState
}

internal sealed interface SearchResultUiState {
    data object Idle : SearchResultUiState
    data object Loading : SearchResultUiState
    data object Empty : SearchResultUiState

    data class Content(
        val items: List<SearchResultUiModel>
    ) : SearchResultUiState

    data class Error(
        val message: String
    ) : SearchResultUiState
}

data class SearchHotKeywordUiModel(
    val keyword: String,
    val score: Int = 0,
    val iconType: Int = 0,
    val iconUrl: String? = null,
    val content: String = ""
)

data class SearchSuggestionUiModel(
    val keyword: String
)

sealed interface SearchRouteTarget {
    data class Song(
        val songId: String
    ) : SearchRouteTarget

    data class Album(
        val albumId: String
    ) : SearchRouteTarget

    data class Artist(
        val artistId: String
    ) : SearchRouteTarget

    data class Playlist(
        val playlistId: String
    ) : SearchRouteTarget

    data class Generic(
        val resultType: SearchResultType,
        val targetId: String
    ) : SearchRouteTarget
}

sealed interface SearchResultUiModel {
    val id: String
    val resultType: SearchResultType
    val title: String
    val coverUrl: String?
    val routeTarget: SearchRouteTarget

    data class Song(
        override val id: String,
        override val title: String,
        val artistText: String,
        val albumTitle: String,
        override val coverUrl: String?,
        override val routeTarget: SearchRouteTarget
    ) : SearchResultUiModel {
        override val resultType: SearchResultType = SearchResultType.SONG
    }

    data class Album(
        override val id: String,
        override val title: String,
        val artistText: String,
        val songCount: Int?,
        override val coverUrl: String?,
        override val routeTarget: SearchRouteTarget
    ) : SearchResultUiModel {
        override val resultType: SearchResultType = SearchResultType.ALBUM
    }

    data class Artist(
        override val id: String,
        override val title: String,
        val albumCount: Int?,
        override val coverUrl: String?,
        override val routeTarget: SearchRouteTarget
    ) : SearchResultUiModel {
        override val resultType: SearchResultType = SearchResultType.ARTIST
    }

    data class Playlist(
        override val id: String,
        override val title: String,
        val creatorName: String,
        val trackCount: Int?,
        override val coverUrl: String?,
        override val routeTarget: SearchRouteTarget
    ) : SearchResultUiModel {
        override val resultType: SearchResultType = SearchResultType.PLAYLIST
    }

    data class Generic(
        override val id: String,
        override val resultType: SearchResultType,
        override val title: String,
        val subtitle: String,
        val tertiary: String = "",
        override val coverUrl: String?,
        override val routeTarget: SearchRouteTarget
    ) : SearchResultUiModel
}
