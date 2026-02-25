package org.example.db
import jakarta.persistence.*

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

    val isActive: Boolean = true
)