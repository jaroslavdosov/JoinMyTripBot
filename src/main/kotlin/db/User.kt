package org.example.db
import jakarta.persistence.*

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int? = null,
    val name: String,
    val gender: String,
    val description: String,
    val userName: String,
    val age: Int,

    @Column(name = "last_notified_at")
    var lastNotifiedAt: java.time.LocalDateTime = java.time.LocalDateTime.now(),

    val isActive: Boolean
)