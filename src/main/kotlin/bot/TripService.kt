package org.example.bot

import org.example.entity.city.City
import org.example.entity.city.CityRepository
import org.example.entity.country.Country
import org.example.entity.country.CountryRepository
import org.example.entity.trip.Trip
import org.example.entity.trip.TripRepository
import org.example.entity.user.User
import org.springframework.stereotype.Service
import java.time.LocalDate

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

    private val dateRegex = Regex("""(\d{1,2})[./](\d{1,2})(?:[./](\d{2,4}))?""")

    fun parseDates(text: String): Pair<LocalDate, LocalDate>? {
        val matches = dateRegex.findAll(text).toList()
        if (matches.isEmpty()) return null

        fun parseMatch(m: MatchResult): LocalDate {
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val year = m.groupValues[3].let {
                if (it.isEmpty()) LocalDate.now().year
                else if (it.length == 2) 2000 + it.toInt()
                else it.toInt()
            }
            return LocalDate.of(year, month, day)
        }

        val start = parseMatch(matches[0])
        val end = if (matches.size > 1) parseMatch(matches[1]) else start
        return start to end
    }


    fun getFormattedDestination(trip: Trip, lang: String): String {
        return if (trip.isCountryWide) {
            val countryName = getTranslatedName(trip.country?.translations, trip.country?.name ?: "Unknown", lang)
            "$countryName (Вся страна)"
        } else {
            val cityName = getTranslatedName(trip.city?.translations, trip.city?.name ?: "Unknown", lang)
            val countryOfCity = trip.city?.country
            val countryName = getTranslatedName(countryOfCity?.translations, countryOfCity?.name ?: "", lang)

            if (countryName.isNotEmpty()) "$cityName ($countryName)" else cityName
        }
    }

    // Внутри TripService.kt

    fun getFormattedDestinationForSearch(city: City?, country: Country?, lang: String): String {
        return if (city == null && country != null) {
            // Если выбрана страна
            val name = getTranslatedName(country.translations, country.name, lang)
            "$name (Вся страна)"
        } else if (city != null) {
            // Если выбран город
            val cityName = getTranslatedName(city.translations, city.name, lang)
            val countryName = getTranslatedName(city.country?.translations, city.country?.name ?: "", lang)
            if (countryName.isNotEmpty()) "$cityName ($countryName)" else cityName
        } else "Неизвестно"
    }

    private val fullFormatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun parseStrictDates(text: String): Pair<java.time.LocalDate, java.time.LocalDate>? {
        val regex = Regex("""^(\d{2}\.\d{2}\.\d{4})-(\d{2}\.\d{2}\.\d{4})$""")
        val match = regex.find(text.replace(" ", "")) ?: return null

        return try {
            val start = java.time.LocalDate.parse(match.groupValues[1], fullFormatter)
            val end = java.time.LocalDate.parse(match.groupValues[2], fullFormatter)

            val today = java.time.LocalDate.now()
            if (start.isBefore(today) || end.isBefore(start)) return null

            start to end
        } catch (e: Exception) {
            null
        }
    }
}