package com.fzfteam

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow


data class User(
    val userId: Long,
    var userHistory: HashMap<Long, String>,
    var userLocation: String = "",
    var userMessagesId: MutableList<Long>,
    var role: UserRoles = UserRoles.GUEST,
    var propblemsList: MutableList<String> = mutableListOf(),
    var lastProblem: String = ""
) {
}

data class Specialist(
    val id: Long,
    val name: String,
    val location: String,
    val categories: List<String>,
    val additionalData: Any
)

@Service
class SpecialistFinderBot: TelegramLongPollingBot() {

    @Value("\${telegram.botName}")
    private val botName : String = ""

    @Value("\${telegram.token}")
    private val token : String = ""

    override fun getBotToken(): String = token

    override fun getBotUsername(): String = botName

    private var counter: Int = 0

    private val specialists : List<Specialist> = initiateMockSpecialistList()

    private val availableLocations : List<String> = specialists.map { it.location }

    private val availableCategories : Set<String> = initiateMockUniqueSpecialistsCategories()

    private val specialyToCategory : HashMap<String, List<String>> = initiateMockSpecialityToCategoryMap()

    private fun initiateMockSpecialityToCategoryMap(): HashMap<String, List<String>> {
        hashMapOf(
            Pair("Юридические услуги", listOf("Нотариус", "Representation in court", "Other")),
            Pair("Housing", listOf("rent", "Purchase and sale", "Other")),
            Pair("health", listOf("Therapist", "Dentist", "Children's doctor")),
            Pair("Services for animals", listOf("Veterinarian", "Animal walking", "Overexposure")),
            Pair("legal services", listOf("Notary", "Representation in court", "Other")),
        )
    }

    private var users : HashMap<Long, User> = hashMapOf()
        get() {
            return field
        }
        set(value) {}

    fun initiateMockUniqueSpecialistsCategories() : Set<String> {
        val categories : Set<String> = specialists.map { it.categories }.flatMap { it }.toSet()

        return categories
    }

    fun initiateMockSpecialistList(): List<Specialist> {
        return listOf<Specialist>(
            Specialist(1, "Michel", "Astana", listOf("Car", "Driver", "Transfer"), "vk.com/michaelDriver"),
            Specialist(1, "Sholpan", "Astana", listOf("HR", "Work", "Hiring", "Hiring Agency"), "+7 700 10 10 10"),
            Specialist(1, "Asiya", "Shimkent", listOf("Nurse", "Nanny"), "+7 700 05 04 51, asyiya@shimkent.com"),
            Specialist(1, "George", "Astana", listOf("Car", "Driver", "Transfer"), "+7 700 00 00 00"),
            Specialist(3, "Maria Fouler", "Bishkek", listOf("Psychological help", "Mind care", "Meditation"), "@mashaF"),
            Specialist(3, "Anastasiya", "Bishkek", listOf("Job search", "Acting skills", "Career advice"), "@uCarrierInFirst"),
            Specialist(3, "Sasha E.", "Bishkek", listOf("Tutor", "English language"), "@englishWithSashaE"),
            Specialist(2, "Alima", "Erevan", listOf("Documents", "Government help", "Notary", "Lawyer"), "@alima"),
            Specialist(2, "Alik Gohaev", "Erevan", listOf("Documents", "Notary", "Lawyer"), "yourErevanLayer.com"),
        )
    }

//start
//q: you search or give help?
//a1: search
//a2: give
//a1q: which problem?
//a2q: what problems you might solve?


    data class AnswerObject (val text: String, val callback: String)

    data class ResponseObject (val responseText: String, val answerVariance: List<AnswerObject>, val nextQuestion: String = "") {
        constructor(responseText: String, nexQuestion: String) : this(responseText, listOf(AnswerObject("Restart", "/start")), nexQuestion)
    }

