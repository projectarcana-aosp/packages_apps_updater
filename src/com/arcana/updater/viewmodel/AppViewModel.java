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

import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
import static com.arcana.updater.util.Constants.DOWNLOAD_PENDING;
import static com.arcana.updater.util.Constants.UPDATE_PENDING;
import static com.arcana.updater.util.Constants.REBOOT_PENDING;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.LiveDataReactiveStreams;
import androidx.lifecycle.MutableLiveData;

import com.arcana.updater.model.data.Response;
import com.arcana.updater.model.repos.AppRepository;
import com.arcana.updater.UpdaterApplication;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

public class AppViewModel extends AndroidViewModel {

    private final AppRepository repository;
    private Disposable disposable;
    private MutableLiveData<Boolean> refreshButtonVisibility, localUpgradeButtonVisibility,
        downloadButtonVisibility, updateButtonVisibility, rebootButtonVisibility;
    private LiveData<String> localUpgradeFileName;
    private LiveData<Response> otaResponse, changelogResponse;

    public AppViewModel(Application application) {
        super(application);
        repository = ((UpdaterApplication) application)
            .getComponent().getAppRepository();
        refreshButtonVisibility = new MutableLiveData<>();
        localUpgradeButtonVisibility = new MutableLiveData<>();
        downloadButtonVisibility = new MutableLiveData<>();
        updateButtonVisibility = new MutableLiveData<>();
        rebootButtonVisibility = new MutableLiveData<>();
        localUpgradeFileName = new MutableLiveData<>();
        observe();
    }

    @Override
    public void onCleared() {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    public LiveData<Response> getOTAResponse() {
        if (otaResponse == null) {
            otaResponse = LiveDataReactiveStreams.fromPublisher(
                repository.getOTAResponsePublisher());
        }
        return otaResponse;
    }

    public LiveData<Response> getChangelogResponse() {
        if (changelogResponse == null) {
            changelogResponse = LiveDataReactiveStreams.fromPublisher(
                repository.getChangelogResponsePublisher());
        }
        return changelogResponse;
    }

    public void fetchBuildInfo() {
        repository.fetchBuildInfo();
    }

    public void fetchChangelog() {
        repository.fetchChangelog();
    }

    public void initiateReboot() {
        repository.resetStatusAndReboot();
    }

    public int getAppThemeMode() {
        return repository.getAppThemeMode();
    }

    public int getRefreshInterval() {
        return repository.getRefreshInterval();
    }

    public void updateRefreshInterval(int days) {
        repository.updateRefreshInterval(days);
    }

    public void updateThemeFromDataStore() {
        switchThemeMode(getAppThemeMode());
    }

    public void updateThemeMode(int mode) {
        repository.updateThemeInDataStore(mode);
        switchThemeMode(mode);
    }

    private void switchThemeMode(int mode) {
        switch (mode) {
            case 0:
                AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_NO);
                break;
            case 1:
                AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_YES);
                break;
            case 2:
                AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    public LiveData<Boolean> getRefreshButtonVisibility() {
        return refreshButtonVisibility;
    }

    public LiveData<Boolean> getLocalUpgradeButtonVisibility() {
        return localUpgradeButtonVisibility;
    }

    public LiveData<Boolean> getDownloadButtonVisibility() {
        return downloadButtonVisibility;
    }

    public LiveData<Boolean> getUpdateButtonVisibility() {
        return updateButtonVisibility;
    }

    public LiveData<Boolean> getRebootButtonVisibility() {
        return rebootButtonVisibility;
    }

    public LiveData<String> getLocalUpgradeFileName() {
        return localUpgradeFileName;
    }

    public void reset() {
        repository.resetStatus();
    }

    public void resetStatusIfNotDone() {
        repository.resetStatusIfNotDone();
    }

    private void observe() {
        disposable = repository.getGlobalStatusProcessor()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(status -> {
                final boolean statusUnknown = status == 0;
                refreshButtonVisibility.setValue(statusUnknown);
                localUpgradeButtonVisibility.setValue(statusUnknown);
                downloadButtonVisibility.setValue(status == DOWNLOAD_PENDING);
                updateButtonVisibility.setValue(status == UPDATE_PENDING);
                rebootButtonVisibility.setValue(status == REBOOT_PENDING);
            });
        localUpgradeFileName = LiveDataReactiveStreams.fromPublisher(
            repository.getLocalUpgradeFileProcessor());
    }
}
