package com.fzfteam

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TelegramBotApplication

fun main(args: Array<String>) {
	runApplication<TelegramBotApplication>(*args)
}
