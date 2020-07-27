package main.kotlin

fun main() {
    val groupId: Int = 197339206
    val vkAccessToken: String = System.getenv("VK_ACCESS_TOKEN")
    val dropboxAccessToken: String = System.getenv("DROPBOX_ACCESS_TOKEN")

    val storage = CloudStorage(dropboxAccessToken)
    val bot = LabsBot(groupId, vkAccessToken, storage)

    bot.startLongPolling()
}
