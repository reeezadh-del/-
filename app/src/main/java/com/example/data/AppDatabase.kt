package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.model.ProxyProfile
import com.example.model.ProxyProtocol

class ProtocolConverters {
    @TypeConverter
    fun fromProtocol(protocol: ProxyProtocol): String = protocol.name

    @TypeConverter
    fun toProtocol(value: String): ProxyProtocol =
        ProxyProtocol.entries.firstOrNull { it.name == value } ?: ProxyProtocol.VLESS
}

@Database(entities = [ProxyProfile::class], version = 2, exportSchema = false)
@TypeConverters(ProtocolConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun proxyProfileDao(): ProxyProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "separ_net_db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
