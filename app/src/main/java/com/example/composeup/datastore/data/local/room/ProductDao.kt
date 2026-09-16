package com.example.composeup.datastore.data.local.room

import androidx.room.*
import com.example.composeup.datastore.data.model.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM product_table ORDER BY id DESC")
    fun getAllProductsFlow(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM product_table WHERE category = :cat ORDER BY id DESC")
    fun getProductsByCategoryFlow(cat: String): Flow<List<ProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Query("DELETE FROM product_table")
    suspend fun deleteAll()
}
