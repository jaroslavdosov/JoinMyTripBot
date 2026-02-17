package org.example.db

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(private val userRepository: UserRepository) {

    @Transactional
    fun createNewUser(userName: String, userSex: String, userAge: Int): User {
        val newUser = User(name = userName, sex = userSex, age = userAge)
        return userRepository.save(newUser)
    }
}