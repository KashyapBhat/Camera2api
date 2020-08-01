package kashyap.`in`.cameraapplication.network

import kashyap.`in`.cameraapplication.BuildConfig
import kashyap.`in`.cameraapplication.common.EMAIL_ID
import okhttp3.Interceptor
import okhttp3.Response

class HeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.run {
        val response = proceed(
            request()
                .newBuilder()
                .addHeader("appId", "")
                .addHeader("device", "android")
                .addHeader("email", EMAIL_ID)
                .build()
        )
        response
    }
}
