package org.example.entity.trip

import jakarta.transaction.Transactional
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
        SELECT DISTINCT t FROM Trip t 
        JOIN FETCH t.user 
        WHERE t.user.id <> :myUserId 
          AND t.user.isActive = true 
          AND (
            (t.city.id = :myCityId) OR 
            (t.country.id = :myCountryId AND (t.isCountryWide = true OR :isMyTripCountryWide = true))
          )
          AND t.travelStart <= :myEnd 
          AND t.travelEnd >= :myStart
    """)
    fun findMatches(
        @Param("myUserId") myUserId: Long,
        @Param("myCityId") myCityId: Long?,
        @Param("myCountryId") myCountryId: Long?,
        @Param("isMyTripCountryWide") isMyTripCountryWide: Boolean,
        @Param("myStart") myStart: java.time.LocalDate,
        @Param("myEnd") myEnd: java.time.LocalDate
    ): List<Trip>
}