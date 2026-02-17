package org.example

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication


@SpringBootApplication
class BotApplication

fun main() {
    runApplication<BotApplication>()

    /*
    val bot = bot {

        token = System.getenv("BOT_TOKEN")
        println(token)



        dispatch {
            text {
                bot.sendMessage(
                    chatId = ChatId.fromId(message.chat.id),
                    messageThreadId = message.messageThreadId,
                    text = text + " test 22",
                    protectContent = true,
                    disableNotification = false,
                )
            }
        }
    }

    bot.startPolling()


     */
}

