package org.example.entity.city

import jakarta.persistence.*
import org.example.entity.country.Country

@Entity
@Table(name = "cities")
class City(
    @Id
    val id: Long,
    val name: String,

    @Column(name = "country_code") // Проверь, как колонка называется в базе!
    val countryCode: String? = null,

    val translations: String? = null,

    @Column(name = "population") // Проверь, как колонка называется в базе!
    val population: Long? = null,

    @ManyToOne
    @JoinColumn(name = "country_id")
    val country: Country? = null
)