package org.example.bot

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
                val matches = tripRepository.findNewMatches(
                    cityId = myTrip.city?.id,
                    countryId = myTrip.country?.id,
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
                        sendMatchNotification(user.id, companion)
                    }
                    // Обновляем "закладку", чтобы не слать одних и тех же
                    myTrip.lastSeenTripId = globalMaxId
                    tripRepository.save(myTrip)
                }
            }
        }
    }

    private fun sendMatchNotification(chatId: Long, match: org.example.entity.trip.Trip) {
        val companion = match.user!!
        val destination = match.city?.let { tripService.getTranslatedName(it.translations, it.name, "ru") }
            ?: match.country?.let { tripService.getTranslatedName(it.translations, it.name, "ru") }
            ?: "маршрут"

        val messageText = """
            🔔 *Новое совпадение!*
            Для вашей поездки в $destination найден попутчик:
            
            👤 *${companion.name ?: "Без имени"}, ${companion.age ?: "?"} лет*
            🚻 Пол: ${if (companion.gender == "MALE") "Мужской" else "Женский"}
            ℹ️ О себе: ${companion.bio ?: "Не заполнено"}
            
            📅 Даты попутчика: ${match.travelStart} — ${match.travelEnd}
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