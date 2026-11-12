package be.reststop.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AuthRepository(private val auth: FirebaseAuth, private val db: FirebaseFirestore) {
    val uid: String? get() = auth.currentUser?.uid

    suspend fun register(name: String, email: String, password: String, role: String) {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val id = result.user!!.uid
        db.collection("users").document(id)
            .set(mapOf("name" to name, "email" to email, "role" to role)).await()
    }

    suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    /** Geeft "customer", "merchant" of null (niet ingelogd / geen profiel). */
    suspend fun role(): String? {
        val id = uid ?: return null
        return db.collection("users").document(id).get().await().getString("role")
    }

    suspend fun resetPassword(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    fun logout() = auth.signOut()
}

class DataRepository(private val db: FirebaseFirestore) {
    private val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    // ---- Realtime lezen ----
    fun availableBags(): Flow<List<Bag>> =
        db.collection("bags").whereGreaterThan("quantityLeft", 0).snapshots().map { snap ->
            val now = System.currentTimeMillis()
            snap.toObjects(Bag::class.java).filter { it.active && it.pickupEnd.toDate().time > now }
        }

    fun bag(id: String): Flow<Bag?> =
        db.collection("bags").document(id).snapshots().map { it.toObject(Bag::class.java) }

    fun bagsOfStore(storeId: String): Flow<List<Bag>> =
        db.collection("bags").whereEqualTo("storeId", storeId).snapshots()
            .map { it.toObjects(Bag::class.java) }

    fun reservationsOfStore(storeId: String): Flow<List<Reservation>> =
        db.collection("reservations").whereEqualTo("storeId", storeId).snapshots()
            .map { it.toObjects(Reservation::class.java) }

    fun reservationsOfCustomer(uid: String): Flow<List<Reservation>> =
        db.collection("reservations").whereEqualTo("customerId", uid).snapshots()
            .map { it.toObjects(Reservation::class.java) }

    // ---- Winkel ----
    suspend fun store(id: String): Store? =
        db.collection("stores").document(id).get().await().toObject(Store::class.java)

    suspend fun saveStore(store: Store) {
        db.collection("stores").document(store.ownerId).set(store.toMap()).await()
    }

    // ---- Pakketten ----
    suspend fun addBag(bag: Bag) {
        db.collection("bags").add(bag.toMap()).await()
    }

    suspend fun setBagActive(bagId: String, active: Boolean) {
        db.collection("bags").document(bagId).update("active", active).await()
    }

    // ---- Reservaties (transacties: aantal daalt correct, nooit onder 0) ----
    suspend fun reserve(bag: Bag, quantity: Int, customerId: String): String {
        val bagRef = db.collection("bags").document(bag.id)
        val resRef = db.collection("reservations").document()
        val code = (1..6).map { alphabet.random() }.joinToString("")
        db.runTransaction { tx ->
            val snap = tx.get(bagRef)
            val left = snap.getLong("quantityLeft") ?: 0L
            if (left < quantity || snap.getBoolean("active") == false) throw SoldOutException()
            tx.update(bagRef, "quantityLeft", left - quantity)
            tx.set(
                resRef, mapOf(
                    "bagId" to bag.id, "storeId" to bag.storeId, "customerId" to customerId,
                    "quantity" to quantity, "pickupCode" to code, "status" to "reserved",
                    "createdAt" to Timestamp.now(), "bagTitle" to bag.title, "storeName" to bag.storeName,
                    "pickupStart" to bag.pickupStart, "pickupEnd" to bag.pickupEnd
                )
            )
            Unit
        }.await()
        return code
    }

    suspend fun cancel(reservationId: String) {
        val resRef = db.collection("reservations").document(reservationId)
        db.runTransaction { tx ->
            val r = tx.get(resRef)
            if (r.getString("status") != "reserved") throw IllegalStateException("not reserved")
            val bagRef = db.collection("bags").document(r.getString("bagId")!!)
            tx.update(resRef, "status", "cancelled")
            tx.update(bagRef, "quantityLeft", FieldValue.increment(r.getLong("quantity") ?: 1L))
            Unit
        }.await()
    }

    suspend fun markCollected(reservationId: String) {
        db.collection("reservations").document(reservationId).update("status", "collected").await()
    }
}
