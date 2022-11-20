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

    private val availableLocations : Set<String> = specialists.map { it.location }.toSet()

    private val availableCategories : Set<String> = initiateMockUniqueSpecialistsCategories()

    private val specialityToCategory : HashMap<String, List<String>> = initiateMockSpecialityToCategoryMap()



    private var users : HashMap<Long, User> = hashMapOf()
        get() {
            return field
        }
        set(value) {}

    fun initiateMockUniqueSpecialistsCategories() : Set<String> {
        val categories : Set<String> = specialists.map { it.categories }.flatMap { it }.toSet()

        return categories
    }

    private fun initiateMockSpecialityToCategoryMap(): HashMap<String, List<String>> {
        return hashMapOf(
            Pair("Юридические услуги", listOf("Нотариус", "Representation in court", "Other")),
            Pair("Жилье", listOf("Аренда", "Купля-продажа", "Other")),
            Pair("Здоровье", listOf("Терапевт", "Стоматолог", "Детский врач")),
            Pair("Услуги для животных", listOf("Ветеринар", "Выгул животных", "Передержка")),
            Pair("Ремонт техники", listOf("Холодильники", "Мобильные телефоны и планшеты", "Прочее")),
            Pair("Ремонт автомобилей", listOf("Шиномонтаж", "Диагностика", "Прочее")),
            Pair("Аренда автомобиля", listOf("Аренда автомобиля")),
        )
    }

    fun initiateMockSpecialistList(): List<Specialist> {
        return listOf<Specialist>(
            Specialist(1, "Михаил", "Астана", listOf("Аренда автомобиля"), "vk\\.com\\/michaelDriver"),
            Specialist(2, "Николай", "Астана", listOf("Аренда автомобиля"), "vk\\.com\\/nikAuto"),
            Specialist(6, "Георгий", "Астана", listOf("Детский врач"), "\\+7 700 00 00 00"),
            Specialist(3, "Катя", "Астана", listOf("Нотариус"), "\\+7 700 10 99 33 @kate"),
            Specialist(4, "Шолпан", "Астана", listOf("Нотариус"), "\\+7 700 10 51 10"),

            Specialist(5, "Asiya", "Шимкент", listOf("Терапевт"), "\\+7 700 05 04 51, asyiya@shimkent\\.com"),
            Specialist(5, "Мария", "Шимкент", listOf("Терапевт"), "\\+7 705 91 24 32, мария@shimkent\\.com"),

            Specialist(7, "Мария Фаулер", "Бишкек", listOf("Терапевт"), "@mashaF"),
            Specialist(8, "Anastasiya", "Бишкек", listOf("Терапевт"), "@yourHealth"),
            Specialist(8, "Максим", "Бишкек", listOf("Аренда автомобиля"), "@carBishkek"),
            Specialist(9, "Sasha E.", "Бишкек", listOf("Ветеринар"), "@englishWithSashaE"),

            Specialist(10, "Alima", "Ереван", listOf("Нотариус"), "@alima"),
            Specialist(11, "Alina", "Ереван", listOf("Аренда автомобиля"), "@alima"),
            Specialist(12, "Alik Gohaev", "Ереван", listOf("Аренда"), "yourErevanHousing\\.com"),
            Specialist(13, "Alexander", "Ереван", listOf("Купля-продажа"), "yourErevanHousing\\.com"),
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
        constructor(responseText: String, nexQuestion: String) : this(responseText, listOf(AnswerObject("Вернуться в начало", "/start")), nexQuestion)
    }

    override fun onUpdateReceived(update: Update) {
//        println(availableLocations.toSet())
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
                            listOf(AnswerObject("Я могу помочь", "/give"), AnswerObject("Мне нужна помощь", "/search")),
                            "Вы можете помочь или вам нужна помощь?"
                        )
                    }

