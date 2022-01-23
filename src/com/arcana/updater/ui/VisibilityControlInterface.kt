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

package com.arcana.updater.ui

import android.view.View

interface VisibilityControlInterface {

    /* Set visibility to all views in @param views
     * View.VISIBLE or View.GONE based on @param visible
     */
    // TODO : remove this annotation once everything is in kotlin
    @JvmDefault
    fun setGroupVisibility(
        visible: Boolean,
        vararg views: View,
    ) {
        val flag = if (visible) View.VISIBLE else View.GONE
        views.forEach { it.setVisibility(flag) }
    }
}