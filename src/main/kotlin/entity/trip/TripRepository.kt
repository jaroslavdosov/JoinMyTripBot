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
    fun deleteByUserId(userId: Long)

    @Query("""
        SELECT t FROM Trip t 
        WHERE t.user.id != :userId 
        AND (t.city.id = :cityId OR t.country.id = :countryId)
        AND t.travelStart <= :end 
        AND t.travelEnd >= :start
    """)
    fun findMatches(
        @Param("cityId") cityId: Long?,
        @Param("countryId") countryId: Long?,
        @Param("userId") userId: Long,
        @Param("start") start: LocalDate,
        @Param("end") end: LocalDate
    ): List<Trip>
}