/*
 *     Copyright (C) 2020 Lawnchair Team.
 *
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.android.launcher3.transition;

import static com.android.launcher3.transition.TransitionUtility.TRANSITION_DEFAULT;
import static com.android.launcher3.transition.TransitionUtility.TRANSITION_PERSPECTIVE;
import static com.android.launcher3.transition.TransitionUtility.TRANSITION_SQUEEZE;
import static com.android.launcher3.transition.TransitionUtility.TRANSITION_CUBE;
import static com.android.launcher3.transition.TransitionUtility.TRANSITION_FLIP_OVER;
import static com.android.launcher3.transition.TransitionUtility.TRANSITION_ROTATE;
import static com.android.launcher3.transition.TransitionUtility.TRANSITION_CASCADE;
import static com.android.launcher3.transition.TransitionUtility.TRANSITION_WINDMILL;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import ch.deletescape.lawnchair.views.OptionsPanel;
import ch.deletescape.lawnchair.views.OptionsTextView;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.Workspace;

/**
 * Home????????? ?????? ???????????? `??????`????????? ???????????? ???????????? layout
 *
 * @see TransitionLayout#show
 * @see R.layout#transition_layout
 */
public class TransitionLayout extends AbstractFloatingView implements View.OnClickListener {

    //Animation??? ??????????????? ????????? ????????????
    private static final float ANIMATE_START_SCALE = 1.2f;      //?????? scale(???????????? ????????? ????????????.)
    private static final float ANIMATE_START_ALPHA = 0.3f;      //?????? alpha(???????????? ????????? ????????????.)
    private static final int APPEAR_ANIMATION_DURATION = 200;   //Layout??? ???????????? Animation??? ????????????

    /*
    * ???????????? Animation(??????????????? ?????? ???????????????)
    * ??? ????????? ????????????.
    * ??????????????? ????????? ?????? ??? ????????? ??????.
    * ?????? ?????? ????????? ??????????????????????????? ???????????? ????????? ?????? ??? ????????? ??????
    */
    private static final int PREVIEW_ANIMATION_DURATION = 300;  //Animation??? ????????????
    private static final int PREVIEW_DELAY_ON_HALF = 100;       //Animation??? ????????????????????? ????????????(??????/?????????????????? ????????? ???????????????)

    Launcher mLauncher;
    Workspace mWorkspace;
    LinearLayout mScrollLayout; //???????????? ???????????????????????? ?????? scroll?????? ?????? layout
    CustomizedHorizontalScrollView mScrollView; //mScrollLayout??? ??????(Custom??? ScrollView ?????????)

    int mItemSelectedColor;     //????????? ?????????????????? ??????(?????????, label)
    int mItemUnSelectedColor;   //????????? ????????????????????? ??????
    int mIconSize;              //???????????????

    //?????????????????????
    OptionsTextView mDefault;       //??????(Slide)
    OptionsTextView mPerspective;   //??????
    OptionsTextView mSqueeze;       //??????
    OptionsTextView mCube;          //??????
    OptionsTextView mFlipOver;      //?????????
    OptionsTextView mRotate;        //??????
    OptionsTextView mCascade;       //??????
    OptionsTextView mWindMill;      //??????

    //????????? ??????????????????
    OptionsTextView mSelectedTransitionItem = null;

    //???????????? animation??? ???????????? 2?????? ?????? View??? (CellLayout)
    CellLayout mWorkspaceFirstChild;
    CellLayout mWorkspaceSecondChild;

    ShortcutAndWidgetContainer mAnimateFirstView;
    ShortcutAndWidgetContainer mAnimateSecondView;

    //2?????? animator??? ???????????????.(?????????, ?????? ??????)
    AnimatorSet mFirstAnimatorSetForward = null;    //???????????? - ??? ??????
    AnimatorSet mSecondAnimatorSetForward = null;   //???????????? - ?????? ??????
    AnimatorSet mFirstAnimatorSetBack = null;       //???????????? - ??? ??????
    AnimatorSet mSecondAnimatorSetBack = null;      //???????????? - ?????? ??????
    boolean mPlayBackAnimationStartDelaying = false;    //?????????????????? animation??? ????????? ??????????????? true??? ????????? (PREVIEW_DELAY_ON_HALF)

