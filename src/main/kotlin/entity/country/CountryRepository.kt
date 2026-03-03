package org.example.entity.country

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface CountryRepository : JpaRepository<Country, Long> {

    @Query("""
        SELECT c FROM Country c 
        WHERE c.name ILIKE CONCAT('%', :q, '%') 
        OR c.translations ILIKE CONCAT('%', :q, '%') 
        OR c.code ILIKE :q
    """)
    fun searchCountries(@Param("q") q: String): List<Country>

    // Этот метод ищет страны, в названии которых есть строка query (без учета регистра)
    fun findByNameContainingIgnoreCase(name: String): List<Country>
}