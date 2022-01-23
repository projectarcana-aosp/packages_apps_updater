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

package com.arcana.updater.util;

// Constants needed application wide
public class Constants {
    // Github branch we are using for the ota
    public static final String GIT_BRANCH = "12.x";
    public static final String OTA_JSON_FILE_NAME = "ota.json";

    // SharedPreferences keys
    public static final String DOWNLOAD_ID = "download_id";
    public static final String DOWNLOAD_STATUS = "download_status";
    public static final String DOWNLOADED_PERCENT = "downloaded_percent";
    public static final String DOWNLOADED_SIZE = "downloaded_size";
    public static final String ENTRY_DATE = "entry_date";
    public static final String GLOBAL_STATUS = "global_status";
    public static final String LOCAL_UPGRADE_FILE = "local_upgrade_file";

    // Download / Update status
    public static final int BATTERY_LOW = 300;
    public static final int CANCELLED = 301;
    public static final int FAILED = 302;
    public static final int INDETERMINATE = 303;
    public static final int DOWNLOADING = 304;
    public static final int UPDATING = 305;
    public static final int PAUSED = 306;
    public static final int FINISHED = 307;
    public static final int DOWNLOAD_PENDING = 308;
    public static final int UPDATE_PENDING = 309;
    public static final int REBOOT_PENDING = 310;

    // Update intent actions
    public static final String ACION_START_UPDATE = "com.arcana.updater.START_UPDATE";

    // 1 MB in bytes
    public static final int MB = 1048576;

    // Preferences
    public static final String THEME_KEY = "theme_settings_preference";
    public static final String REFRESH_INTERVAL_KEY = "refresh_interval_preference";
}
