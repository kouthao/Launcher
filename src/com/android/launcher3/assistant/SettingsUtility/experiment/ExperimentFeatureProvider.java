/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.assistant.SettingsUtility.experiment;

import android.content.Context;

public class ExperimentFeatureProvider {

    public long getMaxSuggestionDisplayCount(Context context) {
        return 3;
    }

    public boolean isPredictiveRingerEnabled(Context context) { return false; }

    public boolean isPredictiveBluetoothDrivingEnabled(Context context) { return false; }

}
