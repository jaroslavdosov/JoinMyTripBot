package org.example.db

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(private val userRepository: UserRepository) {

    @Transactional
    fun createNewUser(
        id: Long,
        name: String,
        gender: String,
        description: String,
        userName: String,
        age: Int,
        lastNotifiedAt: java.time.LocalDateTime,
        isActive: Boolean,
        state: String): User {

        val newUser = User(
            name = name,
            gender = gender,
            description = description,
            userName = userName,
            age = age,
            lastNotifiedAt = lastNotifiedAt,
            isActive = isActive,
            state = state,
            id = id)
        return userRepository.save(newUser)
    }
}