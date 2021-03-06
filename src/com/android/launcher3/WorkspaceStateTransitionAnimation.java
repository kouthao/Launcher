/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3;

import static ch.deletescape.lawnchair.views.LawnchairBackgroundView.ALPHA_INDEX_STATE;
import static com.android.launcher3.LauncherAnimUtils.DRAWABLE_ALPHA;
import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.HOTSEAT_ICONS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OPTIONS;
import static com.android.launcher3.LauncherState.OPTIONS_VIEW;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_WORKSPACE_FADE;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_WORKSPACE_SCALE;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.anim.Interpolators.ZOOM_OUT;
import static com.android.launcher3.anim.PropertySetter.NO_ANIM_PROPERTY_SETTER;
import static com.android.launcher3.graphics.WorkspaceAndHotseatScrim.SCRIM_PROGRESS;
import static com.android.launcher3.graphics.WorkspaceAndHotseatScrim.SYSUI_PROGRESS;

import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.TextView;
import ch.deletescape.lawnchair.LawnchairLauncher;
import ch.deletescape.lawnchair.views.OptionsPanel;
import com.android.launcher3.LauncherState.PageAlphaProvider;
import com.android.launcher3.LauncherStateManager.AnimationConfig;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.graphics.WorkspaceAndHotseatScrim;

/**
 * Manages the animations between each of the workspace states.
 */
public class WorkspaceStateTransitionAnimation {

    private final Launcher mLauncher;
    private final Workspace mWorkspace;

    private float mNewScale;

    public static final float HOTSEAT_MOVE_DISTANCE = 300;

    public WorkspaceStateTransitionAnimation(Launcher launcher, Workspace workspace) {
        mLauncher = launcher;
        mWorkspace = workspace;
    }

    public void setState(LauncherState toState) {
        setWorkspaceProperty(toState, NO_ANIM_PROPERTY_SETTER, new AnimatorSetBuilder(),
                new AnimationConfig());
    }

    public void setStateWithAnimation(LauncherState toState, AnimatorSetBuilder builder,
            AnimationConfig config) {
        setWorkspaceProperty(toState, config.getPropertySetter(builder), builder, config);
    }

    public float getFinalScale() {
        return mNewScale;
    }

