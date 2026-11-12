package com.example.orbitmusic.ui.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Invokes [onNearEnd] once each time the list scrolls within [threshold] items of its end. */
@Composable
fun LoadMoreEffect(listState: LazyListState, onNearEnd: () -> Unit, threshold: Int = 5) {
    val shouldLoad by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - threshold
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { shouldLoad }.distinctUntilChanged().filter { it }.collect { onNearEnd() }
    }
}