    //Animation resource?????? ???????????? ??????
    TransitionManager mTransitionManager;

    public TransitionLayout(Context context) {
        this(context, null);
    }

    public TransitionLayout(Context context,
            @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TransitionLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = Launcher.getLauncher(context);
        mWorkspace = mLauncher.getWorkspace();
        mTransitionManager = new TransitionManager(context);

        //?????????/???????????? ????????? ???????????? ????????????.
        mItemSelectedColor = mLauncher.getColor(R.color.transition_selected_color);
        mItemUnSelectedColor = mLauncher.getColor(R.color.transition_unselected_color);

        //?????????????????? ????????????.
        mIconSize = (int)getResources().getDimension(R.dimen.options_view_icon_size);
    }

    @Override
    protected void onFinishInflate(){
        super.onFinishInflate();

        //?????? View??? ??????
        mScrollLayout = findViewById(R.id.scroll_layout);
        mScrollView = findViewById(R.id.transition_scroll_view);

        int childWidth = mLauncher.getDragLayer().getWidth() / 4;
        for (int i = 0; i < mScrollLayout.getChildCount(); i ++){
            if(mScrollLayout.getChildAt(i) instanceof OptionsTextView){
                OptionsTextView optionsChild = (OptionsTextView) mScrollLayout.getChildAt(i);

                Rect bound = optionsChild.getCompoundDrawables()[1].getBounds();
                int start = (bound.width() - mIconSize)/2;
                bound.set(start, start, start + mIconSize, start + mIconSize);

                optionsChild.getCompoundDrawables()[1].setBounds(bound);

                optionsChild.setWidth(childWidth);
                optionsChild.setOnClickListener(this);
            }
        }

        //?????????????????????
        mDefault = findViewById(R.id.transition_default_button);
        mPerspective = findViewById(R.id.transition_perspective_button);
        mSqueeze = findViewById(R.id.transition_squeeze_button);
        mCube = findViewById(R.id.transition_cube_button);
        mFlipOver = findViewById(R.id.transition_flip_over_button);
        mRotate = findViewById(R.id.transition_rotate_button);
        mCascade = findViewById(R.id.transition_cascade_button);
        mWindMill = findViewById(R.id.transition_windmill_button);

        //Preference??? ????????? ??????????????? ?????????.
        int transitionPref = TransitionUtility.getTransitionPref(getContext());

        //??????????????? ?????? ????????? ??????, ????????? ????????????.
        final int scrollViewPage;
        if(transitionPref == TRANSITION_DEFAULT) {
            mSelectedTransitionItem = mDefault;
            scrollViewPage = 0;
        }
        else if(transitionPref == TRANSITION_PERSPECTIVE) {
            mSelectedTransitionItem = mPerspective;
            scrollViewPage = 0;
        }
        else if(transitionPref == TRANSITION_SQUEEZE) {
            mSelectedTransitionItem = mSqueeze;
            scrollViewPage = 0;
        }
        else if(transitionPref == TRANSITION_CUBE) {
            mSelectedTransitionItem = mCube;
            scrollViewPage = 0;
        }
        else if(transitionPref == TRANSITION_FLIP_OVER) {
            mSelectedTransitionItem = mFlipOver;
            scrollViewPage = 1;
        }
        else if(transitionPref == TRANSITION_ROTATE) {
            mSelectedTransitionItem = mRotate;
            scrollViewPage = 1;
        }
        else if(transitionPref == TRANSITION_CASCADE) {
            mSelectedTransitionItem = mCascade;
            scrollViewPage = 1;
        }
        else if(transitionPref == TRANSITION_WINDMILL) {
            mSelectedTransitionItem = mWindMill;
            scrollViewPage = 1;
        }
        else{
            scrollViewPage = 2;
        }

        //?????? ????????????????????? ????????? ????????????.
        setTextAndDrawableColor(mSelectedTransitionItem, true);

        final TransitionLayout animateTransitionView = this;
        final OptionsPanel animateOptionsView = (OptionsPanel)mLauncher.getOptionsView();
        final ViewTreeObserver observer = getViewTreeObserver();

        observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mScrollView.scrollTo(scrollViewPage * mScrollView.getWidth(), 0);
                getViewTreeObserver().removeOnGlobalLayoutListener(this);

                //Transition Layout??? ??????????????? ????????? ????????? animation
                ObjectAnimator firstAnimator = ObjectAnimator.ofFloat(animateTransitionView, "scaleAlpha", 0, 1);
                firstAnimator.setDuration(APPEAR_ANIMATION_DURATION);
                firstAnimator.start();

                //Options Panel(????????????, ??????, ??????, ??? ??????)??? ????????? ???????????? animation
                ObjectAnimator secondAnimator = ObjectAnimator.ofFloat(animateOptionsView, "alpha", 1, 0f);
                secondAnimator.setDuration(APPEAR_ANIMATION_DURATION);
                secondAnimator.start();
            }
        });
    }

    /**
     * ????????????????????? ????????? ????????????.(?????????, ?????????)
     * @param textView ??????
     * @param selected ????????? ??????????????????, ??????????????????????
     */
    private void setTextAndDrawableColor(TextView textView, boolean selected){
        int color = selected? mItemSelectedColor : mItemUnSelectedColor;
        textView.setTextColor(color);

        int resource = 0;
        if(textView == mDefault) {              //??????
            resource = selected ? R.drawable.launcher_edit_transition_defult_current
                    : R.drawable.launcher_edit_transition_defult_pressed;
        } else if(textView == mPerspective) {   //??????
            resource = selected ? R.drawable.launcher_edit_transition_perspective_current
                    : R.drawable.launcher_edit_transition_perspective_pressed;
        } else if(textView == mSqueeze) {       //??????
            resource = selected ? R.drawable.launcher_edit_transition_squeeze_current
                    : R.drawable.launcher_edit_transition_squeeze_pressed;
        } else if(textView == mCube) {          //??????
            resource = selected ? R.drawable.launcher_edit_transition_box_current
                    : R.drawable.launcher_edit_transition_box_pressed;
        } else if(textView == mFlipOver) {      //?????????
            resource = selected ? R.drawable.launcher_edit_transition_filpover_current
                    : R.drawable.launcher_edit_transition_filpover_pressed;
        } else if(textView == mRotate) {        //??????
            resource = selected ? R.drawable.launcher_edit_transition_rotate_current
                    : R.drawable.launcher_edit_transition_rotate_pressed;
        } else if(textView == mCascade) {       //??????
            resource = selected ? R.drawable.launcher_edit_transition_cascade_current
                    : R.drawable.launcher_edit_transition_cascade_pressed;
        } else if(textView == mWindMill) {      //??????
            resource = selected ? R.drawable.launcher_edit_transition_windmill_current
                    : R.drawable.launcher_edit_transition_windmill_pressed;
        }

        //TextView??? Drawable??? ????????????.
        textView.setCompoundDrawablesWithIntrinsicBounds(0, resource, 0, 0);
        Drawable drawable = getResources().getDrawable(resource, null);
        int start = (drawable.getIntrinsicWidth() - mIconSize)/2;
        Rect bound = new Rect(start, start, start + mIconSize, start + mIconSize);
        textView.getCompoundDrawables()[1].setBounds(bound);
    }

    /**
     * Layout??? ?????????.
     * @param animate Animation??? ?????????????
     */
    @Override
    public void handleClose(boolean animate) {
        mIsOpen = false;
        OptionsPanel optionsView = (OptionsPanel) mLauncher.getOptionsView();

        if(animate){
            //Animation ??????
            final TransitionLayout transitionView = this;

            //TransitionLayout??? ??????????????? ????????? ???????????? animation
            ObjectAnimator firstAnimator = ObjectAnimator.ofFloat(transitionView, "scaleAlpha", 1, 0);
            firstAnimator.addListener(new AnimatorListener(){
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLauncher.getDragLayer().removeView(transitionView);
                }

                @Override
                public void onAnimationStart(Animator animation) { }
                @Override
                public void onAnimationCancel(Animator animation) { }
                @Override
                public void onAnimationRepeat(Animator animation) { }
            });
            firstAnimator.setDuration(APPEAR_ANIMATION_DURATION);
            firstAnimator.start();

            //OptionsView??? ????????? ???????????? animation
            ObjectAnimator secondAnimator = ObjectAnimator.ofFloat(optionsView, "alpha", 1.0f);
            secondAnimator.setDuration(APPEAR_ANIMATION_DURATION);
            secondAnimator.start();
        }
        else{
            //DragLayer?????? view??? ????????????.
            mLauncher.getDragLayer().removeView(this);
        }
    }

    @Override
    public void logActionCommand(int command) {
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_TRANSITION_BOTTOM_SHEET) != 0;
    }

    /**
     * `??????`????????? ???????????? ????????????..
     * Transition Layout??? animation??? ?????? ???????????????.
     *
     * @param launcher Launcher
     * @return ????????? layout??? ????????????.
     */
    public static TransitionLayout show(Launcher launcher) {
        //layout??? inflate??????.
        TransitionLayout sheet = (TransitionLayout) launcher.getLayoutInflater()
                .inflate(R.layout.transition_layout, launcher.getDragLayer(), false);
        sheet.mIsOpen = true;

        //Drag Layer??? layout??? ????????????.
        launcher.getDragLayer().addView(sheet);

        //????????? layout??? ????????????.
        return sheet;
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        return false;
    }

    /**
     * Animation????????????
     * Scale, Alpha?????? ????????????.
     *
     * @param value ?????????
     */
    @Keep
    public void setScaleAlpha(float value) {
        //Scale
        float scaleValue = ANIMATE_START_SCALE - value*(ANIMATE_START_SCALE - 1);
        setScaleX(scaleValue);
        setScaleY(scaleValue);

        //Alpha
        float alphaValue = ANIMATE_START_ALPHA + value*(1 - ANIMATE_START_ALPHA);
        setAlpha(alphaValue);
    }

    @Override
    public void onClick(View v) {
        if(v instanceof OptionsTextView) {
            //???????????? animation??? ?????? ??????????????? ????????? ?????????.
            if(isPreviewAnimationRunning())
                return;

            //????????? ??????, ?????? ??????????????? ???????????? ????????? ????????????.
            if(mSelectedTransitionItem != null && mSelectedTransitionItem != v){
                setTextAndDrawableColor(mSelectedTransitionItem, false);
            }
            setTextAndDrawableColor((OptionsTextView)v, true);
            mSelectedTransitionItem = (OptionsTextView)v;

            final int transitionValue;  //????????????
            if(v == mDefault)
                transitionValue = TRANSITION_DEFAULT;
            else if(v == mPerspective)
                transitionValue = TRANSITION_PERSPECTIVE;
            else if(v == mSqueeze)
                transitionValue = TRANSITION_SQUEEZE;
            else if(v == mCube)
                transitionValue = TRANSITION_CUBE;
            else if(v == mFlipOver)
                transitionValue = TRANSITION_FLIP_OVER;
            else if(v == mRotate)
                transitionValue = TRANSITION_ROTATE;
            else if(v == mCascade)
                transitionValue = TRANSITION_CASCADE;
            else
                transitionValue = TRANSITION_WINDMILL;

            //??????????????? Preference ??? ????????????.
            TransitionUtility.setTransitionPref(getContext(), transitionValue);

            //??????????????? ???????????? ?????? animation xml ???????????? ???????????????.
            mTransitionManager.loadTransitionPrefAndResource();
            mLauncher.getWorkspace().reloadTransitionPref();

            //???????????? animation??? ????????????.
            playPreviewAnimation();
        }
    }

    /**
     * ???????????? Animation ??? ???????????? ?????? View???(?????????, ????????? ??????)??? ?????????,
     * @return ????????? ????????? ???????????? ???????????? true??? ????????????.(Home????????? ?????? ????????? ???????????? ???????????? ????????????.)
     */
    public boolean getChildrenForPreview() {
        return mWorkspace.getChildrenForPreview(this);
    }

    /**
     * ????????? ?????? ??????
     * @param view CellLayout
     */
    public void setFirstChild(CellLayout view) {
        mWorkspaceFirstChild = view;
    }

    /**
     * ????????? ????????????
     * @param view CellLayout
     */
    public void setSecondChild(CellLayout view) {
        mWorkspaceSecondChild = view;
    }

    /**
     * ???????????? animation ??????
     */
    private void playPreviewAnimation(){
        //Animation??????????????? ????????????(??????, ??????)?????? ?????????.
        boolean isLastPageAndEmpty = getChildrenForPreview();
        mWorkspace.hideNeighbors();

        //Animator???????????? ?????????
        if(mFirstAnimatorSetForward != null) {
            mFirstAnimatorSetForward.removeAllListeners();
            mFirstAnimatorSetForward = null;
        }
        if(mSecondAnimatorSetForward != null) {
            mSecondAnimatorSetForward.removeAllListeners();
            mSecondAnimatorSetForward = null;
        }
        if(mFirstAnimatorSetBack != null) {
            mFirstAnimatorSetBack.removeAllListeners();
            mFirstAnimatorSetBack = null;
        }
        if(mSecondAnimatorSetBack != null) {
            mSecondAnimatorSetBack.removeAllListeners();
            mSecondAnimatorSetBack = null;
        }

        //Animation resource?????? ?????????.
        int transition_left_in = mTransitionManager.getAnimationLeftIn();
        int transition_left_out = mTransitionManager.getAnimationLeftOut();
        int transition_right_in = mTransitionManager.getAnimationRightIn();
        int transition_right_out = mTransitionManager.getAnimationRightOut();

        if(mWorkspaceFirstChild != null) {
            mAnimateFirstView = mWorkspaceFirstChild.getShortcutsAndWidgets();
            if(isLastPageAndEmpty && mWorkspaceSecondChild != null) {
                mWorkspaceFirstChild.removeChildView(mAnimateFirstView);
                mWorkspaceSecondChild.addView(mAnimateFirstView);
            }

            mFirstAnimatorSetForward = (AnimatorSet) AnimatorInflater
                    .loadAnimator(getContext(), isLastPageAndEmpty? transition_left_in:transition_left_out);
            setDurationToAnimatorSet(mFirstAnimatorSetForward, PREVIEW_ANIMATION_DURATION);
            mFirstAnimatorSetForward.setTarget(mAnimateFirstView);

            mFirstAnimatorSetBack = (AnimatorSet) AnimatorInflater
                    .loadAnimator(getContext(), isLastPageAndEmpty? transition_left_out:transition_left_in);
            setDurationToAnimatorSet(mFirstAnimatorSetBack, PREVIEW_ANIMATION_DURATION);
            mFirstAnimatorSetBack.setTarget(mAnimateFirstView);
        }

        if(mWorkspaceSecondChild != null) {
            mAnimateSecondView = mWorkspaceSecondChild.getShortcutsAndWidgets();
            if(!isLastPageAndEmpty && mWorkspaceFirstChild != null) {
                mWorkspaceSecondChild.removeChildView(mAnimateSecondView);
                mWorkspaceFirstChild.addView(mAnimateSecondView);
            }

            mSecondAnimatorSetForward = (AnimatorSet) AnimatorInflater
                    .loadAnimator(getContext(), isLastPageAndEmpty? transition_right_out:transition_right_in);
            setDurationToAnimatorSet(mSecondAnimatorSetForward, PREVIEW_ANIMATION_DURATION);
            mSecondAnimatorSetForward.setTarget(mAnimateSecondView);

            mSecondAnimatorSetBack = (AnimatorSet) AnimatorInflater
                    .loadAnimator(getContext(), isLastPageAndEmpty? transition_right_in:transition_right_out);
            setDurationToAnimatorSet(mSecondAnimatorSetBack, PREVIEW_ANIMATION_DURATION);
            mSecondAnimatorSetBack.setTarget(mAnimateSecondView);
        }

        //??????????????? animation??? ????????????.
        playGoingForwardAnimation(isLastPageAndEmpty);
    }

    public void setDurationToAnimatorSet(AnimatorSet animatorSet, int newDuration){
        long currentDuration = animatorSet.getTotalDuration();
        for (int i = 0; i < animatorSet.getChildAnimations().size(); i ++){
            Animator animator = animatorSet.getChildAnimations().get(i);
            animator.setDuration((int)(animator.getDuration() * newDuration/(currentDuration + 0f)));
        }
    }

    /**
     * ???????????? Animation??? ????????????.
     * @param isLastPageAndEmpty ??????????????? ????????????????????? ?????????????
     */
    public void playGoingForwardAnimation(boolean isLastPageAndEmpty){
        //Determine which animator to process end action
        boolean processEndOnFirstAnimation;
        AnimatorSet endProcessAnimator;
        if(mWorkspaceFirstChild == null)
            processEndOnFirstAnimation = false;
        else if(mWorkspaceSecondChild == null)
            processEndOnFirstAnimation = true;
        else{
            processEndOnFirstAnimation = mFirstAnimatorSetForward.getTotalDuration() > mSecondAnimatorSetForward.getTotalDuration();
        }

        if(processEndOnFirstAnimation)
            endProcessAnimator = mFirstAnimatorSetForward;
        else
            endProcessAnimator = mSecondAnimatorSetForward;

        if(mWorkspaceFirstChild != null){
            //set the same position as the first child
            mFirstAnimatorSetForward.start();
        }
        if(mWorkspaceSecondChild != null){
            mSecondAnimatorSetForward.start();
        }

        //Do end action
        endProcessAnimator.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mPlayBackAnimationStartDelaying = true;
                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if(mWorkspaceFirstChild != null){
                            mAnimateFirstView.initializeProperties();
                        }
                        if(mWorkspaceSecondChild != null) {
                            mAnimateSecondView.initializeProperties();
                        }

                        playGoingBackAnimation(isLastPageAndEmpty);
                        mPlayBackAnimationStartDelaying = false;
                    }
                }, PREVIEW_DELAY_ON_HALF);
            }

            @Override
            public void onAnimationStart(Animator animation) { }
            @Override
            public void onAnimationCancel(Animator animation) { }
            @Override
            public void onAnimationRepeat(Animator animation) { }
        });
    }

    /**
     * ???????????? Animation??? ????????????.(????????????)
     * @param isLastPageAndEmpty ??????????????? ????????????????????? ?????????????
     */
    public void playGoingBackAnimation(boolean isLastPageAndEmpty){
        //Determine which animator to process end action
        boolean processEndOnFirstAnimation;
        AnimatorSet endProcessAnimator;
        if(mWorkspaceFirstChild == null)
            processEndOnFirstAnimation = false;
        else if(mWorkspaceSecondChild == null)
            processEndOnFirstAnimation = true;
        else{
            processEndOnFirstAnimation = mFirstAnimatorSetBack.getTotalDuration() > mSecondAnimatorSetBack.getTotalDuration();
        }

        if(processEndOnFirstAnimation)
            endProcessAnimator = mFirstAnimatorSetBack;
        else
            endProcessAnimator = mSecondAnimatorSetBack;

        if(mWorkspaceFirstChild != null){
            mFirstAnimatorSetBack.start();
        }
        if(mWorkspaceSecondChild != null){
            mSecondAnimatorSetBack.start();
        }

        //Do end action
        endProcessAnimator.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if(mWorkspaceFirstChild != null) {
                    mAnimateFirstView.initializeProperties();
                }
                if(mWorkspaceSecondChild != null) {
                    mAnimateSecondView.initializeProperties();
                }

                //Move the child view to original parent
                if(mWorkspaceFirstChild != null && mWorkspaceSecondChild != null){
                    if(isLastPageAndEmpty) {
                        mWorkspaceSecondChild.removeChildView(mAnimateFirstView);
                        mWorkspaceFirstChild.addView(mAnimateFirstView);
                    }
                    else {
                        mWorkspaceFirstChild.removeChildView(mAnimateSecondView);
                        mWorkspaceSecondChild.addView(mAnimateSecondView);
                    }
                }
                mWorkspace.showNeighbors();
            }

            @Override
            public void onAnimationStart(Animator animation) { }
            @Override
            public void onAnimationCancel(Animator animation) { }
            @Override
            public void onAnimationRepeat(Animator animation) { }
        });
    }

    public boolean isPreviewAnimationRunning(){
        if(mFirstAnimatorSetForward != null && mFirstAnimatorSetForward.isRunning())
            return true;
        if(mSecondAnimatorSetForward != null && mSecondAnimatorSetForward.isRunning())
            return true;
        if(mFirstAnimatorSetBack != null && mFirstAnimatorSetBack.isRunning())
            return true;
        if(mSecondAnimatorSetBack != null && mSecondAnimatorSetBack.isRunning())
            return true;
        if(mPlayBackAnimationStartDelaying)
            return true;
        return false;
    }
}
