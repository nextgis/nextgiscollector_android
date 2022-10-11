/*
 * Project:  NextGIS Collector
 * Purpose:  Light mobile GIS for collecting data
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * ********************************************************************
 * Copyright (c) 2018 NextGIS, info@nextgis.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextgis.collector.util

import android.content.SharedPreferences
import com.nextgis.collector.BuildConfig
import com.nextgis.maplib.util.Constants
import com.nextgis.maplib.util.FileUtil
import com.nextgis.maplib.util.HttpResponse
import com.nextgis.maplib.util.NetworkUtil.*
import com.nextgis.maplibui.util.NGIDUtils
import java.io.IOException
import java.io.OutputStream
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class NetworkUtil {
    companion object {
        @Throws(NoSuchAlgorithmException::class)
        fun hash(str: String): String {
            val rnd = str.toLowerCase() + str.reversed().toUpperCase()
            return md5(rnd)
        }

        private fun md5(str: String): String {
            val md = MessageDigest.getInstance("MD5")
            md.update(str.toByteArray())
            val digest = md.digest()
            return bytesToHex(digest)
        }

        private fun bytesToHex(bytes: ByteArray): String {
            return String.format("%032x", BigInteger(1, bytes))
        }

        @Throws(IOException::class)
        fun getHttpConnection(method: String, targetURL: String, token: String): HttpURLConnection {
            val url = URL(targetURL)
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "*/*")
            conn.setRequestProperty("User-Agent",
                    getUserAgentPrefix() + " "
                            + Constants.MAPLIB_USER_AGENT_PART + " " + getUserAgentPostfix())
            conn.setRequestProperty("Authorization", "Bicycle $token")

            conn.doInput = true
            conn.useCaches = false
            conn.readTimeout = TIMEOUT_SOCKET
            conn.connectTimeout = TIMEOUT_CONNECTION
            if (method.isNotEmpty())
                conn.requestMethod = method

            return conn
        }

        @Throws(IOException::class)
        fun getHttpResponse(conn: HttpURLConnection, readErrorResponseBody: Boolean): HttpResponse {
            return getHttpResponse(conn, null, readErrorResponseBody)
        }

        @Throws(IOException::class)
        fun getHttpResponse(conn: HttpURLConnection, outputStream: OutputStream?, readErrorResponseBody: Boolean): HttpResponse {
            val method = conn.requestMethod
            val code = conn.responseCode
            val message = conn.responseMessage
            val response = HttpResponse(code, message)

            val ok = code == HttpURLConnection.HTTP_OK
            val created = code == HttpURLConnection.HTTP_CREATED && method == HTTP_POST
            val accepted = code == HttpURLConnection.HTTP_ACCEPTED
            val empty = code == HttpURLConnection.HTTP_NO_CONTENT
            if (!(ok || created || accepted || empty) && readErrorResponseBody) {
                response.responseBody = responseToString(conn.errorStream)
                return response
            }

            val stream = conn.inputStream
            if (stream == null) {
                response.responseCode = ERROR_DOWNLOAD_DATA
                response.responseMessage = null
                return response
            }

            if (outputStream != null) {
                val data = ByteArray(Constants.IO_BUFFER_SIZE)
                FileUtil.copyStream(stream, outputStream, data, Constants.IO_BUFFER_SIZE)
                outputStream.close()
                stream.close()
            } else {
                val body = responseToString(stream)
                response.responseBody = body
            }

            response.isOk = true
            return response
        }

        fun getEmailOrUsername(preferences: SharedPreferences): String {
            val email = preferences.getString(NGIDUtils.PREF_EMAIL, "")
            val username = preferences.getString(NGIDUtils.PREF_USERNAME, "")
            return if (email.isNullOrBlank() || email == "null") username ?: "" else email
        }
    }
}