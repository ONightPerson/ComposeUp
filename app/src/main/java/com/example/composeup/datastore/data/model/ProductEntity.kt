package com.example.composeup.datastore.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 商品实体模型 (Room Database) */
@Entity(tableName = "product_table")
data class ProductEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "price") val price: Long,
    @ColumnInfo(name = "category") val category: String
)
