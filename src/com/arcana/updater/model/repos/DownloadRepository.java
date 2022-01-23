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

package com.arcana.updater.model.repos;

import static androidx.work.BackoffPolicy.LINEAR;
import static androidx.work.NetworkType.CONNECTED;
import static androidx.work.OneTimeWorkRequest.MIN_BACKOFF_MILLIS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static com.arcana.updater.util.Constants.MB;
import static com.arcana.updater.util.Constants.DOWNLOADING;
import static com.arcana.updater.util.Constants.INDETERMINATE;
import static com.arcana.updater.util.Constants.PAUSED;
import static com.arcana.updater.util.Constants.CANCELLED;
import static com.arcana.updater.util.Constants.FAILED;
import static com.arcana.updater.util.Constants.FINISHED;
import static com.arcana.updater.util.Constants.DOWNLOAD_PENDING;

import android.content.Context;

import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkInfo.State;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.arcana.updater.model.data.BuildInfo;
import com.arcana.updater.model.data.DataStore;
import com.arcana.updater.model.data.DownloadStatus;
import com.arcana.updater.model.data.ProgressInfo;
import com.arcana.updater.R;
import com.arcana.updater.model.data.DownloadManager;
import com.arcana.updater.util.Utils;
import com.arcana.updater.workers.DownloadWorker;

import io.reactivex.rxjava3.processors.BehaviorProcessor;
import io.reactivex.rxjava3.subjects.PublishSubject;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DownloadRepository {
    private final Context context;
    private final ExecutorService executor;
    private final WorkManager workManager;
    private final DownloadManager downloadManager;
    private final DataStore dataStore;

    @Inject
    public DownloadRepository(Context context, DownloadManager downloadManager,
            WorkManager workManager, ExecutorService executor,
            DataStore dataStore) {
        this.context = context;
        this.downloadManager = downloadManager;
        this.executor = executor;
        this.workManager = workManager;
        this.dataStore = dataStore;
    }

    public void startDownload() {
        executor.execute(() -> {
            clearCache();
            downloadManager.start();
            dataStore.setGlobalStatus(DOWNLOADING);
        });
    }

    public void pauseDownload() {
        executor.execute(() -> downloadManager.pauseOrResume());
    }

    public void cancelDownload() {
        executor.execute(() -> {
            downloadManager.cancel();
            clearData();
        });
    }

    public BehaviorProcessor<DownloadStatus> getDownloadStatusProcessor() {
        return dataStore.getDownloadStatusProcessor();
    }

    public PublishSubject<UUID> getUUIDSubject() {
        return downloadManager.getUUIDSubject();
    }

    public WorkManager getWorkManager() {
        return workManager;
    }

    public ProgressInfo getProgressInfo(DownloadStatus downloadStatus) {
        String status = "";
        final int statusCode = downloadStatus.getStatusCode();
        switch (statusCode) {
            case INDETERMINATE:
                status = getString(R.string.waiting);
                break;
            case DOWNLOADING:
                status = getString(R.string.downloading);
                break;
            case PAUSED:
                status = getString(R.string.download_paused);
                break;
            case FINISHED:
                status = getString(R.string.download_finished);
                break;
            case FAILED:
                status = getString(R.string.download_failed);
                break;
        }
        final String extras = String.format("%d/%d MB", (int) (downloadStatus.getDownloadedSize() / MB),
                (int) downloadStatus.getFileSize() / MB);
        return new ProgressInfo(
            status,
            extras,
            statusCode == INDETERMINATE,
            downloadStatus.getProgress()
        );
    }

    public ProgressInfo getProgressInfo(State state) {
        switch (state) {
            case ENQUEUED:
                return new ProgressInfo(
                    getString(R.string.waiting),
                    null,
                    state == State.ENQUEUED,
                    0
                );
            case CANCELLED:
                executor.execute(() -> {
                    if (!downloadManager.isPaused()) {
                        clearData();
                    }
                });
        }
        return null;
    }

    private void clearData() {
        downloadManager.cancel();
        workManager.pruneWork();
        dataStore.setGlobalStatus(DOWNLOAD_PENDING);
        clearCache();
    }

    private void clearCache() {
        Arrays.stream(context.getExternalCacheDir()
            .listFiles()).forEach(file -> file.delete());
    }

    private String getString(int id) {
        return context.getString(id);
    }
}