    /**
     * Starts a transition animation for the workspace.
     */
    private void setWorkspaceProperty(LauncherState state, PropertySetter propertySetter,
            AnimatorSetBuilder builder, AnimationConfig config) {
        float[] scaleAndTranslation = state.getWorkspaceScaleAndTranslation(mLauncher);
        mNewScale = scaleAndTranslation[0];
        PageAlphaProvider pageAlphaProvider = state.getWorkspacePageAlphaProvider(mLauncher);
        final int childCount = mWorkspace.getChildCount();
        for (int i = 0; i < childCount; i++) {
            applyChildState(state, (CellLayout) mWorkspace.getChildAt(i), i, pageAlphaProvider,
                    propertySetter, builder, config);
        }

        int elements = state.getVisibleElements(mLauncher);
        Interpolator fadeInterpolator = builder.getInterpolator(ANIM_WORKSPACE_FADE,
                pageAlphaProvider.interpolator);
        boolean playAtomicComponent = config.playAtomicComponent();

        Interpolator translationInterpolator = !playAtomicComponent ? LINEAR : ZOOM_OUT;
//        propertySetter.setFloat(mWorkspace, View.TRANSLATION_X,
//                scaleAndTranslation[1], translationInterpolator);
//        propertySetter.setFloat(mWorkspace, View.TRANSLATION_Y,
//                scaleAndTranslation[2], translationInterpolator);

        if (playAtomicComponent) {
            //Interpolator scaleInterpolator = builder.getInterpolator(ANIM_WORKSPACE_SCALE, ZOOM_OUT);

            //use accelerate decelerate interpolator
            Interpolator scaleInterpolator = mNewScale == 1.0f? new AccelerateInterpolator(): new DecelerateInterpolator();

            propertySetter.setFloat(mWorkspace, SCALE_PROPERTY, mNewScale, scaleInterpolator);

            float hotseatIconsAlpha = (elements & HOTSEAT_ICONS) != 0 ? 1 : 0;
            propertySetter.setViewAlpha(mLauncher.getHotseat().getLayout(), hotseatIconsAlpha,
                    fadeInterpolator);

            View pageIndicator = mLauncher.getWorkspace().getPageIndicator();

            if(mWorkspace.getChildAt(0) != null) {
                pageIndicator.setPivotX(pageIndicator.getWidth() * 0.5f);
                pageIndicator.setPivotY(-(pageIndicator.getTop() - mWorkspace.getPivotY()
                        - mLauncher.getRootView().getInsets().bottom));
            }
            propertySetter.setFloat(pageIndicator, SCALE_PROPERTY, mNewScale, scaleInterpolator);

            TextView workspaceDragLabel = mLauncher.getWorkspaceDragLabel();

            View hotSeat = mLauncher.getHotseat();
            hotSeat.setPivotX(hotSeat.getWidth() * 0.5f);
            hotSeat.setPivotY(-(hotSeat.getTop() - mWorkspace.getPivotY()
                    - mLauncher.getRootView().getInsets().bottom));
            if((mLauncher.isInState(NORMAL) && state == ALL_APPS) ||
                    (mLauncher.isInState(ALL_APPS) && state == NORMAL)) {
                //Set the pivot position, and scale the hot seat
                propertySetter.setFloat(hotSeat, SCALE_PROPERTY, mNewScale, scaleInterpolator);
            }
            else if(mLauncher.isInState(NORMAL) && state == OPTIONS){
                float newValue = hotSeat.getTranslationY() + HOTSEAT_MOVE_DISTANCE;
                propertySetter.setFloat(hotSeat, View.TRANSLATION_Y, newValue, translationInterpolator);

                //Calculate drag label move distance

                float dragLabelPos = 50.0f;

                if(mWorkspace.getPageCount() > 0){
                    int cellTop = mWorkspace.getPageAt(0).getTop();
                    int cellTopAfterScale = (int)(mWorkspace.getPivotY() - (mWorkspace.getPivotY() - cellTop) * mNewScale);
                    int moveDistance = cellTopAfterScale - workspaceDragLabel.getBottom();

                    //Set padding
                    dragLabelPos = moveDistance - DeviceProfile.DROP_TARGET_PADDING;
                }

                propertySetter.setFloat(workspaceDragLabel, View.TRANSLATION_Y, dragLabelPos, translationInterpolator);
                propertySetter.setFloat(workspaceDragLabel, View.ALPHA, 1, fadeInterpolator);

                mLauncher.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
            else if(mLauncher.isInState(OPTIONS) && state == NORMAL){
                float newValue = hotSeat.getTranslationY() - HOTSEAT_MOVE_DISTANCE;
                propertySetter.setFloat(hotSeat, View.TRANSLATION_Y, newValue, translationInterpolator);
                propertySetter.setFloat(workspaceDragLabel, View.TRANSLATION_Y, -50, translationInterpolator);
                propertySetter.setFloat(workspaceDragLabel, View.ALPHA, 0, fadeInterpolator);

                mLauncher.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
            else if(mLauncher.isInState(ALL_APPS)){
                hotSeat.setScaleX(1);
                hotSeat.setScaleY(1);
            }
        }

        // Set options view
        OptionsPanel optionsPanel = LawnchairLauncher.getLauncher(mLauncher).getOptionsView();
        propertySetter.setViewAlpha(optionsPanel, (elements & OPTIONS_VIEW) != 0 ? 1 : 0, fadeInterpolator);

        if (!config.playNonAtomicComponent()) {
            // Only the alpha and scale, handled above, are included in the atomic animation.
            return;
        }

        // Set scrim
        WorkspaceAndHotseatScrim scrim = mLauncher.getDragLayer().getScrim();
        propertySetter.setFloat(scrim, SCRIM_PROGRESS, state.getWorkspaceScrimAlpha(mLauncher),
                LINEAR);
//        propertySetter.setFloat(scrim, SYSUI_PROGRESS, state.hasSysUiScrim ? 1 : 0, LINEAR);

        //Do not draw blur on home screen animation
        /*
        LawnchairBackgroundView background = LawnchairLauncher.getLauncher(mLauncher).getBackground();
        propertySetter.setFloat(background.getBlurAlphas().getProperty(ALPHA_INDEX_STATE),
                InvertedMultiValueAlpha.VALUE,
                state.getWorkspaceBlurAlpha(mLauncher),
                builder.getInterpolator(ANIM_BLUR_FADE, LINEAR));
        */
    }

    public void applyChildState(LauncherState state, CellLayout cl, int childIndex) {
        applyChildState(state, cl, childIndex, state.getWorkspacePageAlphaProvider(mLauncher),
                NO_ANIM_PROPERTY_SETTER, new AnimatorSetBuilder(), new AnimationConfig());
    }

    private void applyChildState(LauncherState state, CellLayout cl, int childIndex,
            PageAlphaProvider pageAlphaProvider, PropertySetter propertySetter,
            AnimatorSetBuilder builder, AnimationConfig config) {
        float pageAlpha = pageAlphaProvider.getPageAlpha(childIndex);
        int drawableAlpha = Math.round(pageAlpha * (state.hasWorkspacePageBackground ? 150 : 0));

        if (config.playNonAtomicComponent()) {
            propertySetter.setInt(cl.getScrimBackground(),
                    DRAWABLE_ALPHA, drawableAlpha, ZOOM_OUT);
        }
        if (config.playAtomicComponent()) {
            Interpolator fadeInterpolator = builder.getInterpolator(ANIM_WORKSPACE_FADE,
                    pageAlphaProvider.interpolator);
            propertySetter.setFloat(cl.getShortcutsAndWidgets(), View.ALPHA,
                    pageAlpha, fadeInterpolator);
        }
    }
}