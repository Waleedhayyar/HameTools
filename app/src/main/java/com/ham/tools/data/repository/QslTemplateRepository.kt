package com.ham.tools.data.repository

import com.ham.tools.data.local.QslTemplateDao
import com.ham.tools.data.model.QslTemplate
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for QSL template operations
 */
@Singleton
class QslTemplateRepository @Inject constructor(
    private val dao: QslTemplateDao
) {
    fun getAllTemplates(): Flow<List<QslTemplate>> = dao.getAllTemplates()
    
    suspend fun getTemplateById(id: Long): QslTemplate? = dao.getTemplateById(id)
    
    suspend fun getDefaultTemplate(): QslTemplate? = dao.getDefaultTemplate()
    
    suspend fun insertTemplate(template: QslTemplate): Long = dao.insert(template)
    
    suspend fun updateTemplate(template: QslTemplate) = dao.update(template)
    
    suspend fun deleteTemplate(template: QslTemplate) = dao.delete(template)
    
    suspend fun setAsDefault(id: Long) {
        dao.clearDefaultFlag()
        dao.setAsDefault(id)
    }
}
