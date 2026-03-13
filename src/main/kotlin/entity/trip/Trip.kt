package org.example.entity.trip

import jakarta.persistence.*
import org.example.entity.city.City
import org.example.entity.country.Country
import org.example.entity.user.User
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "trips")
class Trip(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne @JoinColumn(name = "user_id")
    var user: User? = null,

    @ManyToOne @JoinColumn(name = "city_id")
    var city: City? = null,

    @ManyToOne @JoinColumn(name = "country_id")
    var country: Country? = null,

    var isCountryWide: Boolean = false, // true если едем в страну целиком

    var travelStart: LocalDate? = null,
    var travelEnd: LocalDate? = null,

    // Оставляем текстовое поле для быстрого отображения (опционально)
    var destinationName: String? = null,
    // Добавь эти поля, если их нет:
    var prefGender: String = "ALL",
    var prefAgeMin: Int = 18,
    var prefAgeMax: Int = 99,
    var notificationsEnabled: Boolean = false,
    var lastSeenTripId: Long? = null,

    val createdAt: LocalDateTime = LocalDateTime.now()

)