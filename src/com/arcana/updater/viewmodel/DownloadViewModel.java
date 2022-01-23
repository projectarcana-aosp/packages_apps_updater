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

package com.arcana.updater.viewmodel;

import static com.arcana.updater.util.Constants.INDETERMINATE;
import static com.arcana.updater.util.Constants.PAUSED;
import static com.arcana.updater.util.Constants.CANCELLED;
import static com.arcana.updater.util.Constants.FAILED;
import static com.arcana.updater.util.Constants.FINISHED;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.LiveDataReactiveStreams;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.work.WorkInfo;
import androidx.work.WorkInfo.State;
import androidx.work.WorkManager;

import com.arcana.updater.model.data.ProgressInfo;
import com.arcana.updater.model.repos.DownloadRepository;
import com.arcana.updater.UpdaterApplication;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

import javax.inject.Inject;

public class DownloadViewModel extends AndroidViewModel {
    private final MediatorLiveData<ProgressInfo> progressInfo;
    private final MutableLiveData<Boolean> viewVisibility, controlVisibility, pauseStatus;
    private DownloadRepository repository;
    private WorkManager workManager;
    private Disposable disposable;
    private boolean paused;

    @Inject
    public void setDependencies(DownloadRepository repository, WorkManager workManager) {
        this.repository = repository;
        this.workManager = workManager;
    }

    public DownloadViewModel(Application application) {
        super(application);
        ((UpdaterApplication) application).getComponent().inject(this);
        progressInfo = new MediatorLiveData<>();
        viewVisibility = new MutableLiveData<>(false);
        controlVisibility = new MutableLiveData<>(true);
        pauseStatus = new MutableLiveData<>(false);
        observeProgress();
    }

    @Override
    public void onCleared() {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    public LiveData<ProgressInfo> getDownloadProgress() {
        return progressInfo;
    }

    public LiveData<Boolean> pauseButtonStatus() {
        return pauseStatus;
    }

    public LiveData<Boolean> getViewVisibility() {
        return viewVisibility;
    }

    public LiveData<Boolean> getControlVisibility() {
        return controlVisibility;
    }

    public void startDownload() {
        repository.startDownload();
    }

    public void pauseDownload() {
        paused = !paused;
        pauseStatus.setValue(paused);
        repository.pauseDownload();
    }

    public void cancelDownload() {
        repository.cancelDownload();
    }

    private void observeProgress() {
        disposable = repository.getUUIDSubject()
            .distinctUntilChanged()
            .filter(id -> id != null)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(id -> progressInfo.addSource(
                workManager.getWorkInfoByIdLiveData(id), workInfo -> {
                    if (workInfo == null) {
                        return;
                    }
                    final State state = workInfo.getState();
                    if (state == State.CANCELLED && !paused) {
                        viewVisibility.postValue(false);
                    }
                    ProgressInfo info = repository.getProgressInfo(state);
                    if (info != null) {
                        progressInfo.postValue(info);
                    }
                })
            );
        progressInfo.addSource(LiveDataReactiveStreams.fromPublisher(
            repository.getDownloadStatusProcessor()),
                downloadStatus -> {
                    final int statusCode = downloadStatus.getStatusCode();
                    pauseStatus.setValue(statusCode == PAUSED);
                    if (viewVisibility.getValue()) {
                        if (statusCode == 0 || statusCode == CANCELLED) {
                            viewVisibility.postValue(false);
                        }
                    } else {
                        if (statusCode >= INDETERMINATE) {
                            viewVisibility.postValue(true);
                        }
                    }
                    if (controlVisibility.getValue()) {
                        if (statusCode < INDETERMINATE || statusCode > PAUSED) {
                            controlVisibility.postValue(false);
                        }
                    } else {
                        if (statusCode >= INDETERMINATE && statusCode <= PAUSED) {
                            controlVisibility.postValue(true);
                        }
                    }
                    progressInfo.postValue(repository.getProgressInfo(downloadStatus));
                });
    }
}
