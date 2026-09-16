package com.example.composeup.datastore.data.local.room

import androidx.room.*
import com.example.composeup.datastore.data.model.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("SELECT * FROM product_table ORDER BY id DESC")
    fun getAllProductsFlow(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM product_table WHERE category = :cat ORDER BY id DESC")
    fun getProductsByCategoryFlow(cat: String): Flow<List<ProductEntity>>

    @Query("DELETE FROM product_table")
    suspend fun deleteAll()
}
