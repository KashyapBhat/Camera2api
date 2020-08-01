package kashyap.`in`.cameraapplication.network

import io.reactivex.Single
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {
    @Multipart
    @POST("upload")
    fun onFileUpload(
        @Part("email") mEmail: RequestBody?,
        @Part file: MultipartBody.Part?
    ): Single<ResponseBody?>?
}