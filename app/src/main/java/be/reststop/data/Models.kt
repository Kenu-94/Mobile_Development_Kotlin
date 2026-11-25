package be.reststop.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint
//models aanmaken
//alle omzetting van onze modellen voor room/firestore

//Firestore: stores/{storeId}. Het document-id is de uid van de handelaar.
data class Store(
    val ownerId: String = "",
    val name: String = "",
    val category: String = "other",
    val location: GeoPoint = GeoPoint(0.0, 0.0),
    val address: String = "",
    val postcode: String = "",
    val city: String = "",
    val email: String = "",
    val phone: String? = null
)

// Firestore: bags/{bagId}. storeName, category en location zijn bewust gekopieerd.
//dit is wat je kan bestellen
data class Bag(
    @DocumentId val id: String = "",
    val storeId: String = "",
    val storeName: String = "",
    val category: String = "other",
    val title: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val originalValue: Double = 0.0,
    val quantityTotal: Int = 0,
    val quantityLeft: Int = 0,
    val pickupStart: Timestamp = Timestamp.now(),
    val pickupEnd: Timestamp = Timestamp.now(),
    val location: GeoPoint = GeoPoint(0.0, 0.0),
    val active: Boolean = true,
    val photoBase64: String = ""
)

// Firestore: reservations/{id}. Titel/winkel/venster zijn gekopieerd voor het offline overzicht.
data class Reservation(
    @DocumentId val id: String = "",
    val bagId: String = "",
    val storeId: String = "",
    val customerId: String = "",
    val quantity: Int = 1,
    val pickupCode: String = "",
    val status: String = "reserved",
    val createdAt: Timestamp = Timestamp.now(),
    val bagTitle: String = "",
    val storeName: String = "",
    val pickupStart: Timestamp = Timestamp.now(),
    val pickupEnd: Timestamp = Timestamp.now()
)
//omzetten om ze te kunnen opslana in firestore of room, ze worden een map

fun Store.toMap(): Map<String, Any?> = mapOf(
    "ownerId" to ownerId, "name" to name, "category" to category, "location" to location,
    "address" to address, "postcode" to postcode, "city" to city,
    "email" to email, "phone" to phone
)

fun Bag.toMap(): Map<String, Any> = mapOf(
    "storeId" to storeId, "storeName" to storeName, "category" to category, "title" to title,
    "description" to description, "price" to price, "originalValue" to originalValue,
    "quantityTotal" to quantityTotal, "quantityLeft" to quantityLeft, "pickupStart" to pickupStart,
    "pickupEnd" to pickupEnd, "location" to location, "active" to active, "photoBase64" to photoBase64
)

//reservatie omzetten naar cached want zo een reservatie van firestore opgeslagen wordne bij room db, zodat het ook offline kan bekeken wordne die reservaties
fun Reservation.toCached() = CachedReservation(
    id = id, storeId = storeId, bagTitle = bagTitle, storeName = storeName, pickupCode = pickupCode,
    pickupStart = pickupStart.toDate().time, pickupEnd = pickupEnd.toDate().time,
    status = status, quantity = quantity, createdAt = createdAt.toDate().time
)

class SoldOutException : Exception()
