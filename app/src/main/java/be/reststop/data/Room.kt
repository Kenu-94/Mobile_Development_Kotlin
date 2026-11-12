package be.reststop.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity
data class FavoriteStore(@PrimaryKey val storeId: String, val name: String, val address: String, val addedAt: Long)

@Entity
data class CachedReservation(
    @PrimaryKey val id: String,
    val bagTitle: String,
    val storeName: String,
    val pickupCode: String,
    val pickupStart: Long,
    val pickupEnd: Long,
    val status: String,
    val quantity: Int = 1,
    val createdAt: Long = 0
)

@Entity
data class CachedAddress(
    @PrimaryKey val key: String,
    val displayName: String,
    val city: String,
    val postcode: String = "",
    val fetchedAt: Long
)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM FavoriteStore ORDER BY addedAt DESC")
    fun all(): Flow<List<FavoriteStore>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(favorite: FavoriteStore)

    @Query("DELETE FROM FavoriteStore WHERE storeId = :storeId")
    suspend fun delete(storeId: String)
}

@Dao
abstract class ReservationDao {
    @Query("SELECT * FROM CachedReservation ORDER BY createdAt DESC")
    abstract fun all(): Flow<List<CachedReservation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(list: List<CachedReservation>)

    @Query("DELETE FROM CachedReservation")
    abstract suspend fun clear()

    /** Spiegelt de volledige Firestore-lijst naar Room. */
    @Transaction
    open suspend fun replaceAll(list: List<CachedReservation>) {
        clear()
        upsertAll(list)
    }
}

@Dao
interface AddressDao {
    @Query("SELECT * FROM CachedAddress WHERE `key` = :key")
    suspend fun get(key: String): CachedAddress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(address: CachedAddress)
}

@Database(
    entities = [FavoriteStore::class, CachedReservation::class, CachedAddress::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun reservationDao(): ReservationDao
    abstract fun addressDao(): AddressDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "reststop.db")
                .build().also { instance = it }
        }
    }
}
