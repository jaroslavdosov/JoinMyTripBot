package org.example

import org.example.bot.TelegramBot
import org.example.entity.trip.TripRepository
import org.example.entity.user.UserRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.send.SendMessage

@Service
class NotificationService(
    private val tripRepository: TripRepository,
    @org.springframework.context.annotation.Lazy
    private val bot: TelegramBot
) {

    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 30000) // Раз в час. Для теста поставь 30000 (30 сек)
    fun processNotifications() {
        log.info("Запуск проверки уведомлений...")

    }
}