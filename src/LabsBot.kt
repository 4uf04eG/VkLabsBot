import com.petersamokhin.vksdk.core.client.VkApiClient
import com.petersamokhin.vksdk.core.http.paramsOf
import com.petersamokhin.vksdk.core.io.FileOnDisk
import com.petersamokhin.vksdk.core.model.VkSettings
import com.petersamokhin.vksdk.core.model.objects.Keyboard
import com.petersamokhin.vksdk.core.model.objects.UploadableContent
import com.petersamokhin.vksdk.core.model.objects.keyboard
import com.petersamokhin.vksdk.http.VkOkHttpClient


class LabsBot(groupId: Int, accessToken: String, cloudStorage: CloudStorage) {
    private val client: VkApiClient
    private val cloudStorage: CloudStorage

    private var year: String = ""
    private var semester: String = ""
    private var subject: String = ""

    init {
        val vkHttpClient = VkOkHttpClient()

        this.cloudStorage = cloudStorage
        client = VkApiClient(groupId, accessToken, VkApiClient.Type.Community, VkSettings(vkHttpClient))
        requestYear()
    }

    fun startLongPolling() {
        client.startLongPolling()
    }

    private fun requestYear(): Keyboard {
        client.clearLongPollListeners()
        client.onMessage { messageEvent ->
            val id = messageEvent.message.peerId

            when (messageEvent.message.text) {
                "Start" -> initStartMessageResponse(id)
                "Первый курс" -> {
                    year = "Первый курс"
                    requestSemester(id)
                }
                "Второй курс" -> {
                    year = "Второй курс"
                    requestSemester(id)
                }
                "Третий курс" -> {
                    year = "Третий курс"
                    requestSemester(id)
                }
                "Четвёртый курс" -> {
                    year = "Четвёртый курс"
                    requestSemester(id)
                }
                else -> client.sendMessage {
                    peerId = id
                    message = "Неизвестная команда"
                    keyboard = requestYear()
                }.execute()
            }
        }

        return keyboard(oneTime = true) {
            row { primaryButton("Первый курс") }
            row { primaryButton("Второй курс") }
            row { primaryButton("Третий курс") }
            row { primaryButton("Четвёртый курс") }
        }

    }

    private fun initStartMessageResponse(id: Int) {
        client.sendMessage {
            peerId = id
            message = "Привет! Во время сессии [id87738858|меня] часто просят поделиться моими лабами." +
                    " Мне не жалко и я делюсь, однако запросов достаточно много и мне лень каждый" +
                    " раз раскидывать одни и те же ссылки.\n\n" +
                    "Гениальное решение - бот, который будет раздавать всё за меня.\n\n" +
                    "Для работы с ботом выберите учебный курс."
            keyboard = requestYear()
        }.execute()
    }

    private fun requestSemester(id: Int) {
        client.clearLongPollListeners()
        client.sendMessage {
            peerId = id
            message = "Выберите семестр"
            keyboard = keyboard {
                row { primaryButton("Первый семестр") }
                row { primaryButton("Второй семестр") }
                row { primaryButton("Назад") }
            }
        }.execute()

        client.onMessage { messageEvent ->
            when (messageEvent.message.text) {
                "Первый семестр" -> {
                    semester = "Первый семестр"
                    requestListOfSubjects(id)
                }
                "Второй семестр" -> {
                    semester= "Второй семестр"
                    requestListOfSubjects(id)
                }
                // Баг, когда колбэки для курсовов не устанавливаются
                "Назад" -> {
                    client.sendMessage {
                        peerId = id
                        message = "Выберите курс"
                        keyboard = requestYear()
                    }.execute()
                }
            }
        }
    }

    private fun requestListOfSubjects(id: Int) {
        val subjects = cloudStorage.requestSubjectsList(year, semester)
        val converted = subjects.map { SubjectNameConverter.convert(it) }

        client.clearLongPollListeners()
        client.sendMessage {
            peerId = id
            message = "Выберите предмет"
            keyboard = keyboard {
                converted.forEach{ row { primaryButton(label = it) } }
                row { primaryButton("Назад") }
            }
        }.execute()
        client.onMessage{ messageEvent ->
            if (converted.contains(messageEvent.message.text)) {
                val index = converted.indexOf(messageEvent.message.text)
                subject = subjects[index]
                requestAction(id)
            } else if (messageEvent.message.text == "Назад") {
                requestSemester(id)
            }
        }
    }

    private fun requestAction(id: Int) {
        client.clearLongPollListeners()
        client.sendMessage {
            peerId = id
            message = "Выберите действие"
            keyboard = keyboard {
                row { primaryButton("Сгенерировать ссылку") }
                row { primaryButton("Сгенерировать zip-архив") }
                row { primaryButton("Назад") }
            }
        }.execute()

        client.onMessage { messageEvent ->
            when (messageEvent.message.text) {
                "Сгенерировать ссылку" -> {
                    client.sendMessage {
                        peerId = id
                        message = "Ссылка на папку: ${cloudStorage.generateFolderLink(year, semester, subject)}"
                    }.execute()
                }
                "Сгенерировать zip-архив" -> {
                    client.sendMessage {
                        peerId = id
                        message = "Ожидайте загрузки файла"
                    }.execute()

                    cloudStorage.generateZipFile(year, semester, subject)
                    loadAndAttachZipFile(id, subject)
                }
                "Назад" -> {
                    requestListOfSubjects(id)
                }
            }
        }
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
