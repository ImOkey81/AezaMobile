package aeza.hostmaster.mobile.di

import android.util.Log
import aeza.hostmaster.mobile.BuildConfig
import aeza.hostmaster.mobile.data.remote.ApiService
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private const val DEFAULT_BASE_URL = "http://10.0.2.2:8080/"
private const val LEGACY_CHECK_HOST = "check-host.net"

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val DEFAULT_USER_AGENT = "curl/8.4.0"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .apply {
            if (BuildConfig.DEBUG) {
                val logging = HttpLoggingInterceptor { message ->
                    Log.d("ApiService", message)
                }.apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
                addInterceptor(logging)
            }
            if (BuildConfig.API_USERNAME.isNotBlank()) {
                val credentials = Credentials.basic(
                    BuildConfig.API_USERNAME,
                    BuildConfig.API_PASSWORD
                )
                addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .addHeader("Authorization", credentials)
                        .build()
                    chain.proceed(request)
                }
            }
            addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .build()
                chain.proceed(request)
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .setLenient()
        .create()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(resolveBaseUrl())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(client)
            .build()

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService =
        retrofit.create(ApiService::class.java)
}

private fun resolveBaseUrl(): String {
    val configured = BuildConfig.API_BASE_URL.ifBlank { DEFAULT_BASE_URL }
    return if (configured.endsWith("/")) configured else "$configured/"
}
