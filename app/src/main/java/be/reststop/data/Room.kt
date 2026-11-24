package be.reststop.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

//gwn alle lokale dingen en room db

//favo winkel entity
@Entity
data class FavoriteStore(@PrimaryKey val storeId: String, val name: String, val address: String, val addedAt: Long)

//reservaties lokaal bijhouden, geen wifi nodig , entity
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
//address ook lokaal behouden,
// om al opgezochte adressen gwn lokaal bij te houden zo,
// moet die niet elke keer online checken entity,
@Entity
data class CachedAddress(
    @PrimaryKey val key: String,
    val displayName: String,
    val city: String,
    val postcode: String = "",
    val fetchedAt: Long
)

@Dao
//gegevsn van favo winke aanpassen
interface FavoriteDao {
    //favo ophalen
    @Query("SELECT * FROM FavoriteStore ORDER BY addedAt DESC")
    fun all(): Flow<List<FavoriteStore>>
//favo toevoegen/vervangen
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(favorite: FavoriteStore)
//verwijderne van favo
    @Query("DELETE FROM FavoriteStore WHERE storeId = :storeId")
    suspend fun delete(storeId: String)
}

@Dao
abstract class ReservationDao {
    //reservaties ophalen
    @Query("SELECT * FROM CachedReservation ORDER BY createdAt DESC")
    abstract fun all(): Flow<List<CachedReservation>>
//reservatie toevoegen/vervangen
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(list: List<CachedReservation>)
//verwijderen
    @Query("DELETE FROM CachedReservation")
    abstract suspend fun clear()

    // Spiegelt de volledige Firestore-lijst naar Room.
    //volledige lijst vervangen,
    // om de room reservaties te synchroniserne met de full lijst van firestore
    // , bvb wnr de wifi weer aan is om lokaal t eupdaten
    @Transaction
    open suspend fun replaceAll(list: List<CachedReservation>) {
        clear()
        upsertAll(list)
    }
}

@Dao
interface AddressDao {
    //get
    @Query("SELECT * FROM CachedAddress WHERE `key` = :key")
    suspend fun get(key: String): CachedAddress?

    //add/replace
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(address: CachedAddress)

    //geen verwijdereing want adress bestaan altijd
}

//db
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
//db ophalen/aanmaken
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "reststop.db")
                .build().also { instance = it }
        }
    }
}
