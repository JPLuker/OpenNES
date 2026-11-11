package com.swordfish.lemuroid.app.mobile.feature.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.swordfish.lemuroid.app.shared.romm.RommApiClient
import com.swordfish.lemuroid.app.shared.romm.RommLibraryCatalog
import com.swordfish.lemuroid.common.paging.buildFlowPaging
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    private val retrogradeDb: RetrogradeDatabase,
    private val context: Context,
) : ViewModel() {
    class Factory(
        val retrogradeDb: RetrogradeDatabase,
        val context: Context,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SearchViewModel(retrogradeDb, context.applicationContext) as T
        }
    }

    val indexedNesFileNames: Flow<Set<String>> =
        retrogradeDb.gameDao().observeNesFileNames().map { it.toSet() }

    fun refreshRomMLibrary() {
        viewModelScope.launch { RommLibraryCatalog.refresh(context) }
    }

    fun downloadFromRomM(rom: RommApiClient.Rom) {
        viewModelScope.launch { RommLibraryCatalog.download(context, rom) }
    }

    val queryString = MutableStateFlow("")

    enum class UIState { Idle, Loading, Ready }

    val searchResults =
        queryString
            .debounce(400.milliseconds)
            .flatMapLatest {
                buildFlowPaging(20, viewModelScope) {
                    retrogradeDb.gameSearchDao().search(it)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), PagingData.empty())

    val searchState: Flow<UIState> =
        queryString
            .flatMapLatest { searchStatesForQuery(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), UIState.Idle)

    private fun searchStatesForQuery(query: String): Flow<UIState> {
        if (query.isEmpty()) {
            return flowOf(UIState.Idle)
        }

        return flow {
            emit(UIState.Loading)
            delay(500)
            emit(UIState.Ready)
        }
    }
}
