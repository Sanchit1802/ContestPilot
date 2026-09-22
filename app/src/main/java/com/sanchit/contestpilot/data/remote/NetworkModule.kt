package com.sanchit.contestpilot.data.remote

import com.sanchit.contestpilot.BuildConfig
import com.sanchit.contestpilot.data.remote.codechef.CodeChefApi
import com.sanchit.contestpilot.data.remote.codeforces.CodeforcesApi
import com.sanchit.contestpilot.data.remote.status.AutomationStatusApi
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Builds the HTTP stack. One [OkHttpClient] is shared by every API so connection pools,
 * timeouts and the (debug-only) logger are configured in a single place.
 */
object NetworkModule {

    private const val TIMEOUT_SECONDS = 20L

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .apply {
                if (BuildConfig.DEBUG) {
                    // BASIC only: never log headers or bodies, which keeps any future
                    // authenticated request from leaking into logcat.
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        }
                    )
                }
            }
            .build()
    }

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val codeforcesApi: CodeforcesApi by lazy {
        retrofit(CodeforcesApi.BASE_URL).create(CodeforcesApi::class.java)
    }

    val codeChefApi: CodeChefApi by lazy {
        retrofit(CodeChefApi.BASE_URL).create(CodeChefApi::class.java)
    }

    val automationStatusApi: AutomationStatusApi by lazy {
        // Every call passes an absolute @Url, so this base is only a Retrofit formality.
        retrofit("https://raw.githubusercontent.com/").create(AutomationStatusApi::class.java)
    }
}
