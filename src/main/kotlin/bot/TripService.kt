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

    // В TripService.kt

    fun parseStrictDates(text: String): Pair<java.time.LocalDate, java.time.LocalDate>? {
        val cleanText = text.replace(" ", "").replace("—", "-").trim()
        val regex = Regex("""^(\d{2}\.\d{2}\.\d{4})-(\d{2}\.\d{2}\.\d{4})$""")
        val match = regex.find(cleanText) ?: return null

        return try {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")
            val start = java.time.LocalDate.parse(match.groupValues[1], formatter)
            val end = java.time.LocalDate.parse(match.groupValues[2], formatter)

            val today = java.time.LocalDate.now()
            val maxFutureDate = today.plusYears(1) // Ограничение: 1 год вперед

            // 1. Базовая проверка порядка дат
            if (start.isBefore(today.minusDays(1)) || end.isBefore(start)) {
                return null
            }

            // 2. Проверка горизонта планирования (не далее 1 года вперед)
            if (start.isAfter(maxFutureDate)) {
                // Можно кидать кастомную ошибку или просто возвращать null
                return null
            }

            // 3. Проверка длительности периода (не более 90 дней)
            val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(start, end)
            if (daysBetween > 90) {
                return null
            }

            start to end
        } catch (e: Exception) {
            null
        }
    }
}