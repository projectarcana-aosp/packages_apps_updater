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

package com.arcana.updater.ui.activity;

import static android.app.Activity.RESULT_OK;
import static android.content.DialogInterface.BUTTON_POSITIVE;
import static android.content.Intent.ACTION_OPEN_DOCUMENT;
import static android.content.Intent.CATEGORY_OPENABLE;
import static android.graphics.Color.TRANSPARENT;
import static android.os.UserHandle.SYSTEM;
import static android.provider.OpenableColumns.DISPLAY_NAME;
import static android.view.HapticFeedbackConstants.KEYBOARD_PRESS;
import static android.widget.Toast.LENGTH_SHORT;
import static com.arcana.updater.util.Constants.ACION_START_UPDATE;
import static com.arcana.updater.util.Constants.THEME_KEY;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.Group;
import androidx.lifecycle.ViewModelProvider;
import androidx.core.widget.NestedScrollView;

import com.android.internal.util.arcana.KryptonUtils;
import com.arcana.updater.model.data.BatteryMonitor;
import com.arcana.updater.model.data.BuildInfo;
import com.arcana.updater.model.data.Response;
import com.arcana.updater.model.data.ResponseCode;
import com.arcana.updater.R;
import com.arcana.updater.services.UpdateInstallerService;
import com.arcana.updater.ui.fragment.*;
import com.arcana.updater.ui.VisibilityControlInterface;
import com.arcana.updater.util.NotificationHelper;
import com.arcana.updater.util.Utils;
import com.arcana.updater.UpdaterApplication;
import com.arcana.updater.viewmodel.*;

import javax.inject.Inject;

public class UpdaterActivity extends AppCompatActivity implements VisibilityControlInterface {
    private static final String MAGISK_PACKAGE = "com.topjohnwu.magisk";
    private static final String MIME_TYPE_ZIP = "application/zip";
    private static final int SELECT_FILE = 1001;
    private Group latestBuildGroup, rebootButtonGroup;
    private TextView currentStatus, latestBuildVersion,
        latestBuildTimestamp, latestBuildName,
        latestBuildMd5, changelogText;
    private NestedScrollView changelogView;
    private TextView localUpgradeFileName;
    private Button refreshButton, downloadButton,
        localUpgradeButton, updateButton,
        magiskButton, rebootButton;
    private ProgressBar refreshProgress;
    private AppViewModel viewModel;
    private NotificationHelper notificationHelper;
    private BatteryMonitor batteryMonitor;
    private ViewModelProvider provider;
    private AlertDialog copyingDialog;

