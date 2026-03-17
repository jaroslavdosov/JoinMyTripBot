package org.example.entity.trip

import jakarta.transaction.Transactional
import org.example.entity.user.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface TripRepository : JpaRepository<Trip, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("DELETE FROM Trip t WHERE t.user.id = :userId")
    fun deleteByUserId(@Param("userId") userId: Long) // Проверь, чтобы тут было именно :userId и @Param("userId")
    fun findAllByUserId(userId: Long): List<Trip>

    @Query("""
        SELECT DISTINCT t.user FROM Trip t 
        WHERE (
            (:cityId IS NOT NULL AND t.city.id = :cityId) 
            OR 
            (:countryId IS NOT NULL AND (t.country.id = :countryId OR t.city.country.id = :countryId))
        )
        AND t.user.id != :currentUserId
        AND t.user.isActive = true
        AND (:gender = 'ALL' OR t.user.gender = :gender)
        AND t.user.age BETWEEN :minAge AND :maxAge
        AND t.travelStart <= :searchEnd 
        AND t.travelEnd >= :searchStart
    """)
    fun findMatches(
        @Param("cityId") cityId: Long?,
        @Param("countryId") countryId: Long?,
        @Param("currentUserId") currentUserId: Long,
        @Param("gender") gender: String,
        @Param("minAge") minAge: Int,
        @Param("maxAge") maxAge: Int,
        @Param("searchStart") searchStart: java.time.LocalDate,
        @Param("searchEnd") searchEnd: java.time.LocalDate
    ): List<User>

    @Query("SELECT MAX(t.id) FROM Trip t")
    fun findMaxId(): Long?

    // Также убедись, что у тебя есть этот метод для вывода списка
    fun findByUser(user: User): List<Trip>

    // Поиск совпадений для конкретной поездки
    @Query("""
            SELECT t FROM Trip t 
            WHERE t.user.id != :currentUserId
            AND t.id > :lastSeenId
            AND (
                (:cityId IS NOT NULL AND t.city.id = :cityId) 
                OR 
                (:countryId IS NOT NULL AND (t.country.id = :countryId OR t.city.country.id = :countryId))
            )
            AND t.user.isActive = true
            AND (:gender = 'ALL' OR t.user.gender = :gender)
            AND t.user.age BETWEEN :minAge AND :maxAge
            AND t.travelStart <= :searchEnd 
            AND t.travelEnd >= :searchStart
        """)
    fun findNewMatches(
        @Param("cityId") cityId: Long?,
        @Param("countryId") countryId: Long?,
        @Param("currentUserId") currentUserId: Long,
        @Param("gender") gender: String,
        @Param("minAge") minAge: Int,
        @Param("maxAge") maxAge: Int,
        @Param("searchStart") searchStart: LocalDate,
        @Param("searchEnd") searchEnd: LocalDate,
        @Param("lastSeenId") lastSeenId: Long
    ): List<Trip>
}