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

package com.android.launcher3.assistant.SettingsUtility.overlay;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import com.android.launcher3.R;
import com.android.launcher3.assistant.SettingsUtility.experiment.ExperimentFeatureProvider;
import com.android.launcher3.assistant.SettingsUtility.instrumentation.MetricsFeatureProvider;
import com.android.launcher3.assistant.SettingsUtility.search.SearchFeatureProvider;
import com.android.launcher3.assistant.SettingsUtility.suggestions.SuggestionFeatureProvider;

public abstract class FeatureFactory {

    private static final String LOG_TAG = "FeatureFactory";
    private static final boolean DEBUG = false;

    protected static FeatureFactory sFactory;

    /**
     * Returns a factory for creating feature controllers. Creates the factory if it does not
     * already exist. Uses the value of {@link R.string#config_featureFactory} to instantiate
     * a factory implementation.
     */
    public static FeatureFactory get(Context context) {
        if (sFactory != null) {
            return sFactory;
        }

        if (DEBUG) {
            Log.d(LOG_TAG, "getFactory");
        }
        final String clsName = context.getString(R.string.config_featureFactory);
        if (TextUtils.isEmpty(clsName)) {
            throw new UnsupportedOperationException("No feature factory configured");
        }
        try {
            sFactory = (FeatureFactory) context.getClassLoader().loadClass(clsName).newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new FactoryNotFoundException(e);
        }

        if (DEBUG) {
            Log.d(LOG_TAG, "started " + sFactory.getClass().getSimpleName());
        }
        return sFactory;
    }

    public abstract MetricsFeatureProvider metricsFeatureProvider(Context context);

    public abstract SuggestionFeatureProvider suggestionFeatureProvider();

    public abstract ExperimentFeatureProvider experimentFeatureProvider();

    public abstract SearchFeatureProvider searchFeatureProvider();

    public static final class FactoryNotFoundException extends RuntimeException {
        public FactoryNotFoundException(Throwable throwable) {
            super("Unable to create factory. Did you misconfigure Proguard?", throwable);
        }
    }

}
