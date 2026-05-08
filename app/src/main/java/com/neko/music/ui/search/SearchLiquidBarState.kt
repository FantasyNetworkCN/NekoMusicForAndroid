package com.neko.music.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 与 [com.neko.music.ui.screens.SearchResultScreen] 共享，供 layerBackdrop 外的顶栏真液态采样。
 */
class SearchLiquidBarState {
    var searchQuery: String by mutableStateOf("")
    var searchType: String by mutableStateOf("music")
    var onImeSearchAction: () -> Unit by mutableStateOf({})
}
