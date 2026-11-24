package be.reststop.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
//bevat 2 repo , authrepo en datarepo
//authrepositories: regelt de gebruikers acc, registeren, inloggen, wachtwoord resseten e uitloggen
//datarepository: ophalen, opslaan en aanpaasen van winkels, pakketen en reservatie in firebase firestore




//auth is voor de acountbeheer, db is voor de gebruikersgegevns op te slaan
class AuthRepository(private val auth: FirebaseAuth, private val db: FirebaseFirestore) {
    //unieke id van de momentele ingelogd gebruiker, niemand ingelogd? =null
    //zo kan de app herkene wie nu actief is op de apparaat enz
    val uid: String? get() = auth.currentUser?.uid

    //account amjen, email, password en role nodig
    suspend fun register(name: String, email: String, password: String, role: String) {
     //neiuwe acc maken via firebase authentication met de email en wachtwpprd
        val result = auth.createUserWithEmailAndPassword(email, password).await()
      //unieke id van de neiuwe acc pakken, !! is voor tegen dat we zeker zijn dat het niet null is
        val id = result.user!!.uid
        //bij de firestore colelctie 'users' wordt er een document gemaakt met hetzelfde id als de gebruiker
        //deze document bevat de naam, email, en rol
        db.collection("users").document(id)
            .set(mapOf("name" to name, "email" to email, "role" to role)).await()
    }

    //inloggen
    suspend fun login(email: String, password: String) {
        //de gegevens contorlerne bij de firebase authenticie,
        //als het correct is wordt die gebruiker ingelogd
        auth.signInWithEmailAndPassword(email, password).await()
    }

    // Geeft "customer", "merchant" of null (niet ingelogd / geen profiel).
    suspend fun role(): String? {
        //de user is wordt gehaald, als niemand ingelogd is stopt het en geeft het ene null
        val id = uid ?: return null
        //die document wordt opgehaald van firestore, de role wordt uitgelezen
        return db.collection("users").document(id).get().await().getString("role")
    }
//een email wordt gestuurd naar de gebruiker email via firebase, daarin kan de gebruikt de ww resetten
    suspend fun resetPassword(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    //gwn uitloggen
    fun logout() = auth.signOut()
}

//repositoy dat toegang tot firestore heeft, beheert dus de winkels, pakketen , reservaties ezn
class DataRepository(private val db: FirebaseFirestore) {
    //de alphabet voor de ophaal codes, er is geen i 0 o 1 om verwarring te vermeidne
    private val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    // ---- Realtime lezen ----
    //real time gegevns ophalen
    //bekijkt hoeveel pakketen er aanwezig zijn, dit is voortdurend
    fun availableBags(): Flow<List<Bag>> =
        db.collection("bags").whereGreaterThan("quantityLeft", 0).snapshots().map { snap ->
            //zoekt bags op, gebruikt een filter van waar een pakket meer dan 0 heeft
            //we gebruiken snapshots om zo makkeljijk en snel alles geupadte te houden
            val now = System.currentTimeMillis()
            //de tijd nu in ms
            snap.toObjects(Bag::class.java).filter { it.active && it.pickupEnd.toDate().time > now }
               // firestore document wordt omgezet nar een bag en wordt gefilteerd op alleen pakketne die actief zijn en ophlaamomentn niet voorbij is
        }//zo krijg je alleen pakketne die beschikbaar zijn
    //specifieke pakket getting via id
    fun bag(id: String): Flow<Bag?> =
        db.collection("bags").document(id).snapshots().map { it.toObject(Bag::class.java) }

    //pakketen van een specifieke winkel ophalen
    fun bagsOfStore(storeId: String): Flow<List<Bag>> =
        db.collection("bags").whereEqualTo("storeId", storeId).snapshots()
            .map { it.toObjects(Bag::class.java) }

    //alle reservaties van een winkel ophalen
    fun reservationsOfStore(storeId: String): Flow<List<Reservation>> =
        db.collection("reservations").whereEqualTo("storeId", storeId).snapshots()
            .map { it.toObjects(Reservation::class.java) }

    //alle reservaties van een klant ophalen
    fun reservationsOfCustomer(uid: String): Flow<List<Reservation>> =
        db.collection("reservations").whereEqualTo("customerId", uid).snapshots()
            .map { it.toObjects(Reservation::class.java) }

    // ---- Winkel ----
    //winkel ophalen via id
    suspend fun store(id: String): Store? =
        db.collection("stores").document(id).get().await().toObject(Store::class.java)

    //winkel oplslaan
    suspend fun saveStore(store: Store) {
        db.collection("stores").document(store.ownerId).set(store.toMap()).await()
    }

    // ---- Pakketten ----
    //pakket toevoegen
    suspend fun addBag(bag: Bag) {
        db.collection("bags").add(bag.toMap()).await()
    }

    //pakket online/offline zetten
    suspend fun setBagActive(bagId: String, active: Boolean) {
        db.collection("bags").document(bagId).update("active", active).await()
    }

    // ---- Reservaties (transacties: aantal daalt correct, nooit onder 0) ----
    //hier wordt de reservatoe zelf gemaajt
    suspend fun reserve(bag: Bag, quantity: Int, customerId: String): String {
        val bagRef = db.collection("bags").document(bag.id)
        //de pakket dat gereserveerd wordt
        val resRef = db.collection("reservations").document()
        //niuewe doucment verwijzingen voor deze reservatie wordt gemaakt, firestore maakt ene id
        val code = (1..6).map { alphabet.random() }.joinToString("")
    //ophaal code
        db.runTransaction { tx ->
            //transactie van firestore begint
            val snap = tx.get(bagRef)
            //gegevns van de pakket opahlen binnen deze transactie
            val left = snap.getLong("quantityLeft") ?: 0L
            //checken hoevele van die pakket nog over is
            if (left < quantity || snap.getBoolean("active") == false) throw SoldOutException()
            tx.update(bagRef, "quantityLeft", left - quantity)
           //we passen de voorraad aan want we gaan reserveen
            tx.set(
                //reservatie document wordt gemaakt bij firestores
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

    //reservatie annuleren
    suspend fun cancel(reservationId: String) {
        //de reservatie dat geannuleerd wordt
        val resRef = db.collection("reservations").document(reservationId)
        db.runTransaction { tx ->
            //reservatie gegevns ophalen
            val r = tx.get(resRef)
            if (r.getString("status") != "reserved") throw IllegalStateException("not reserved")
         //id van pakket ophalen en verwijzen ernaar
            val bagRef = db.collection("bags").document(r.getString("bagId")!!)
           //veranderen de status en aantal
            tx.update(resRef, "status", "cancelled")
            tx.update(bagRef, "quantityLeft", FieldValue.increment(r.getLong("quantity") ?: 1L))
            Unit
        }.await()
    }

    //gwnr eservatie wordt opgehaald
    suspend fun markCollected(reservationId: String) {
        db.collection("reservations").document(reservationId).update("status", "collected").await()
    }
}
