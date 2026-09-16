package com.example.composeup.datastore.data.repository

import android.content.Context
import com.example.composeup.datastore.data.local.room.AppDatabase
import com.example.composeup.datastore.data.model.ProductEntity
import kotlinx.coroutines.flow.Flow

class RoomRepository(context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val productDao = database.productDao()

//    val allProductsFlow: Flow<List<ProductEntity>> = productDao.getAllProductsFlow()

    fun getProductsByCategoryFlow(category: String): Flow<List<ProductEntity>> {
        return if (category.isEmpty()) {
            productDao.getAllProductsFlow()
        } else {
            productDao.getProductsByCategoryFlow(category)
        }
    }

    suspend fun addProduct(product: ProductEntity) {
        productDao.insertProduct(product)
    }

    suspend fun clearAll() {
        productDao.deleteAll()
    }
}