    @Inject
    void setDependencies(NotificationHelper helper, BatteryMonitor batteryMonitor) {
        notificationHelper = helper;
        this.batteryMonitor = batteryMonitor;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((UpdaterApplication) getApplication()).getComponent().inject(this);
        notificationHelper.removeAllNotifications(); // Clear all cancellable notifications
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.app_name);
        provider = new ViewModelProvider(this);
        viewModel = provider.get(AppViewModel.class);
        viewModel.resetStatusIfNotDone();
        viewModel.updateThemeFromDataStore();
        setContentView(R.layout.updater_activity);
        getSupportFragmentManager().beginTransaction()
            .setReorderingAllowed(true)
            .add(R.id.download_progress_container, DownloadProgressFragment.class, null)
            .add(R.id.update_progress_container, UpdateProgressFragment.class, null)
            .commit();
        setWidgets();
        setCurrentBuildInfo();
        registerObservers();
    }

    @Override
    public void onStart() {
        super.onStart();
        UpdaterApplication.setUIVisible(true);
    }

    @Override
    public void onStop() {
        super.onStop();
        UpdaterApplication.setUIVisible(false);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
            Intent resultData) {
        if (resultCode == RESULT_OK && resultData != null && requestCode == SELECT_FILE) {
            Uri fileUri = resultData.getData();
            if (fileUri != null) {
                Cursor cursor = getContentResolver().query(fileUri,
                    null, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    String fileName = cursor.getString(cursor.getColumnIndex(DISPLAY_NAME));
                    if (fileName != null) {
                        showConfirmDialog(R.string.confirm_selection,
                            fileName, (dialog, which) -> {
                                showCopyingDialog();
                                provider.get(UpdateViewModel.class).setupLocalUpgrade(fileName, fileUri);
                            });
                    }
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.settings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.option_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case R.id.option_reset:
                viewModel.reset();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showToast(int resId) {
        Toast.makeText(this, resId, LENGTH_SHORT).show();
    }

    private void showConfirmDialog(int titleId,
            String msg, OnClickListener listener) {
        final View view = getLayoutInflater().inflate(R.layout.dialog_layout, null);
        final TextView title = view.findViewById(R.id.title);
        title.setText(titleId);
        final TextView message = view.findViewById(R.id.message);
        message.setText(msg);
        new Builder(this, R.style.AlertDialogTheme)
            .setView(view)
            .setPositiveButton(android.R.string.yes, listener)
            .setNegativeButton(android.R.string.no, null)
            .show();
    }

    private void showCopyingDialog() {
        copyingDialog = new Builder(this, R.style.AlertDialogTheme)
            .setTitle(R.string.copying)
            .setMessage(R.string.do_not_close)
            .setCancelable(false)
            .setView(LayoutInflater.from(this).inflate(
                R.layout.copy_progress_bar, null, false))
            .show();
    }

    private void setWidgets() {
        refreshProgress = findViewById(R.id.refresh_progress);
        currentStatus = findViewById(R.id.current_status);

        latestBuildGroup = findViewById(R.id.latest_build_group);
        latestBuildVersion = findViewById(R.id.latest_build_version);
        latestBuildTimestamp = findViewById(R.id.latest_build_timestamp);
        latestBuildName = findViewById(R.id.latest_build_filename);
        latestBuildMd5 = findViewById(R.id.latest_build_md5);

        changelogView = findViewById(R.id.changelog);
        changelogText = findViewById(R.id.changelog_text);
        localUpgradeFileName = findViewById(R.id.local_upgrade_file_name);

        refreshButton = findViewById(R.id.refresh_button);
        downloadButton = findViewById(R.id.download_button);
        localUpgradeButton = findViewById(R.id.local_upgrade_button);
        updateButton = findViewById(R.id.update_button);

        rebootButtonGroup = findViewById(R.id.reboot_button_group);
        magiskButton = findViewById(R.id.magisk_button);
        rebootButton = findViewById(R.id.reboot_button);
    }

    private void setCurrentBuildInfo() {
        ((TextView) findViewById(R.id.device))
            .setText(getString(R.string.device, Utils.getDevice()));
        ((TextView) findViewById(R.id.current_version))
            .setText(getString(R.string.version, Utils.getVersion()));
        ((TextView) findViewById(R.id.current_timestamp))
            .setText(getString(R.string.date, Utils.formatDate(Utils.getBuildDate())));
    }

    public void setNewBuildInfo(BuildInfo buildInfo) {
        latestBuildVersion.setText(getString(R.string.version,
            buildInfo.getVersion()));
        latestBuildTimestamp.setText(getString(R.string.date,
            Utils.formatDate(buildInfo.getDate())));
        latestBuildName.setText(getString(R.string.file,
            buildInfo.getFileName()));
        latestBuildMd5.setText(getString(R.string.md5,
            buildInfo.getMd5()));
        setGroupVisibility(true, latestBuildGroup);
    }

    private String getString(int id, String str) {
        return String.format("%s: %s", getString(id), str);
    }

    public void setBuildFetchResult(int textId) {
        currentStatus.setText(getString(textId));
    }

    private void registerObservers() {
        viewModel.getOTAResponse().observe(this,
            response -> handleOTAResponse(response));
        viewModel.getChangelogResponse().observe(this,
            response -> handleChaneglogResponse(response));
        viewModel.getRefreshButtonVisibility().observe(this,
            visibility -> setGroupVisibility(visibility, refreshButton));
        viewModel.getLocalUpgradeButtonVisibility().observe(this,
            visibility -> setGroupVisibility(visibility, localUpgradeButton));
        viewModel.getDownloadButtonVisibility().observe(this,
            visibility -> setGroupVisibility(visibility, downloadButton));
        viewModel.getUpdateButtonVisibility().observe(this,
            visibility -> {
                setGroupVisibility(visibility, updateButton);
                if (visibility && copyingDialog != null &&
                        copyingDialog.isShowing()) {
                    copyingDialog.dismiss();
                }
            });
        viewModel.getRebootButtonVisibility().observe(this,
            visibility -> {
                setGroupVisibility(visibility, rebootButtonGroup);
                if (visibility) {
                    stopServiceAsUser(new Intent(this, UpdateInstallerService.class), SYSTEM);
                    setGroupVisibility(KryptonUtils.isPackageInstalled(this,
                        MAGISK_PACKAGE), magiskButton);
                }
            });
        viewModel.getLocalUpgradeFileName().observe(this,
            fileName -> {
                localUpgradeFileName.setText(fileName);
                setGroupVisibility(!fileName.isEmpty(), localUpgradeFileName);
                setGroupVisibility(fileName.isEmpty(), currentStatus);
            });
    }

    private void handleOTAResponse(Response response) {
        ResponseCode status = response.getStatus();
        refreshButton.setClickable(status != ResponseCode.FETCHING);
        switch (status) {
            case EMPTY_RESPONSE:
                setBuildFetchResult(R.string.hit_refresh);
                setGroupVisibility(false, latestBuildGroup);
                break;
            case FETCHING:
                setBuildFetchResult(R.string.fetching_build_status_text);
                setGroupVisibility(true, refreshProgress);
                break;
            case FAILED:
                setBuildFetchResult(R.string.unable_to_fetch_details);
                setGroupVisibility(false, refreshProgress);
                break;
            case UP_TO_DATE:
                setBuildFetchResult(R.string.current_is_latest);
                setGroupVisibility(false, refreshProgress);
                break;
            case NEW_DATA:
                viewModel.fetchChangelog();
                setBuildFetchResult(R.string.new_update);
                setGroupVisibility(false, refreshProgress);
                setNewBuildInfo((BuildInfo) response.getBody());
                break;
        }
    }

    private void handleChaneglogResponse(Response response) {
        switch (response.getStatus()) {
            case EMPTY_RESPONSE:
                setGroupVisibility(false, changelogView);
                changelogText.setText(null);
                break;
            case FETCHING:
                setGroupVisibility(true, changelogView, refreshProgress);
                changelogText.setText(R.string.fetching_changelog);
                break;
            case FAILED:
                setGroupVisibility(false, refreshProgress);
                changelogText.setText(R.string.unable_to_fetch_changelog);
                break;
            case DATA_UNAVAILABLE:
                setGroupVisibility(false, refreshProgress);
                changelogText.setText(R.string.changelog_unavailable);
                break;
            case NEW_DATA:
                setGroupVisibility(false, refreshProgress);
                changelogText.setText((SpannableStringBuilder) response.getBody(),
                    TextView.BufferType.SPANNABLE);
        }
    }

    public void refreshStatus(View v) {
        v.performHapticFeedback(KEYBOARD_PRESS);
        viewModel.fetchBuildInfo();
    }

    public void startDownload(View v) {
        v.performHapticFeedback(KEYBOARD_PRESS);
        provider.get(DownloadViewModel.class).startDownload();
    }

    public void localUpgrade(View v) {
        v.performHapticFeedback(KEYBOARD_PRESS);
        Intent intent = new Intent(ACTION_OPEN_DOCUMENT);
        intent.addCategory(CATEGORY_OPENABLE);
        intent.setType(MIME_TYPE_ZIP);
        startActivityForResult(intent, SELECT_FILE);
    }

    public void startUpdate(View v) {
        v.performHapticFeedback(KEYBOARD_PRESS);
        if (!batteryMonitor.isBatteryOkay()) {
            Toast.makeText(this, R.string.plug_in_charger, Toast.LENGTH_LONG).show();
            return;
        }
        final Intent intent = new Intent(this, UpdateInstallerService.class);
        intent.setAction(ACION_START_UPDATE);
        startServiceAsUser(intent, SYSTEM);
    }

    public void openMagisk(View v) {
        v.performHapticFeedback(KEYBOARD_PRESS);
        final Intent intent = getPackageManager().getLaunchIntentForPackage(MAGISK_PACKAGE);
        if (intent != null) {
            startActivity(intent);
        } else {
            Toast.makeText(this, R.string.magisk_not_installed, LENGTH_SHORT).show();
        }
    }

    public void rebootSystem(View v) {
        v.performHapticFeedback(KEYBOARD_PRESS);
        showConfirmDialog(R.string.sure_to_reboot,
            null, (dialog, which) -> viewModel.initiateReboot());
    }
}
