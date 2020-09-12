import com.petersamokhin.vksdk.core.client.VkApiClient
import com.petersamokhin.vksdk.core.http.paramsOf
import com.petersamokhin.vksdk.core.io.FileOnDisk
import com.petersamokhin.vksdk.core.model.VkSettings
import com.petersamokhin.vksdk.core.model.objects.Keyboard
import com.petersamokhin.vksdk.core.model.objects.UploadableContent
import com.petersamokhin.vksdk.core.model.objects.keyboard
import com.petersamokhin.vksdk.http.VkOkHttpClient
import kotlin.concurrent.fixedRateTimer

class LabsBot(groupId: Int, accessToken: String, private val cloudStorage: CloudStorage) {
    private val client: VkApiClient

    private val years: MutableMap<Int, String> = mutableMapOf()
    private val semesters: MutableMap<Int, String> = mutableMapOf()
    private val subjects: MutableMap<Int, String> = mutableMapOf()
    private val states: MutableMap<Int, State> = mutableMapOf()

    init {
        val vkHttpClient = VkOkHttpClient()
        client = VkApiClient(groupId, accessToken, VkApiClient.Type.Community, VkSettings(vkHttpClient))

        initListeners()
//
//        // I haven't found any better way to handle multi-user subject selection.
//        // So I just initialize list of all subjects and update it every hour
        fixedRateTimer(name = "subjects-update",
                period = 3600000 /* Millisecond in hour */) { initListeners() }
    }

    fun startLongPolling() {
        client.startLongPolling()
    }

    private fun initListeners() {
        val allSubjects = cloudStorage.requestAllSubjectsList()
        val converted = allSubjects.map { SubjectNameConverter.convert(it) }

        client.clearLongPollListeners()
        client.onMessage { messageEvent ->
            val id = messageEvent.message.peerId

            when (messageEvent.message.text) {
                "Start" -> initStartMessageResponse(id)
                "Первый курс" -> {
                    years[id] = "Первый курс"
                    requestSemester(id)
                }
                "Второй курс" -> {
                    years[id] = "Второй курс"
                    requestSemester(id)
                }
                "Третий курс" -> {
                    years[id] = "Третий курс"
                    requestSemester(id)
                }
                "Четвёртый курс" -> {
                    years[id] = "Четвёртый курс"
                    requestSemester(id)
                }
                "Первый семестр" -> {
                    if (hasRequestErrors(id,
                                    validateYear = true)) {
                        return@onMessage
                    }

                    semesters[id] = "Первый семестр"
                    requestListOfSubjects(id)
                }
                "Второй семестр" -> {
                    if (hasRequestErrors(id,
                                    validateYear = true)) {
                        return@onMessage
                    }

                    semesters[id] = "Второй семестр"
                    requestListOfSubjects(id)
                }
                "Сгенерировать ссылку" -> {
                    if (hasRequestErrors(id,
                                    validateYear = true,
                                    validateSemester = true,
                                    validateSubject = true)) {
                        return@onMessage
                    }

                    sendTypingStatus(id)

                    client.sendMessage {
                        peerId = id
                        message = "Ссылка на папку: ${cloudStorage.generateFolderLink(
                                years[id]!!,
                                semesters[id]!!,
                                subjects[id]!!)}"
                    }.execute()
                }
                "Сгенерировать zip-архив" -> {
                    if (hasRequestErrors(id,
                                    validateYear = true,
                                    validateSemester = true,
                                    validateSubject = true)) {
                        return@onMessage
                    }

                    client.sendMessage {
                        peerId = id
                        message = "Ожидайте загрузки файла"
                    }.execute()

                    sendTypingStatus(id)

                    cloudStorage.generateZipFile(
                            years[id]!!,
                            semesters[id]!!,
                            subjects[id]!!)
                    loadAndAttachZipFile(id, subjects[id]!!)
                }
                "Назад" -> processReturnRequest(id)
                in converted -> {
                    if (hasRequestErrors(id,
                                    validateYear = true,
                                    validateSemester = true)) {
                        return@onMessage
                    }

                    val index = converted.indexOf(messageEvent.message.text)
                    subjects[id] = allSubjects[index]
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
        when (states.getOrDefault(id, State.NONE)) {
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
        client.call("messages.setActivity",
                paramsOf("type" to "typing", "peer_id" to id), batch = true)
    }

    private fun hasRequestErrors(id: Int,
                                 validateYear: Boolean = false,
                                 validateSemester: Boolean = false,
                                 validateSubject: Boolean = false): Boolean {
        var errors = ""

        if (validateYear && years.getOrDefault(id, "").isEmpty()) {
            errors += "Выберите курс\n"
        }
        if (validateSemester && semesters.getOrDefault(id, "").isEmpty()) {
            errors += "Выберите семестр\n"
        }
        if (validateSubject && subjects.getOrDefault(id, "").isEmpty()) {
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
        states[id] = State.YEAR_SELECTION

        return keyboard {
            row { primaryButton("Первый курс") }
            row { primaryButton("Второй курс") }
            row { primaryButton("Третий курс") }
            row { primaryButton("Четвёртый курс") }
        }
    }

    private fun clearUserData(id: Int) {
        years[id] = ""
        semesters[id] = ""
        subjects[id] = ""
        states[id] = State.NONE
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
        states[id] = State.SEMESTER_SELECTION

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
        states[id] = State.SUBJECT_SELECTION

        sendTypingStatus(id)

        val subjects = cloudStorage.requestSubjectsListForSemester(years[id]!!, semesters[id]!!)
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
        states[id] = State.ACTION_SELECTION

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
