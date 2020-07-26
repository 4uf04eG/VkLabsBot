import com.petersamokhin.vksdk.core.client.VkApiClient
import com.petersamokhin.vksdk.core.model.VkSettings
import com.petersamokhin.vksdk.core.model.objects.keyboard
import com.petersamokhin.vksdk.http.VkOkHttpClient


fun main() {
    val groupId = 197339206
    val accessToken: String = System.getenv("ACCESS_TOKEN")

    val bot = LabsBot(groupId, accessToken)
    bot.startLongPolling()
}
