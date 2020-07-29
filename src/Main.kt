import java.nio.file.Files
import java.nio.file.Paths

class Main {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val groupId = 197339206
            val vkAccessToken: String = System.getenv("VK_ACCESS_TOKEN")
            val dropboxAccessToken: String = System.getenv("DROPBOX_ACCESS_TOKEN")

            Files.createDirectories(Paths.get("files"))

            val storage = CloudStorage(dropboxAccessToken)
            val bot = LabsBot(groupId, vkAccessToken, storage)

            bot.startLongPolling()
        }
    }
}
