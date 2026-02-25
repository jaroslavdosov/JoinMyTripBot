package org.example.entity.trip

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface TripRepository : JpaRepository<Trip, Long> {

    @Query("""
        SELECT t FROM Trip t 
        WHERE t.destination = :dest 
        AND t.user.id != :userId 
        AND t.travelStart <= :endDate 
        AND t.travelEnd >= :startDate
    """)
    fun findMatches(
        dest: String,
        userId: Long,
        startDate: java.time.LocalDate,
        endDate: java.time.LocalDate
    ): List<Trip>
}