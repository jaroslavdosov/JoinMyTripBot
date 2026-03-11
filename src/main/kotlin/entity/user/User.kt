package org.example.entity.user
import jakarta.persistence.*
import org.example.entity.city.City
import org.example.entity.trip.Trip

@Entity
@Table(name = "users")
class User(
    @Id
    var id: Long,
    var name: String? = null,
    var gender: String = "MALE",
    var description: String? = null,
    var userName: String? = null,
    var age: Int? = null,




    @Column(name = "state")
    var state: String = "START",

    @Column(name = "last_notified_at")
    var lastNotifiedAt: java.time.LocalDateTime = java.time.LocalDateTime.now(),

    var isActive: Boolean = true,

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    var trips: MutableList<Trip> = mutableListOf(),

    var tempDestination: String? = null,

    @Column(name = "temp_city_id")
    var tempCityId: Long? = null,

    @Column(name = "temp_country_id")
    var tempCountryId: Long? = null,

    var languageCode: String = "ru",

    var bio: String? = null,
    var photoFileId: String? = null,

    @ManyToOne
    @JoinColumn(name = "home_city_id")
    var homeCity: City? = null
)