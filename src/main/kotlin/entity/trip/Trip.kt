package org.example.entity.trip

import jakarta.persistence.*
import org.example.entity.user.User

@Entity
@Table(name = "trips")
class Trip(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,

    var destination: String,

    @Column(name = "travel_start")
    var travelStart: java.time.LocalDate,

    @Column(name = "travel_end")
    var travelEnd: java.time.LocalDate
)