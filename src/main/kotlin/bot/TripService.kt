package org.example.bot

import org.example.entity.city.CityRepository
import org.example.entity.country.CountryRepository
import org.example.entity.trip.TripRepository
import org.example.entity.user.User
import org.springframework.stereotype.Service

@Service
class TripService(
    private val tripRepository: TripRepository,
    private val cityRepository: CityRepository,
    private val countryRepository: CountryRepository
) {
    fun getTranslatedName(translations: String?, defaultName: String, langCode: String): String {
        if (translations == null) return defaultName
        val regex = "\"$langCode\":\\s*\"([^\"]+)\"".toRegex()
        return regex.find(translations)?.groups?.get(1)?.value ?: defaultName
    }

    fun detectLanguage(text: String): String =
        if (Regex("[а-яА-ЯёЁ]").containsMatchIn(text)) "ru" else "en"

    fun findMatchesForUser(user: User): List<User> {
        val matchedUsers = mutableSetOf<User>()
        user.trips.forEach { trip ->
            val found = tripRepository.findMatches(
                user.id!!, trip.city?.id, trip.country?.id,
                trip.isCountryWide, trip.travelStart!!, trip.travelEnd!!
            )
            found.forEach { it.user?.let { u -> matchedUsers.add(u) } }
        }
        return matchedUsers.toList()
    }
}