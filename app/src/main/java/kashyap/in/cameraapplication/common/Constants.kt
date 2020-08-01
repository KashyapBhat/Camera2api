package kashyap.`in`.cameraapplication.common

const val CAMERA_ID = "camera_id"
const val CAMERA_FRONT = "1"
const val CAMERA_BACK = "0"

enum class ApiStatus { SUCCESS, ERROR, LOADING }

enum class ErrorCodes(val code: Int, val message: String) {
    SocketTimeOut(-1, "Timeout"),
    UnAuthorised(401, "Unauthorised"),
    NotFound(404, "Not found")
}

const val AUTH_KEY = "access-token"
const val FORMAT = "_format"

const val CONNECT_TIMEOUT: Long = 60
const val READ_TIMEOUT: Long = 60
const val WRITE_TIMEOUT: Long = 60


const val BASE_URL = "https://srv-file6.gofile.io/"

const val EMAIL_ID = "bobbikashyap@gmail.com"

