package org.example.entity.user

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
        userName: String?, // Сделайте его nullable, так как не у всех есть username
        age: Int,
        lastNotifiedAt: java.time.LocalDateTime,
        isActive: Boolean,
        state: String): User {

        val newUser = User(
            id = id,
            name = name,
            gender = gender,
            description = description,
            userName = userName, // Проверьте, что здесь не пустая строка ""
            age = age,
            lastNotifiedAt = lastNotifiedAt,
            isActive = isActive,
            state = state)
        return userRepository.save(newUser)
    }
}