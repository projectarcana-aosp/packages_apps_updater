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

package com.arcana.updater.model.data;;

import static android.os.Process.THREAD_PRIORITY_BACKGROUND;
import static android.os.UpdateEngine.ErrorCodeConstants.*;
import static android.os.UpdateEngine.UpdateStatusConstants.*;
import static com.arcana.updater.util.Constants.BATTERY_LOW;
import static com.arcana.updater.util.Constants.INDETERMINATE;
import static com.arcana.updater.util.Constants.UPDATE_PENDING;
import static com.arcana.updater.util.Constants.UPDATING;
import static com.arcana.updater.util.Constants.REBOOT_PENDING;
import static com.arcana.updater.util.Constants.FINISHED;
import static com.arcana.updater.util.Constants.PAUSED;
import static com.arcana.updater.util.Constants.CANCELLED;
import static com.arcana.updater.util.Constants.FAILED;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.ServiceSpecificException;
import android.os.UpdateEngine;
import android.os.UpdateEngineCallback;
import android.util.Log;

import androidx.annotation.WorkerThread;

import com.arcana.updater.model.data.DataStore;
import com.arcana.updater.R;
import com.arcana.updater.util.NotificationHelper;

import io.reactivex.rxjava3.processors.BehaviorProcessor;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UpdateManager {
    private static final String TAG = "UpdateManager";
    private final OTAFileManager ofm;
    private final UpdateEngine updateEngine;
    private final NotificationHelper helper;
    private final DataStore dataStore;
    private final BehaviorProcessor<UpdateStatus> updateStatusProcessor;
    private final BatteryMonitor batteryMonitor;
    private HandlerThread thread;
    private Handler bgHandler, mainHandler;
    private UpdateStatus updateStatus;
    private boolean updateQueued;
    private boolean isUpdating;

    private final UpdateEngineCallback updateEngineCallback = new UpdateEngineCallback() {
        @Override
        public void onStatusUpdate(int status, float percent) {
            if (status == DOWNLOADING || status == FINALIZING) {
                if (getCurrentStatusCode() != UPDATING) {
                    updateStatus.setStatusCode(UPDATING);
                }
                updateStatus.setStep(status == DOWNLOADING ? 1 : 2);
                updateStatusProcessor.onNext(updateStatus);
            }
            switch (status) {
                case IDLE:
                case CLEANUP_PREVIOUS_UPDATE:
                    // We don't have to update the ui for these
                    break;
                case UPDATE_AVAILABLE:
                    setGlobalStatus(UPDATING);
                    break;
                case DOWNLOADING:
                    isUpdating = true;
                case FINALIZING:
                    updateStatus.setProgress((int) (percent*100));
                    updateStatusProcessor.onNext(updateStatus);
                    break;
                case UPDATED_NEED_REBOOT:
                    isUpdating = false;
                    // Ready for reboot
                    setGlobalStatus(REBOOT_PENDING);
                    helper.onlyNotify(R.string.update_finished,
                        R.string.update_finished_notif_desc);
                    thread.quitSafely();
                    break;
                default:
                    // Log unhandled cases
                    Log.e(TAG, "onStatusUpdate: unknown status code " + status);
            }
        }

        @Override
        public void onPayloadApplicationComplete(int errorCode) {
            updateQueued = false;
            isUpdating = false;
            switch (errorCode) {
                case SUCCESS:
                    updateStatus.setStatusCode(FINISHED);
                    updateStatusProcessor.onNext(updateStatus);
                    break;
                case DOWNLOAD_INVALID_METADATA_MAGIC_STRING:
                case DOWNLOAD_METADATA_SIGNATURE_MISMATCH:
                    resetAndNotify(R.string.metadata_verification_failed);
                    break;
                case PAYLOAD_TIMESTAMP_ERROR:
                    resetAndNotify(R.string.attempting_downgrade);
                    break;
                case NEW_ROOTFS_VERIFICATION_ERROR:
                    resetAndNotify(R.string.rootfs_verification_failed);
                    break;
                case DOWNLOAD_TRANSFER_ERROR:
                    resetAndNotify(R.string.ota_transfer_error);
                    break;
                case USER_CANCELLED:
                    break;
                default:
                    // Log unhandled cases
                    Log.e(TAG, "onPayloadApplicationComplete: unknown errorCode " + errorCode);
            }
        }
    };

    @Inject
    public UpdateManager(UpdateEngine updateEngine, OTAFileManager ofm,
            NotificationHelper helper, DataStore dataStore,
            BatteryMonitor batteryMonitor) {
        this.updateEngine = updateEngine;
        this.ofm = ofm;
        this.helper = helper;
        this.dataStore = dataStore;
        this.batteryMonitor = batteryMonitor;
        thread = new HandlerThread(TAG, THREAD_PRIORITY_BACKGROUND);
        updateStatus = new UpdateStatus();
        updateStatusProcessor = BehaviorProcessor.create();
        updateEngineReset(); // Cancel any ongoing updates / unbind callbacks we are not aware of
        batteryMonitor.getBatteryOkayProcessor().subscribe(okay -> {
            if (!okay && isUpdating) {
                pause(true); // Pause any ongoing updates if battery is low & unplugged
                helper.notifyOrToast(R.string.battery_low,
                    R.string.plug_in_charger, mainHandler);
            }
        });
        mainHandler = new Handler(Looper.getMainLooper());
    }

    @WorkerThread
    public void start() {
        if (updateQueued) {
            return;
        }
        updateQueued = true;
        if (!batteryMonitor.isBatteryOkay()) {
            notifyBatteryIsLow();
            return;
        }
        reset(); // Reset update engine whenever a new update is applied
        if (thread.getState() == Thread.State.TERMINATED) {
            // Create a new thread if the current one is terminated
            thread = new HandlerThread(TAG, THREAD_PRIORITY_BACKGROUND);
        }
        thread.start();
        bgHandler = new Handler(thread.getLooper());
        updateEngine.setPerformanceMode(true);
        final PayloadInfo payloadInfo = PayloadInfoFactory.createPayloadInfo(
            ofm.getOTAFileUri());
        if (!payloadInfo.validate()) {
            resetAndNotify(R.string.invalid_zip_file);
            return;
        }
        updateStatus.setStatusCode(INDETERMINATE);
        updateStatusProcessor.onNext(updateStatus);
        updateEngine.bind(updateEngineCallback, bgHandler);
        try {
            updateEngine.applyPayload(payloadInfo.getFilePath(),
                payloadInfo.getOffset(), payloadInfo.getSize(),
                payloadInfo.getHeaderKeyValuePairs());
        } catch (ServiceSpecificException e) {
            Log.e(TAG, "ServiceSpecificException when applying payload", e);
            resetAndNotify(R.string.update_failed);
        }
    }

    public void pause(boolean pause) {
        try {
            if (pause) {
                updateEngine.suspend();
                updateStatus.setStatusCode(PAUSED);
                updateStatusProcessor.onNext(updateStatus);
            } else {
                if (!batteryMonitor.isBatteryOkay()) {
                    notifyBatteryIsLow();
                    return;
                }
                updateEngine.resume();
                updateStatus.setStatusCode(INDETERMINATE);
                updateStatusProcessor.onNext(updateStatus);
            }
        } catch (ServiceSpecificException e) {
            // No ongoing update to suspend or resume, there is no need to log this
        }
    }

    @WorkerThread
    public void cancel() {
        updateQueued = false;
        isUpdating = false;
        updateEngineReset();
        updateStatus.setStatusCode(CANCELLED);
        updateStatusProcessor.onNext(updateStatus);
        thread.quitSafely();
    }

    public BehaviorProcessor<UpdateStatus> getUpdateStatusProcessor() {
        return updateStatusProcessor;
    }

    public int getCurrentStatusCode() {
        return updateStatus.getStatusCode();
    }

    public boolean isUpdating() {
        return isUpdating;
    }

    public void userInitiatedReset() {
        reset();
        updateStatus = new UpdateStatus();
        updateStatusProcessor.onNext(updateStatus);
    }

    private void setGlobalStatus(int status) {
        bgHandler.post(() -> dataStore.setGlobalStatus(status));
    }

    private void updateEngineReset() {
        try {
            updateEngine.cancel();
        } catch (ServiceSpecificException e) {
            // No ongoing update to cancel, there is no need to log this
        } finally {
            // Reset, cleanup and unbind
            reset();
        }
    }

    private void reset() {
        updateEngine.cleanupAppliedPayload();
        updateEngine.resetStatus();
        updateEngine.unbind();
    }

    private void resetAndNotify(int msgId) {
        updateStatus.setStatusCode(FAILED);
        updateStatusProcessor.onNext(updateStatus);
        setGlobalStatus(UPDATE_PENDING);
        reset();
        helper.notifyOrToast(R.string.update_failed, msgId, mainHandler);
        thread.quitSafely();
    }

    private void notifyBatteryIsLow() {
        helper.notifyOrToast(R.string.battery_low,
            R.string.plug_in_charger, mainHandler);
        updateStatus.setStatusCode(BATTERY_LOW);
        updateStatusProcessor.onNext(updateStatus);
    }
}
