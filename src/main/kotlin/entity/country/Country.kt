package org.example.entity.country

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "countries")
class Country(
    @Id val id: Long,
    val name: String,
    @Column(name = "iso2") val iso2: String, // Код страны (RU, US, FR)

    @Column(name = "code")
    val code: String? = null,

    val translations: String? // JSON с переводами
)