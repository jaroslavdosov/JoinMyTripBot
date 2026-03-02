package org.example.bot

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

@Configuration
class BotConfig {
    @Bean
    fun telegramBotsApi(myBot: TelegramBot): TelegramBotsApi {
        val botsApi = TelegramBotsApi(DefaultBotSession::class.java)
        botsApi.registerBot(myBot)
        return botsApi
    }
}