//                    SETTING LOCATION IF NOT GUEST
                    !messageText.contains("/") &&
                    user.userLocation.isEmpty() -> {
                        user.userLocation = messageText
                        var nexQuestion = "Какая помощь вам требуется?"
                        val categoriesButtons = availableCategories.map { AnswerObject(it, it) }

                        if (user.role == UserRoles.SPECIALIST) nexQuestion = "Чем вы могли бы помочь?"
                        if (user.role == UserRoles.GUEST) {
                            ResponseObject(
                                "You say: '$messageText'",
                                listOf(AnswerObject("Я могу помочь", "/give"), AnswerObject("Мне нужна помощь", "/search")),
                                ""
                            )
                        } else {
                            ResponseObject(
                                "You say: '$messageText'",
                                categoriesButtons,
                                nexQuestion)
                        }

                    }

//                    SETTING PROBLEM
                    !messageText.contains("/") &&
                            !user.userLocation.isEmpty()  -> {
                        val problem = messageText
                        user.lastProblem = problem
                        user.propblemsList.add(problem)
                        var textPrefixDependOnRole = "Вы ищите"
                        var callbackDependsOnRole = "/find"
                        if (user.role === UserRoles.SPECIALIST) {
                            textPrefixDependOnRole = "Вы можете помочь с"
                            callbackDependsOnRole = "/suggest"
                        }

                        ResponseObject(
                            "$textPrefixDependOnRole: ${problem}, Локация: ${user.userLocation}",
                            listOf(
                                AnswerObject("Всё верно!", callbackDependsOnRole),
                                AnswerObject("Нет, вернуться к началу", "/start")),
                        )
                    }

                    else -> ResponseObject("Что-то пошло не так", listOf(AnswerObject("Вернуться к началу", "/start")))
                }
            } else {
                ResponseObject("Понимаю только текст", listOf(AnswerObject("Вернуться к началу","/search")),"Верниуться к началу")
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
//                println(data)
//                println(user)

                when {
                    data == "/start" -> {
                        user.setup()
                        var answers = mutableListOf(AnswerObject("Я могу помочь", "/give"), AnswerObject("Мне требуется помощь", "/search"))
                        if (user.userLocation.isNotEmpty()) {
                            answers.add(AnswerObject("Изменить мою локацию", "/changeLocation"))
                        }
                        ResponseObject(
                            "You chose $data",
                            answers,
                            "Вы можете помочь или вам нужна помощь?"
                        )
                    }
                    data == "/give" -> {
                        user.role = UserRoles.SPECIALIST
                        var nexQuestion = "В какой локации вам требуется помощь?"
                        val locationButtons = availableLocations.map { AnswerObject(it, it) }
                        if (user.userLocation.isNotEmpty()) {
                            nexQuestion = "Чем вы могли бы помочь?"
                            ResponseObject("You chose $data", nexQuestion)
                        } else {
                            ResponseObject("You chose $data", locationButtons, nexQuestion)
                        }
                    }
                    data == "/search" -> {
                        user.role = UserRoles.SEARCHER
                        var nexQuestion = "В какой локации вам требуется помощь?"
                        val locationButtons = availableLocations.map { AnswerObject(it, it) }
                        if (user.userLocation.isNotEmpty()) {
                            nexQuestion = "Чем вы могли бы помочь?"
                            val problemsButtons = specialityToCategory.keys.map { AnswerObject(it, it)}
                            ResponseObject("You chose $data", problemsButtons, nexQuestion)
                        } else {
                            ResponseObject("You chose $data", locationButtons, nexQuestion)
                        }

                    }
                    //                    SETTING LOCATION IF NOT GUEST
                    !data.contains("/") &&
                    user.userLocation.isEmpty() -> {
                        user.userLocation = data
                        var nexQuestion = "Какая помощь вам требуется?"
                        val categoriesButtons = availableCategories.map { AnswerObject(it, it) }

                        if (user.role == UserRoles.SPECIALIST) nexQuestion = "Чем вы могли бы помочь?"
                        if (user.role == UserRoles.GUEST) {
                            ResponseObject(
                                "You say: '$data'",
                                listOf(AnswerObject("Я могу помочь", "/give"), AnswerObject("Мне требуется помощь", "/search")),
                                ""
                            )
                        } else {
                            ResponseObject(
                                "You say: '$data'",
                                categoriesButtons,
                                nexQuestion)
                        }
                    }

                    //                    SETTING PROBLEM OR SUGGEST IF SPECIALIST
                    !data.contains("/") &&
                            !user.userLocation.isEmpty()  -> {
                        val problem = data
                        user.lastProblem = problem
                        user.propblemsList.add(problem)
                        var textPrefixDependOnRole = "Вы ищите"
                        var callbackDependsOnRole = "/find"
                        if (user.role === UserRoles.SPECIALIST) {
                            textPrefixDependOnRole = "Вы можете помочь людям с"
                            callbackDependsOnRole = "/suggest"
                        }

                        ResponseObject(
                            "$textPrefixDependOnRole: ${problem}, Location: ${user.userLocation}",
                            listOf(
                                AnswerObject("Всё верно!", callbackDependsOnRole),
                                AnswerObject("Нет, вернуться к началу", "/start")),
                        )
                    }
                    data == "/changeLocation" -> {
                        var deletedLocation = user.userLocation
                        user.userLocation = ""
                        ResponseObject(
                            "You chose $data",
                            listOf(AnswerObject(deletedLocation, "previousLocation?$deletedLocation")),
                            "Ваша предыдущая локация это $deletedLocation \n Выберите другую локацию"
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
                        val specialistInLocation = specialists.filter { it.location == user.userLocation }.filter { it.categories.contains(user.lastProblem) }
//                        TOOD: HANDLE IF NOT FOUND OR NOT SHOW EMPTY CATEGORIES IN CURRENT LOCATION
                        ResponseObject(
                            "",
                            specialistInLocation.map{AnswerObject(it.name, "/id${it.id}")},
                            "Мы нашли тех, кто может вам помочь"
                        )
                    }
                    data == "/suggest" -> {
                        ResponseObject("Мы сохранили информацию о вас, спасибо","Development in progress")
                    }

                    data.contains("/id") -> {
                        val toSearchQuery = hashMapOf(Pair("location", user.userLocation), Pair("problem", user.lastProblem))
                        val specialistId = data.substring("/id".length).toLong()
                        val specialist = specialists.find{ it.id == specialistId }

                        if (specialist != null) {
                            ResponseObject(
//                            "QUERY: ${user.userLocation} and ${user.lastProblem}",
                            "Имя: ${specialist.name} \n" +
                                        "Локация: ${specialist.location}\n" +
                                        "Категории: ${specialist.categories}\n" +
                                        "Дополнительная информация: ${specialist.additionalData}\n",
                                listOf(AnswerObject("Оставить обратную связь", "/feedback")),
                                ""
                            )
                        } else {
                            ResponseObject(
//                            "QUERY: ${user.userLocation} and ${user.lastProblem}",
                                "Что-то пошло не так",
                                listOf(AnswerObject("Оставить обратную связь", "/feedback")),
                                ""
                            )
                        }
//                        TODO: GET SPECIALISTS BY QUERY



                    }
                    data == "/feedback" -> {
                        ResponseObject(
                            "You chose $data",
                            listOf(
                                AnswerObject("Я нашел решение проблемы, спасибо!", "/thanks"),
                                AnswerObject("Я не нашел решения", "/notFound"),
                                AnswerObject("Оставить отзыв", "/claims")),
                            ""
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

                    else -> ResponseObject("Неизвестная команда", listOf(AnswerObject("Вернуться к началу", "/start")),"Верниуться к началу")
                }
            } else {
                ResponseObject("Неизвестная команда", listOf(AnswerObject("Вернуться к началу", "/start")),"Верниуться к началу")
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
//    println(this)
}

enum class UserRoles {
    SPECIALIST, SEARCHER, GUEST
}
