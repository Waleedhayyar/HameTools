package com.ham.tools.data.local

import androidx.room.TypeConverter
import com.ham.tools.data.model.Mode
import com.ham.tools.data.model.PropagationMode
import com.ham.tools.data.model.QslStatus

/**
 * Type converters for Room database
 * 
 * Converts enum types to/from strings for database storage
 */
class Converters {
    
    @TypeConverter
    fun fromMode(mode: Mode): String {
        return mode.name
    }
    
    @TypeConverter
    fun toMode(value: String): Mode {
        return Mode.valueOf(value)
    }
    
    @TypeConverter
    fun fromQslStatus(status: QslStatus): String {
        return status.name
    }
    
    @TypeConverter
    fun toQslStatus(value: String): QslStatus {
        return try {
            QslStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            // Handle legacy values during migration
            QslStatus.NOT_SENT
        }
    }
    
    @TypeConverter
    fun fromPropagationMode(mode: PropagationMode): String {
        return mode.name
    }
    
    @TypeConverter
    fun toPropagationMode(value: String): PropagationMode {
        return try {
            PropagationMode.valueOf(value)
        } catch (e: IllegalArgumentException) {
            PropagationMode.UNKNOWN
        }
    }
}
