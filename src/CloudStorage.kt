import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.sharing.CreateSharedLinkWithSettingsErrorException
import java.io.FileOutputStream

class CloudStorage(accessToken: String) {
    private val dropboxClient: DbxClientV2 = DbxClientV2(DbxRequestConfig(accessToken), accessToken)

    fun requestAllSubjectsList(): List<String> {
        val years = dropboxClient.files().listFolder("/Labs").entries
        val subjects = mutableListOf<String>()

        for (year in years) {
            val semesters = dropboxClient.files()
                    .listFolder("/Labs/${year.name}/").entries

            for (semester in semesters) {
                subjects.addAll(dropboxClient.files()
                        .listFolder("/Labs/${year.name}/${semester.name}/").entries.map { it.name })
            }
        }

        return subjects
    }

    fun requestSubjectsListForSemester(year: String, semester: String): List<String> {
        return dropboxClient.files()
                .listFolder("/Labs/$year/$semester/").entries.map { it.name }
    }

    fun generateZipFile(year: String, semester: String, subject: String) {
       dropboxClient.files()
               .downloadZip("/Labs/$year/$semester/$subject/")
               .download(FileOutputStream("files/$subject.zi"))
    }

    fun generateFolderLink(year: String, semester: String, subject: String): String {
        return try {
            dropboxClient.sharing()
                    .createSharedLinkWithSettings("/Labs/$year/$semester/$subject").url
        } catch (e: CreateSharedLinkWithSettingsErrorException) {
            e.errorValue.sharedLinkAlreadyExistsValue.metadataValue.url
        }
    }
}