    override fun onUpdateReceived(update: Update) {
        println(availableLocations.toSet())
        if (update.hasMessage()) {
            val message = update.message
            var user = users.getOrPut(message.from.id){ User(
                userId = message.from.id,
                userHistory = hashMapOf(),
                userMessagesId = mutableListOf()
            )}

            val chatId = message.chatId.toString()
            val responseText = if (message.hasText()) {
                val messageText = message.text
                user.userMessagesId.add(message.messageId.toLong())
                user.userHistory[message.messageId.toLong()] = messageText
                when {
                    messageText == "/start" -> {
                        user.setup()
                        ResponseObject(
                            "You chose $messageText",
                            listOf(AnswerObject("I can help", "/give"), AnswerObject("I need Help", "/search")),
                            "You search or give help?"
                        )
                    }

//                    SETTING LOCATION IF NOT GUEST
                    !messageText.contains("/") &&
                    user.userLocation.isEmpty() -> {
                        user.userLocation = messageText
                        var nexQuestion = "Which problem you want find solve?"
                        val categoriesButtons = availableCategories.map { AnswerObject(it, it) }

                        if (user.role == UserRoles.SPECIALIST) nexQuestion = "Which problem you can solve?"
                        if (user.role == UserRoles.GUEST) {
                            ResponseObject(
                                "You say: '$messageText'",
                                listOf(AnswerObject("I can help", "/give"), AnswerObject("I need Help", "/search")),
                                ""
                            )
                        } else {
                            ResponseObject(
                                "You say: '$messageText'",
                                categoriesButtons,
                                nexQuestion)
                        }

                    }

                    !messageText.contains("/") &&
                            !user.userLocation.isEmpty()  -> {
                        val problem = messageText
                        user.lastProblem = problem
                        user.propblemsList.add(problem)
                        var textPrefixDependOnRole = "Your problem is"
                        var callbackDependsOnRole = "/find"
                        if (user.role === UserRoles.SPECIALIST) {
                            textPrefixDependOnRole = "You can help people with"
                            callbackDependsOnRole = "/suggest"
                        }

                        ResponseObject(
                            "$textPrefixDependOnRole: ${problem}, Location: ${user.userLocation}",
                            listOf(
                                AnswerObject("That's right", callbackDependsOnRole),
                                AnswerObject("No, back to start", "/start")),
                        )
                    }

                    else -> ResponseObject("Ooops, i dont understand", listOf(AnswerObject("Back to start", "/start")))
                }
            } else {
                ResponseObject("I dont understand", listOf(AnswerObject("I need Help","/search")),"Please restart")
            }
            sendNotification(chatId, responseText)
        }
        else if(update.hasCallbackQuery()){
            val user = users.getOrPut(update.callbackQuery.from.id){
                User(
                    userId = update.callbackQuery.from.id,
                    userHistory =  hashMapOf(),
                    userMessagesId = mutableListOf()
                )
            }
            val responseText = if (update.callbackQuery.data.isNotEmpty()) {
                val data = update.callbackQuery.data
                println(data)
                println(user)

                when {
                    data == "/start" -> {
                        user.setup()
                        var answers = mutableListOf(AnswerObject("I can help", "/give"), AnswerObject("I need Help", "/search"))
                        if (user.userLocation.isNotEmpty()) {
                            answers.add(AnswerObject("Change my location", "/changeLocation"))
                        }
                        ResponseObject(
                            "You chose $data",
                            answers,
                            "You search or give help?"
                        )
                    }
                    data == "/give" -> {
                        user.role = UserRoles.SPECIALIST
                        var nexQuestion = "Where problems you might solve?"
                        val locationButtons = availableLocations.map { AnswerObject(it, it) }
                        if (user.userLocation.isNotEmpty()) {
                            nexQuestion = "Which problem you might solve?"
                            ResponseObject("You chose $data", nexQuestion)
                        } else {
                            ResponseObject("You chose $data", locationButtons, nexQuestion)
                        }
                    }
                    data == "/search" -> {
                        user.role = UserRoles.SEARCHER
                        var nexQuestion = "Where problems you want solve?"
                        val locationButtons = availableLocations.map { AnswerObject(it, it) }
                        if (user.userLocation.isNotEmpty()) {
                            nexQuestion = "Which problem you want solve?"
                            ResponseObject("You chose $data", nexQuestion)
                        } else {
                            ResponseObject("You chose $data", locationButtons, nexQuestion)
                        }

                    }
                    //                    SETTING LOCATION IF NOT GUEST
                    !data.contains("/") &&
                    user.userLocation.isEmpty() -> {
                        user.userLocation = data
                        var nexQuestion = "Which problem you want find solve?"
                        val categoriesButtons = availableCategories.map { AnswerObject(it, it) }

                        if (user.role == UserRoles.SPECIALIST) nexQuestion = "Which problem you can solve?"
                        if (user.role == UserRoles.GUEST) {
                            ResponseObject(
                                "You say: '$data'",
                                listOf(AnswerObject("I can help", "/give"), AnswerObject("I need Help", "/search")),
                                ""
                            )
                        } else {
                            ResponseObject(
                                "You say: '$data'",
                                categoriesButtons,
                                nexQuestion)
                        }
                    }
                    data == "/changeLocation" -> {
                        var deletedLocation = user.userLocation
                        user.userLocation = ""
                        ResponseObject(
                            "You chose $data",
                            listOf(AnswerObject(deletedLocation, "previousLocation?$deletedLocation")),
                            "Your previously location is $deletedLocation Select or type your location"
                        )
                    }
                    data.contains("previousLocation") -> {
                        user.userLocation = data.substring("previousLocation?".length)
                        ResponseObject(
                            "You chose ${user.userLocation}",
//                            listOf(AnswerObject(deletedLocation, "previousLocation=$deletedLocation")),
//                            "Your previously location is $deletedLocation Select or type your location"
                        ""
                        )
                    }
                    data == "/find" -> {
                        ResponseObject(
                            "",
                            listOf(
                                AnswerObject("Aleksey", "/id53"),
                                AnswerObject("Maria", "/id32"),
                                AnswerObject("Maks", "/id803"),
                                AnswerObject("Morjan", "/id332"),
                                AnswerObject("Sholpan", "/id812")
                            ),
                            "We found your specialists"
                        )
                    }
                    data == "/suggest" -> {
                        ResponseObject("We save information about your help, thanks","Development in progress")
                    }

                    data.contains("/id") -> {
                        val toSearchQuery = hashMapOf(Pair("location", user.userLocation), Pair("problem", user.lastProblem))

//                        TODO: GET SPECIALISTS BY QUERY


                        ResponseObject(
//                            "QUERY: ${user.userLocation} and ${user.lastProblem}",
                            "QUERY: $data",
                            listOf(AnswerObject("Feedback", "/feedback")),
                            "Please leave your feedback"
                        )
                    }
                    data == "/feedback" -> {
                        ResponseObject(
                            "You chose $data",
                            listOf(
                                AnswerObject("I found help, thanks!", "/thanks"),
                                AnswerObject("I not found help", "/notFound"),
                                AnswerObject("have a bone to pick", "/claims")),
                            "Come again"
                        )
                    }
                    data == "/thanks" ||
                    data == "/claims" ||
                    data == "/notFound" -> {
                        ResponseObject(
                            "You chose $data",
//                            listOf(
//                                AnswerObject("I found help, thanks!", "/thanks"),
//                                AnswerObject("I not found help", "/not_found"),
//                                AnswerObject("have a bone to pick", "/claims")),
                            "Development in progress"
                        )
                    }

                    else -> ResponseObject("Its not a command", "")
                }
            } else {
                ResponseObject("I dont understand", listOf(AnswerObject("I need Help","/search")),"Please restart")
            }

            sendNotification(update.callbackQuery.from.id.toString(), responseText)
        }

    }

