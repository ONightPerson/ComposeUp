package com.example.composeup.datastore.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.composeup.datastore.data.model.ProductEntity
import com.example.composeup.datastore.data.repository.RoomRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class RoomViewModel(private val repository: RoomRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val productList: StateFlow<List<ProductEntity>> = _searchQuery
        .flatMapLatest { query ->
            repository.getProductsByCategoryFlow(query)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addProduct(id: Int, name: String, price: Long, category: String) {
        viewModelScope.launch {
            repository.addProduct(ProductEntity(id, name, price, category))
        }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearAll() }
    }
}
