package org.example.entity.city

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.data.domain.Pageable

@Repository
interface CityRepository : JpaRepository<City, Long> {

    @Query("""
    SELECT c FROM City c 
    WHERE (c.name ILIKE CONCAT(:q, '%') 
    OR c.translations ILIKE CONCAT('%', :q, '%'))
    ORDER BY c.population DESC
""")
    fun searchCities(@Param("q") q: String): List<City>
}