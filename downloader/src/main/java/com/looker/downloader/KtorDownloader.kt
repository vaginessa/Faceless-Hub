package com.looker.downloader

import android.util.Log
import com.looker.downloader.model.DownloadItem
import com.looker.downloader.model.DownloadState
import com.looker.downloader.model.HeaderInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.contentLength
import io.ktor.http.etag
import io.ktor.http.lastModified
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

object KtorDownloader : Downloader {

	private const val TAG = "KtorDownloader"

	private val httpClient = HttpClient(OkHttp) { expectSuccess = true }

	override suspend fun download(item: DownloadItem): Flow<DownloadState> =
		callbackFlow {
			send(DownloadState.Pending)
			val httpResponse: HttpResponse = httpClient.get(item.url) {
				onDownload { bytesSent, contentLength ->
					Log.i(TAG, "download: bytesSent: $bytesSent, contentLength: $contentLength")
					send(
						DownloadState.Progress(
							total = contentLength,
							percent = bytesSent percentBy contentLength
						)
					)
				}
			}
			val responseBody: ByteArray = httpResponse.body()
			item.file.writeBytes(responseBody)
			val headerInfo = HeaderInfo(
				eTag = httpResponse.etag(),
				contentLength = httpResponse.contentLength(),
				lastModified = httpResponse.lastModified()
			)
			send(DownloadState.Success(headerInfo))
			awaitClose { println("Cancelled") }
		}.flowOn(Dispatchers.IO)

}

infix fun Long.percentBy(denominator: Long): Int = this.toInt().times(100) / denominator.toInt()