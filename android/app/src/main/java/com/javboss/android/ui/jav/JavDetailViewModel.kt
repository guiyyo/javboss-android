package com.javboss.android.ui.jav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javboss.android.core.net.ApiResult
import com.javboss.android.data.model.JavItem
import com.javboss.android.di.AppGraph
import kotlinx.coroutines.launch

class JavDetailViewModel : ViewModel() {

    var item by mutableStateOf<JavItem?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var loadedId: Long = 0

    fun load(id: Long, force: Boolean = false) {
        if (!force && loadedId == id && item != null) return
        loadedId = id
        loading = true
        error = null
        viewModelScope.launch {
            when (val result = AppGraph.jav.detail(id)) {
                is ApiResult.Success -> item = result.data
                is ApiResult.Failure -> error = result.message
            }
            loading = false
        }
    }

    fun rate(rating: Double) {
        val current = item ?: return
        viewModelScope.launch {
            when (val result = AppGraph.jav.rate(current.id, rating)) {
                is ApiResult.Success -> item = result.data
                is ApiResult.Failure -> error = result.message
            }
        }
    }
}
