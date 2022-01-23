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

package com.arcana.updater.model.data;

import static androidx.work.BackoffPolicy.LINEAR;
import static androidx.work.NetworkType.CONNECTED;
import static androidx.work.OneTimeWorkRequest.MIN_BACKOFF_MILLIS;
import static com.arcana.updater.util.Constants.DOWNLOADING;
import static com.arcana.updater.util.Constants.INDETERMINATE;
import static com.arcana.updater.util.Constants.PAUSED;
import static com.arcana.updater.util.Constants.CANCELLED;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.arcana.updater.model.data.BuildInfo;
import com.arcana.updater.model.data.DataStore;
import com.arcana.updater.workers.DownloadWorker;

import io.reactivex.rxjava3.subjects.PublishSubject;

import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DownloadManager {

    private final Constraints constraints;
    private final WorkManager workManager;
    private final DataStore dataStore;
    private final PublishSubject<UUID> uuidSubject;
    private UUID id;

    @Inject
    public DownloadManager(WorkManager workManager, DataStore dataStore) {
        this.workManager = workManager;
        this.dataStore = dataStore;
        constraints = new Constraints.Builder()
            .setRequiredNetworkType(CONNECTED)
            .setRequiresStorageNotLow(true)
            .build();
        uuidSubject = PublishSubject.create();
    }

    @WorkerThread
    public void start() {
        dataStore.deleteDownloadStatus();
        fetchAndEnqueueDownload();
    }

    @WorkerThread
    public void pauseOrResume() {
        if (id == null) {
            id = dataStore.getCurrentDownloadId();
        }
        if (id != null) {
            workManager.cancelWorkById(id);
            id = null;
            dataStore.updateDownloadStatus(PAUSED);
        } else {
            fetchAndEnqueueDownload();
        }
        dataStore.updateDownloadId(id);
    }

    @WorkerThread
    public void cancel() {
        if (id != null) {
            workManager.cancelWorkById(id);
            id = null;
            dataStore.updateDownloadId(null);
        }
        dataStore.updateDownloadStatus(CANCELLED);
    }

    @WorkerThread
    public boolean isDownloading() {
        final int status = dataStore.getDownloadStatusCode();
        return status >= INDETERMINATE && status <= PAUSED;
    }

    @WorkerThread
    public boolean isPaused() {
        return dataStore.getDownloadStatusCode() == PAUSED;
    }

    public PublishSubject<UUID> getUUIDSubject() {
        return uuidSubject;
    }

    private OneTimeWorkRequest buildRequest(BuildInfo buildInfo) {
        return new OneTimeWorkRequest.Builder(DownloadWorker.class)
            .setConstraints(constraints)
            .setInputData(new Data.Builder()
                .putString(BuildInfo.URL, buildInfo.getUrl())
                .putString(BuildInfo.FILE_NAME, buildInfo.getFileName())
                .putString(BuildInfo.MD5, buildInfo.getMd5())
                .putLong(BuildInfo.FILE_SIZE, buildInfo.getFileSize())
                .build())
            .setBackoffCriteria(LINEAR, MIN_BACKOFF_MILLIS, MILLISECONDS)
            .build();
    }

    private void fetchAndEnqueueDownload() {
        final BuildInfo buildInfo = dataStore.getBuildInfo();
        if (buildInfo.getFileName() == null) {
            return;
        }
        final OneTimeWorkRequest downloadRequest = buildRequest(buildInfo);
        id = downloadRequest.getId();
        workManager.enqueue(downloadRequest);
        uuidSubject.onNext(id);
        if (dataStore.getDownloadStatusCode() == 0) {
            dataStore.updateDownloadStatus(INDETERMINATE);
        }
    }
}
