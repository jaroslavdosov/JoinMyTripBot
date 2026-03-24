package org.example.bot

import org.example.entity.trip.Trip
import org.example.entity.trip.TripRepository
import org.example.entity.user.UserRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

@Service
class NotificationService(
    private val userRepository: UserRepository,
    private val tripRepository: TripRepository,
    private val tripService: TripService,
    private val bot: TelegramLongPollingBot // Бот должен быть бином
) {

    @Scheduled(fixedRate = 30000) // 1 минута
    fun scanAndNotify() {
        println("scanAndNotify!")
        val users = userRepository.findAll()
        val globalMaxId = tripRepository.findMaxId() ?: 0L

        users.forEach { user ->
            user.trips.filter { it.notificationsEnabled }.forEach { myTrip ->

                val effectiveCityId = if (myTrip.isCountryWide) null else myTrip.city?.id
                val effectiveCountryId = myTrip.country?.id ?: myTrip.city?.country?.id
                println("Проверка матчей для юзера ${user.id}: City=$effectiveCityId, Country=$effectiveCountryId, Start=${myTrip.travelStart}, End=${myTrip.travelEnd}")

                val matches = tripRepository.findNewMatches(
                    cityId = if (myTrip.isCountryWide) null else myTrip.city?.id,
                    countryId = myTrip.country?.id ?: myTrip.city?.country?.id,
                    currentUserId = user.id,
                    gender = myTrip.prefGender,
                    minAge = myTrip.prefAgeMin,
                    maxAge = myTrip.prefAgeMax,
                    searchStart = myTrip.travelStart!!,
                    searchEnd = myTrip.travelEnd!!,
                    lastSeenId = myTrip.lastSeenTripId ?: 0L
                )

                if (matches.isNotEmpty()) {
                    matches.forEach { companion ->
                        // Внутри цикла matches.forEach { ... }
                        try {
                            sendMatchNotification(user.id, companion, myTrip)
                        } catch (e: org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException) {
                            val errorMessage = e.apiResponse // Получаем текст ошибки от API

                            if (errorMessage.contains("forbidden", ignoreCase = true) ||
                                errorMessage.contains("chat not found", ignoreCase = true)) {

                                println("Пользователь ${user.id} заблокировал бота или чат не найден. Отключаем уведомления.")

                                // Вариант А: Отключить только уведомления для всех поездок пользователя
                                user.trips.forEach {
                                    it.notificationsEnabled = false
                                    tripRepository.save(it)
                                }

                                // Вариант Б: Пометить самого пользователя как неактивного (рекомендуется)
                                user.isActive = false

                                userRepository.save(user)

                            } else {
                                println("Произошла ошибка API при отправке юзеру ${user.id}: $errorMessage")
                            }
                        } catch (e: Exception) {
                            println("Непредвиденная ошибка при уведомлении юзера ${user.id}: ${e.message}")
                        }
                    }
                    // Обновляем "закладку", чтобы не слать одних и тех же
                    myTrip.lastSeenTripId = globalMaxId
                    tripRepository.save(myTrip)
                }
            }
        }
    }


    private fun sendMatchNotification(chatId: Long, match: Trip, myTrip: Trip) {
        val companion = match.user ?: return
        // ИСПРАВЛЕНО: MM вместо mm
        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")

        // Название ТВОЕГО места назначения
        val myDestination = myTrip.city?.let {
            tripService.getTranslatedName(it.translations, it.name, "ru")
        } ?: myTrip.country?.let {
            tripService.getTranslatedName(it.translations, it.name, "ru")
        } ?: "путешествие"

        // Название места ПОПУТЧИКА
        val companionDestination = match.city?.let {
            tripService.getTranslatedName(it.translations, it.name, "ru")
        } ?: match.country?.let {
            tripService.getTranslatedName(it.translations, it.name, "ru")
        } ?: ""

        // Формируем текст локации: "Россия (в г. Самара)" если они разные
        val destinationInfo = if (myDestination != companionDestination && companionDestination.isNotEmpty()) {
            "$myDestination (в г. $companionDestination)"
        } else {
            myDestination
        }

        val homeCityName = companion.homeCity?.let {
            tripService.getTranslatedName(it.translations, it.name, "ru")
        } ?: "Не указан"

        val messageText = """
        🔔 *Новое совпадение!*
        Для вашей поездки в **$destinationInfo** найден попутчик:
        
        👤 *${companion.name ?: "Без имени"}, ${companion.age ?: "?"} лет*
        🚻 Пол: ${if (companion.gender == "MALE") "Мужской" else "Женский"}
        🏠 Город проживания: $homeCityName
        ℹ️ О себе: ${companion.bio ?: "Не заполнено"}
        
       📅 Даты поездки: ${match.travelStart?.format(dateFormatter)} - ${match.travelEnd?.format(dateFormatter)}
    """.trimIndent()

        val keyboard = InlineKeyboardMarkup(listOf(
            listOf(InlineKeyboardButton("📝 Написать попутчику").apply {
                url = "https://t.me/${companion.userName}"
            })
        ))

        if (companion.photoFileId != null) {
            bot.execute(SendPhoto().apply {
                this.chatId = chatId.toString()
                this.photo = InputFile(companion.photoFileId)
                this.caption = messageText
                this.parseMode = "Markdown"
                this.replyMarkup = keyboard
            })
        } else {
            bot.execute(SendMessage().apply {
                this.chatId = chatId.toString()
                this.text = messageText
                this.parseMode = "Markdown"
                this.replyMarkup = keyboard
            })
        }
    }
}