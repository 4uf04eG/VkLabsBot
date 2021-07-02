import com.mongodb.client.MongoCollection
import org.litote.kmongo.eq
import org.litote.kmongo.setValue

class UserState(
    val userId: Int,
    year: String?,
    semester: String?,
    subject: String?,
    state: State,
) {
    @Transient
    lateinit var database: MongoCollection<UserState>

    var year: String? = year
        set(value) {
            database.updateOne(UserState::userId eq userId, setValue(UserState::year, value))
            field = value
        }

    var semester: String? = semester
        set(value) {
            database.updateOne(UserState::userId eq userId, setValue(UserState::semester, value))
            field = value
        }

    var subject: String? = subject
        set(value) {
            database.updateOne(UserState::userId eq userId, setValue(UserState::subject, value))
            field = value
        }

    var state: State = state
        set(value) {
            database.updateOne(UserState::userId eq userId, setValue(UserState::state, value))
            field = value
        }
}


