import com.mongodb.client.MongoDatabase
import com.petersamokhin.vksdk.core.client.VkApiClient
import com.petersamokhin.vksdk.core.http.paramsOf
import com.petersamokhin.vksdk.core.io.FileOnDisk
import com.petersamokhin.vksdk.core.model.VkSettings
import com.petersamokhin.vksdk.core.model.objects.Keyboard
import com.petersamokhin.vksdk.core.model.objects.UploadableContent
import com.petersamokhin.vksdk.core.model.objects.keyboard
import com.petersamokhin.vksdk.http.VkOkHttpClient
import org.litote.kmongo.eq
import org.litote.kmongo.findOne
import org.litote.kmongo.getCollection
import kotlin.concurrent.fixedRateTimer

class LabsBot(
    groupId: Int, accessToken: String,
    private val cloudStorage: CloudStorage,
    private val database: MongoDatabase
) {
    private val client: VkApiClient

    init {
        val vkHttpClient = VkOkHttpClient()
        client = VkApiClient(groupId, accessToken, VkApiClient.Type.Community, VkSettings(vkHttpClient))

        initListeners()
//
//        // I haven't found any better way to handle multi-user subject selection.
//        // So I just initialize list of all subjects and update it every hour
        fixedRateTimer(
            name = "subjects-update",
            period = 3600000 /* Millisecond in hour */
        ) { initListeners() }
    }

    fun startLongPolling() {
        client.startLongPolling()
    }

    private fun getUser(userId: Int): UserState {
        val collection = database.getCollection<UserState>()
        val user = collection.findOne(UserState::userId eq userId) ?: createUser(userId)
        user.database = collection
        return user
    }

    private fun isUserExists(userId: Int): Boolean {
        val collection = database.getCollection<UserState>()
        return collection.findOne(UserState::userId eq userId) != null
    }

    private fun createUser(userId: Int): UserState {
        val collection = database.getCollection<UserState>()
        val user = UserState(userId, null, null, null, State.NONE)
        collection.insertOne(user)
        return user
    }

    private fun initListeners() {
        val allSubjects = cloudStorage.requestAllSubjectsList()
        val converted = allSubjects.map { SubjectNameConverter.convert(it) }

        client.clearLongPollListeners()
        client.onMessage { messageEvent ->
            val id = messageEvent.message.peerId

            if (!isUserExists(id)) {
                initStartMessageResponse(id)
                return@onMessage
            }

            when (messageEvent.message.text) {
                "Start", "Начать", "Привет" -> initStartMessageResponse(id)
                "Первый курс" -> {
                    getUser(id).year = "Первый курс"
                    requestSemester(id)
                }
                "Второй курс" -> {
                    getUser(id).year = "Второй курс"
                    requestSemester(id)
                }
                "Третий курс" -> {
                    getUser(id).year = "Третий курс"
                    requestSemester(id)
                }
                "Четвёртый курс" -> {
                    getUser(id).year = "Четвёртый курс"
                    requestSemester(id)
                }
                "Первый семестр" -> {
                    if (hasRequestErrors(
                            id,
                            validateYear = true
                        )
                    ) {
                        return@onMessage
                    }

                    getUser(id).semester = "Первый семестр"
                    requestListOfSubjects(id)
                }
                "Второй семестр" -> {
                    if (hasRequestErrors(
                            id,
                            validateYear = true
                        )
                    ) {
                        return@onMessage
                    }

                    getUser(id).semester = "Второй семестр"
                    requestListOfSubjects(id)
                }
                "Сгенерировать ссылку" -> {
                    if (hasRequestErrors(
                            id,
                            validateYear = true,
                            validateSemester = true,
                            validateSubject = true
                        )
                    ) {
                        return@onMessage
                    }

                    sendTypingStatus(id)

                    client.sendMessage {
                        peerId = id
                        message = "Ссылка на папку: ${
                            cloudStorage.generateFolderLink(
                                getUser(id).year!!,
                                getUser(id).semester!!,
                                getUser(id).subject!!
                            )
                        }"
                    }.execute()
                }
                "Сгенерировать zip-архив" -> {
                    if (hasRequestErrors(
                            id,
                            validateYear = true,
                            validateSemester = true,
                            validateSubject = true
                        )
                    ) {
                        return@onMessage
                    }

                    client.sendMessage {
                        peerId = id
                        message = "Ожидайте загрузки файла"
                    }.execute()

                    sendTypingStatus(id)

                    cloudStorage.generateZipFile(
                        getUser(id).year!!,
                        getUser(id).semester!!,
                        getUser(id).subject!!
                    )
                    loadAndAttachZipFile(id, getUser(id).subject!!)
                }
                "Назад" -> processReturnRequest(id)
                in converted -> {
                    if (hasRequestErrors(
                            id,
                            validateYear = true,
                            validateSemester = true
                        )
                    ) {
                        return@onMessage
                    }

                    val index = converted.indexOf(messageEvent.message.text)
                    getUser(id).subject = allSubjects[index]
                    requestAction(id)
                }
                else -> client.sendMessage {
                    peerId = id
                    message = "Неизвестная команда"
                    keyboard = requestYearKeyboard(id)
                }.execute()
            }
        }
    }

    private fun processReturnRequest(id: Int) {
        when (getUser(id).state) {
            State.SEMESTER_SELECTION -> client.sendMessage {
                peerId = id
                message = "Выберите курс"
                keyboard = requestYearKeyboard(id)
            }.execute()
            State.SUBJECT_SELECTION -> requestSemester(id)
            State.ACTION_SELECTION -> requestListOfSubjects(id)
            else -> client.sendMessage {
                peerId = id
                message = "Ошибка! Выберите курс"
                keyboard = requestYearKeyboard(id)
            }.execute()
        }
    }

    private fun sendTypingStatus(id: Int) {
        client.call(
            "messages.setActivity",
            paramsOf("type" to "typing", "peer_id" to id), batch = true
        )
    }

    private fun hasRequestErrors(
        id: Int,
        validateYear: Boolean = false,
        validateSemester: Boolean = false,
        validateSubject: Boolean = false
    ): Boolean {
        var errors = ""

        if (validateYear && (getUser(id).year ?: "").isEmpty()) {
            errors += "Выберите курс\n"
        }
        if (validateSemester && (getUser(id).semester ?: "").isEmpty()) {
            errors += "Выберите семестр\n"
        }
        if (validateSubject && (getUser(id).subject ?: "").isEmpty()) {
            errors += "Выберите предмет\n"
        }

        if (errors.isEmpty()) return false

        client.sendMessage {
            peerId = id
            message = "Ошибка!\n\n$errors"
            keyboard = requestYearKeyboard(id)
        }.execute()

        return true
    }

    private fun requestYearKeyboard(id: Int): Keyboard {
        getUser(id).state = State.YEAR_SELECTION

        return keyboard {
            row { primaryButton("Первый курс") }
            row { primaryButton("Второй курс") }
            row { primaryButton("Третий курс") }
            row { primaryButton("Четвёртый курс") }
        }
    }

    private fun clearUserData(id: Int) {
        if (isUserExists(id)) {
            getUser(id).year = null
            getUser(id).semester = null
            getUser(id).subject = null
            getUser(id).state = State.NONE
        }
    }

    private fun initStartMessageResponse(id: Int) {
        clearUserData(id)
        client.sendMessage {
            peerId = id
            message = "Привет! Во время сессии [id87738858|меня] часто просят поделиться моими лабами." +
                    " Мне не жалко и я делюсь, однако запросов достаточно много и мне лень каждый" +
                    " раз раскидывать одни и те же ссылки.\n\n" +
                    "Гениальное решение - бот, который будет раздавать всё за меня.\n\n" +
                    "Для работы с ботом выберите учебный курс."
            keyboard = requestYearKeyboard(id)
        }.execute()
    }

    private fun requestSemester(id: Int) {
        getUser(id).state = State.SEMESTER_SELECTION

        client.sendMessage {
            peerId = id
            message = "Выберите семестр"
            keyboard = keyboard {
                row { primaryButton("Первый семестр") }
                row { primaryButton("Второй семестр") }
                row { primaryButton("Назад") }
            }
        }.execute()
    }

    private fun requestListOfSubjects(id: Int) {
        getUser(id).state = State.SUBJECT_SELECTION

        sendTypingStatus(id)

        val subjects = cloudStorage.requestSubjectsListForSemester(getUser(id).year!!, getUser(id).semester!!)
        val converted = subjects.map { SubjectNameConverter.convert(it) }

        client.sendMessage {
            peerId = id
            message = "Выберите предмет"
            keyboard = keyboard {
                converted.forEach { row { primaryButton(label = it) } }
                row { primaryButton("Назад") }
            }
        }.execute()
    }

    private fun requestAction(id: Int) {
        getUser(id).state = State.ACTION_SELECTION

        client.sendMessage {
            peerId = id
            message = "Выберите действие"
            keyboard = keyboard {
                row { primaryButton("Сгенерировать ссылку") }
                row { primaryButton("Сгенерировать zip-архив") }
                row { primaryButton("Назад") }
            }
        }.execute()
    }

    private fun loadAndAttachZipFile(id: Int, fileName: String) {
        val attachmentStr = client.uploader().uploadContent(
            "docs.getMessagesUploadServer",
            "docs.save",
            params = paramsOf("type" to "doc", "peer_id" to id),
            items = listOf(
                UploadableContent.File(
                    fieldName = "file",
                    fileName = "$fileName.zi",
                    mediaType = "doc",
                    file = FileOnDisk("files/${fileName}.zi")
                )
            )
        ).let { response: String ->
            val regex = "(doc[\\d_]*)\\?".toRegex()

            if (regex.containsMatchIn(response)) {
                regex.find(response)!!.groups[1]?.value
            } else {
                ""
            }
        }

        client.sendMessage {
            peerId = id
            message = "Вк не пропускает zip-файлы. Замените zi на zip и будет вам счастье"
            attachment = attachmentStr
        }.execute()
    }
}
