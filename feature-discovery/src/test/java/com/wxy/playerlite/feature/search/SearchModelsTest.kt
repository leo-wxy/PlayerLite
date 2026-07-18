package com.wxy.playerlite.feature.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchModelsTest {
    @Test
    fun searchResultType_shouldCoverDocumentedTypeCodes() {
        assertEquals(
            listOf(1, 10, 100, 1000, 1002, 1004, 1006, 1009, 1014, 1018, 2000),
            SearchResultType.entries.map { it.typeCode }
        )
    }

    @Test
    fun searchUiState_shouldExposeAllDocumentedTypesByDefault() {
        assertEquals(
            SearchResultType.entries,
            SearchUiState().availableResultTypes
        )
    }
}
