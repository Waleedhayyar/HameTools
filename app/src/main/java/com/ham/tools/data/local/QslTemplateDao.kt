package com.ham.tools.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ham.tools.data.model.QslTemplate
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for QSL template operations
 */
@Dao
interface QslTemplateDao {
    
    /**
     * Get all templates ordered by update time
     */
    @Query("SELECT * FROM qsl_templates ORDER BY updatedAt DESC")
    fun getAllTemplates(): Flow<List<QslTemplate>>
    
    /**
     * Get template by ID
     */
    @Query("SELECT * FROM qsl_templates WHERE id = :id")
    suspend fun getTemplateById(id: Long): QslTemplate?
    
    /**
     * Get the default template
     */
    @Query("SELECT * FROM qsl_templates WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultTemplate(): QslTemplate?
    
    /**
     * Insert a new template
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: QslTemplate): Long
    
    /**
     * Update an existing template
     */
    @Update
    suspend fun update(template: QslTemplate)
    
    /**
     * Delete a template
     */
    @Delete
    suspend fun delete(template: QslTemplate)
    
    /**
     * Clear default flag from all templates
     */
    @Query("UPDATE qsl_templates SET isDefault = 0")
    suspend fun clearDefaultFlag()
    
    /**
     * Set a template as default
     */
    @Query("UPDATE qsl_templates SET isDefault = 1 WHERE id = :id")
    suspend fun setAsDefault(id: Long)
}