    private fun sendNotification(chatId: String, responseObject: ResponseObject) {
        var finalMessage = responseObject.responseText + " \n"+ responseObject.nextQuestion
        val responseMessage = SendMessage(chatId, finalMessage)
        responseMessage.enableMarkdownV2(true)
        if (responseObject.answerVariance.isNotEmpty()) {
            val buttons  = responseObject.answerVariance.map {answer ->
                val button = InlineKeyboardButton()
                button.text = answer.text
                button.callbackData = answer.callback
                button
            }
            responseMessage.replyMarkup = getInlineKeyBoardMessage(
                listOf(buttons)
            )
        }
        execute(responseMessage)
    }

    private fun getReplyMarkup(allButtons: List<List<String>>): ReplyKeyboard? {
        val markup = ReplyKeyboardMarkup()
        markup.keyboard = allButtons.map { rowButtons ->
            val row = KeyboardRow()
            rowButtons.forEach{rowButton -> row.add(rowButton) }
            row
        }
        return markup
    }

    fun getInlineKeyBoardMessage(allButtons: List<List<InlineKeyboardButton>>): InlineKeyboardMarkup {
        val inlineKeyboardMarkup = InlineKeyboardMarkup()
        inlineKeyboardMarkup.keyboard = allButtons.map { rowButtons ->
            val row : MutableList<InlineKeyboardButton> = ArrayList()
            rowButtons.forEach { rowButton -> row.add(rowButton) }
            row
        }
        return inlineKeyboardMarkup
    }
}

fun User.setup() {
    this.role = UserRoles.GUEST
    this.userHistory = hashMapOf()
    this.userMessagesId = mutableListOf()
    this.lastProblem = ""
    this.propblemsList = mutableListOf()
    println(this)
}

enum class UserRoles {
    SPECIALIST, SEARCHER, GUEST
}
