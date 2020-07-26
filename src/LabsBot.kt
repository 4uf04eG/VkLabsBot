import com.petersamokhin.vksdk.core.client.VkApiClient
import com.petersamokhin.vksdk.core.model.VkSettings
import com.petersamokhin.vksdk.core.model.objects.Keyboard
import com.petersamokhin.vksdk.core.model.objects.TextButton
import com.petersamokhin.vksdk.core.model.objects.inlineKeyboard
import com.petersamokhin.vksdk.core.model.objects.keyboard
import com.petersamokhin.vksdk.http.VkOkHttpClient


class LabsBot(groupId: Int, accessToken: String) {
    private var client: VkApiClient

    init {
        val vkHttpClient = VkOkHttpClient()

        client = VkApiClient(groupId, accessToken, VkApiClient.Type.Community, VkSettings(vkHttpClient))

        client.onMessage { messageEvent ->
            if (messageEvent.message.text == "Start") {
                initStartMessageResponse(messageEvent.message.peerId)
            }
        }
    }

    fun startLongPolling() {
        client.startLongPolling()
    }

    private fun initStartMessageResponse(id: Int) {
        client.sendMessage {
            peerId = id
            message = "Привет! Во время сессии [id87738858|меня] часто просят поделиться моими лабами." +
                    " Мне не жалко и я делюсь, однако запросов достаточно много и мне лень каждый" +
                    " раз раскидывать одни и те же ссылки.\n\n" +
                    "Гениальное решение - бот, который будет раздавать всё за меня.\n\n" +
                    "Для работы с ботом выберите учебный курс."
            keyboard = inlineKeyboard {
                row { primaryButton(label = "Первый курс") }
                row { primaryButton(label = "Второй курс") }
                row { primaryButton(label = "Третий курс") }
                row { primaryButton(label = "Четвёртый курс") }
            }
        }.execute()
    }
}
