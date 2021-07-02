import org.litote.kmongo.KMongo
import java.nio.file.Files
import java.nio.file.Paths

class Main {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val groupId = 197339206
            val vkAccessToken: String = System.getenv("VK_ACCESS_TOKEN")
            val dropboxAccessToken: String = System.getenv("DROPBOX_ACCESS_TOKEN")
            val mongoDbUri = System.getenv("MONGODB_URI") ?: "mongodb://localhost/main"

            Files.createDirectories(Paths.get("files"))

            val storage = CloudStorage(dropboxAccessToken)
            val database = KMongo.createClient(connectionString = mongoDbUri).getDatabase("main")
            val bot = LabsBot(groupId, vkAccessToken, storage, database)

            bot.startLongPolling()
        }
    }
}
