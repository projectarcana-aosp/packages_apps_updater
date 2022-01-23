/*
 * Copyright (C) 2021 AOSP-Krypton Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.arcana.updater.util

import android.os.Environment
import android.os.SystemProperties
import android.util.Log

import com.arcana.updater.util.Constants.MB

import java.io.File
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.text.SimpleDateFormat
import java.util.Date

import kotlin.jvm.JvmStatic

// TODO : remove all JvmStatic annotations once entire app is in kotlin
class Utils private constructor() {
    companion object {
        private const val TAG = "UpdaterUtils"

        // Build props
        private const val PROP_DEVICE = "ro.arcana.device"
        private const val PROP_VERSION = "ro.arcana.version"
        private const val PROP_DATE = "ro.arcana.build_date_utc"

        // Date format (Ex: 12 June 2021, 11:59 AM)
        private val DATE_FORMAT = SimpleDateFormat("dd MMM yyyy, hh:mm a")

        // Downloads directory as a File object
        // TODO : deal with this deprecated API
        private val DOWNLOADS_DIR = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS)

        // Get device code name
        @JvmStatic
        fun getDevice() = SystemProperties.get(PROP_DEVICE, "unavailable")

        // Get build version
        @JvmStatic
        fun getVersion() = SystemProperties.get(PROP_VERSION, "unavailable")

        // Get build date (seconds since epoch)
        @JvmStatic
        fun getBuildDate(): Long = SystemProperties.get(PROP_DATE, "0").toLong() * 1000L

        // Format given time in milliseconds with dateFormat
        @JvmStatic
        fun formatDate(date: Long) = DATE_FORMAT.format(Date(date))

        // Get a File object pointing to the @param fileName
        // file in Downloads folder
        @JvmStatic
        fun getDownloadFile(fileName: String) = File(DOWNLOADS_DIR, fileName)

        // Calculate md5 hash of the given file
        @JvmStatic
        fun computeMd5(file: File): String? {
            val md5Digest: MessageDigest
            try {
                md5Digest = MessageDigest.getInstance("MD5")
            } catch (e: NoSuchAlgorithmException) {
                return null
            }
            // Files processed will be of GB order usually, so 1MB buffer will speed up the process
            val buffer = ByteArray(MB)
            var bytesRead: Int
            try {
                file.inputStream().use {
                    bytesRead = it.read(buffer)
                    while (bytesRead != -1) {
                        md5Digest.update(buffer, 0, bytesRead)
                        bytesRead = it.read(buffer)
                    }
                }
                var builder = StringBuilder()
                md5Digest.digest().forEach({
                    builder.append(String.format("%02x", it))
                })
                return builder.toString()
            } catch (e: IOException) {
                Log.e(TAG, "IOException while computing md5 of file ${file.getAbsolutePath()}")
            }
            return null
        }
    }
}
