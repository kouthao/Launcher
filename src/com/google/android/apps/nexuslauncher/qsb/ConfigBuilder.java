package com.google.android.apps.nexuslauncher.qsb;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RemoteViews;

import com.android.launcher3.AppInfo;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AllAppsRecyclerView;
import com.android.launcher3.allapps.AlphabeticalAppsList;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.graphics.BitmapRenderer;
import com.android.launcher3.uioverrides.WallpaperColorInfo;
import com.android.launcher3.util.Themes;
import com.google.android.apps.nexuslauncher.NexusLauncherActivity;
import com.google.android.apps.nexuslauncher.PredictionUiStateManager;
import com.google.android.apps.nexuslauncher.allapps.PredictionsFloatingHeader;
import com.google.android.apps.nexuslauncher.search.AppSearchProvider;
import com.google.android.apps.nexuslauncher.search.nano.SearchProto.a_search;
import com.google.android.apps.nexuslauncher.search.nano.SearchProto.b_search;
import com.google.android.apps.nexuslauncher.search.nano.SearchProto.c_search;
import com.google.android.apps.nexuslauncher.search.nano.SearchProto.d_search;

import com.google.android.apps.nexuslauncher.util.ComponentKeyMapper;
import java.util.ArrayList;
import java.util.List;

public class ConfigBuilder {
    private final c_search mNano;
    private final NexusLauncherActivity mActivity;
    private final Bundle mBundle;
    private boolean co;
    private final AbstractQsbLayout mQsbLayout;
    private BubbleTextView mBubbleTextView;
    private final boolean mIsAllApps;
    private final UserManagerCompat mUserManager;

    public ConfigBuilder(AbstractQsbLayout qsbLayout, boolean isAllApps) {
        mBundle = new Bundle();
        mNano = new c_search();
        mQsbLayout = qsbLayout;
        mActivity = qsbLayout.mActivity;
        mIsAllApps = isAllApps;
        mUserManager = UserManagerCompat.getInstance(mActivity);
    }

    public static Intent getSearchIntent(Rect sourceBounds, View gIcon, View micIcon) {
        Intent intent = new Intent("com.google.nexuslauncher.FAST_TEXT_SEARCH");
        intent.setSourceBounds(sourceBounds);
        if (micIcon.getVisibility() != View.VISIBLE) {
            intent.putExtra("source_mic_alpha", 0f);
        }
        return intent.putExtra("source_round_left", true)
                .putExtra("source_round_right", true)
                .putExtra("source_logo_offset", getCenter(gIcon, sourceBounds))
                .putExtra("source_mic_offset", getCenter(micIcon, sourceBounds))
                .putExtra("use_fade_animation", true)
                .setPackage("com.google.android.googlequicksearchbox")
                .addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private void bW() {
        if (mNano.ez != null) {
            return;
        }
        final a_search en = mNano.en;
        final a_search ez = new a_search();
        ez.ef = en.ef;
        ez.eg = en.eg + en.ee;
        ez.ee = en.ee;
        ez.eh = en.eh;
        mNano.ez = ez;
    }

    private AllAppsRecyclerView getAppsView() {
        return (AllAppsRecyclerView) mActivity.findViewById(R.id.apps_list_view);
    }

    private int getBackgroundColor() {
        return ColorUtils.compositeColors(Themes.getAttrColor(mActivity, R.attr.allAppsScrimColor),
                ColorUtils.setAlphaComponent(WallpaperColorInfo.getInstance(mActivity).getMainColor(), 255));
    }

    private b_search bZ(final AppInfo appInfo, final int n) {
        if (appInfo == null) {
            return null;
        }
        final b_search b = new b_search();
        b.label = appInfo.title.toString();
        b.ej = "icon_bitmap_" + n;
        mBundle.putParcelable(b.ej, appInfo.iconBitmap);
        Uri uri = AppSearchProvider.buildUri(appInfo, mUserManager);
        b.el = uri.toString();
        b.ek = new Intent("com.google.android.apps.nexuslauncher.search.APP_LAUNCH",
                uri.buildUpon().appendQueryParameter("predictionRank", Integer.toString(n)).build())
                .toUri(0);
        return b;
    }

    private RemoteViews searchIconTemplate() {
        final RemoteViews remoteViews = new RemoteViews(mActivity.getPackageName(), R.layout.apps_search_icon_template);

        final int iconSize = mBubbleTextView.getIconSize();
        final int horizontalPadding = (mBubbleTextView.getWidth() - iconSize) / 2;
        final int paddingTop = mBubbleTextView.getPaddingTop();
        final int paddingBottom = mBubbleTextView.getHeight() - iconSize - paddingTop;
        remoteViews.setViewPadding(android.R.id.icon, horizontalPadding, paddingTop, horizontalPadding, paddingBottom);
        final int minPadding = Math.min((int) (iconSize * 0.12f), Math.min(horizontalPadding, Math.min(paddingTop, paddingBottom)));
        remoteViews.setViewPadding(R.id.click_feedback_wrapper, horizontalPadding - minPadding, paddingTop - minPadding, horizontalPadding - minPadding, paddingBottom - minPadding);
        remoteViews.setTextViewTextSize(android.R.id.title, 0, mActivity.getDeviceProfile().allAppsIconTextSizePx);
        remoteViews.setViewPadding(android.R.id.title, mBubbleTextView.getPaddingLeft(), mBubbleTextView.getCompoundDrawablePadding() + mBubbleTextView.getIconSize(), mBubbleTextView.getPaddingRight(), 0);

        return remoteViews;
    }

    private RemoteViews searchQsbTemplate() {
        final RemoteViews remoteViews = new RemoteViews(mActivity.getPackageName(), R.layout.apps_search_qsb_template);

        final int effectiveHeight = mQsbLayout.getHeight() - mQsbLayout.getPaddingTop() - mQsbLayout.getPaddingBottom() + 20;
        final Bitmap mShadowBitmap = mQsbLayout.mShadowBitmap;
        if(mShadowBitmap != null) {
            final int internalWidth = (mShadowBitmap.getWidth() - effectiveHeight) / 2;
            final int verticalPadding = (mQsbLayout.getHeight() - mShadowBitmap.getHeight()) / 2;
            remoteViews.setViewPadding(R.id.qsb_background_container, mQsbLayout.getPaddingLeft() - internalWidth, verticalPadding, mQsbLayout.getPaddingRight() - internalWidth, verticalPadding);
            final Bitmap bitmap = Bitmap.createBitmap(mShadowBitmap, 0, 0, mShadowBitmap.getWidth() / 2, mShadowBitmap.getHeight());
            final Bitmap bitmap2 = Bitmap.createBitmap(mShadowBitmap, (mShadowBitmap.getWidth() - 20) / 2, 0, 20, mShadowBitmap.getHeight());
            remoteViews.setImageViewBitmap(R.id.qsb_background_1, bitmap);
            remoteViews.setImageViewBitmap(R.id.qsb_background_2, bitmap2);
            remoteViews.setImageViewBitmap(R.id.qsb_background_3, bitmap);
        }
        remoteViews.setViewVisibility(R.id.mic_icon, View.INVISIBLE);

        int horizontalPadding = mQsbLayout.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ?
                mQsbLayout.getWidth() : 0;
        remoteViews.setViewPadding(R.id.qsb_icon_container, horizontalPadding, 0, horizontalPadding, 0);

        return remoteViews;
    }

    private static Point getCenter(final View view, final Rect rect) {
        final int[] location = new int[2];
        view.getLocationInWindow(location);
        final Point point = new Point();
        point.x = location[0] - rect.left + view.getWidth() / 2;
        point.y = location[1] - rect.top + view.getHeight() / 2;
        return point;
    }

    private void cd() {
        mNano.ey = "search_box_template";
        mBundle.putParcelable(mNano.ey, searchQsbTemplate());
        mNano.ew = R.id.g_icon;
        mNano.ex = 0;
        final a_search viewBounds = getViewBounds(mActivity.getDragLayer());
        final int topShift = mNano.en.eg + (co ? 0 : mNano.en.ee);
        viewBounds.eg += topShift;
        viewBounds.ee -= topShift;
        mNano.et = viewBounds;
        if (viewBounds.eh > 0 && viewBounds.ee > 0) {
            Bitmap bitmap = BitmapRenderer.createHardwareBitmap(viewBounds.eh, viewBounds.ee, out -> a(topShift, out));
            mBundle.putParcelable(mNano.eu, bitmap);
        } else {
            String stringBuilder = "Invalid preview bitmap size. width: " +
                    viewBounds.eh +
                    "hight: " +
                    viewBounds.ee +
                    " top shift: " +
                    topShift;
            Log.e("ConfigBuilder", stringBuilder);
            viewBounds.ee = 0;
            viewBounds.ef = 0;
            viewBounds.eg = 0;
            viewBounds.eh = 0;
            Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            bitmap.setPixel(0, 0, 0);
            mBundle.putParcelable(mNano.eu, bitmap);
        }
    }

    private /* synthetic */ void a(int i, Canvas canvas) {
        int save = canvas.save();
        canvas.translate(0.0f, (float) (-i));
        a(canvas, mActivity.getAppsView().getRecyclerViewContainer());
        a(canvas, mActivity.getAppsView().getFloatingHeaderView());
        canvas.restoreToCount(save);
    }

    private void a(Canvas canvas, View view) {
        final int[] array = {0, 0};
        mActivity.getDragLayer().mapCoordInSelfToDescendant(mActivity.getAppsView(), array);
        mActivity.getDragLayer().mapCoordInSelfToDescendant(view, array);
        canvas.translate((float) (-array[0]), (float) (-array[1]));
        view.draw(canvas);
        canvas.translate((float) array[0], (float) array[1]);
    }

    private void ce() {
        View view = null;
        AllAppsRecyclerView appsView = getAppsView();
        GridLayoutManager.SpanSizeLookup spanSizeLookup = ((GridLayoutManager) appsView.getLayoutManager())
                .getSpanSizeLookup();
        int allAppsCols = Math
                .min(mActivity.getDeviceProfile().inv.numColsDrawer, appsView.getChildCount());
        int childCount = appsView.getChildCount();
        BubbleTextView[] bubbleTextViewArr = new BubbleTextView[allAppsCols];
        int i4 = -1;
        for (int i = 0; i < childCount; i++) {
            RecyclerView.ViewHolder childViewHolder = appsView.getChildViewHolder(appsView.getChildAt(i));
            if (childViewHolder.itemView instanceof BubbleTextView) {
                int spanGroupIndex = spanSizeLookup.getSpanGroupIndex(childViewHolder.getLayoutPosition(), allAppsCols);
                if (spanGroupIndex >= 0) {
                    if (i4 >= 0) {
                        if (spanGroupIndex != i4) {
                            view = childViewHolder.itemView;
                            break;
                        }
                    }
                    i4 = spanGroupIndex;
                    int index = ((GridLayoutManager.LayoutParams) childViewHolder.itemView.getLayoutParams()).getSpanIndex();
                    bubbleTextViewArr[index] = (BubbleTextView) childViewHolder.itemView;
                }
            }
        }
        if (bubbleTextViewArr.length == 0 || bubbleTextViewArr[0] == null) {
            Log.e("ConfigBuilder", "No icons rendered in all apps");
            cf();
            return;
        }
        mBubbleTextView = bubbleTextViewArr[0];
        mNano.es = allAppsCols;
        int iconCountOffset = 0;
        for (int i = 0; i < bubbleTextViewArr.length; i++) {
            if (bubbleTextViewArr[i] == null) {
                iconCountOffset = allAppsCols - i;
                allAppsCols = i;
                break;
            }
        }
        co = appsView.getChildViewHolder(bubbleTextViewArr[0]).getItemViewType() == 4;
        a_search lastColumn = getViewBounds(bubbleTextViewArr[allAppsCols - 1]);
        a_search firstColumn = getViewBounds(bubbleTextViewArr[0]);
        if (Utilities.isRtl(mActivity.getResources())) {
            a_search temp = lastColumn;
            lastColumn = firstColumn;
            firstColumn = temp;
        }
        int iconWidth = lastColumn.eh;
        int totalIconDistance = lastColumn.ef - firstColumn.ef;
        int iconDistance = totalIconDistance / allAppsCols;
        firstColumn.eh = iconWidth + totalIconDistance;
        if (Utilities.isRtl(mActivity.getResources())) {
            firstColumn.ef -= iconCountOffset * iconWidth;
            firstColumn.eh += iconCountOffset * iconWidth;
        } else {
            firstColumn.eh += iconCountOffset * (iconDistance + iconWidth);
        }
        mNano.en = firstColumn;
        if (!this.co) {
            firstColumn.eg -= firstColumn.ee;
        } else if (view != null) {
            a_search viewBounds3 = getViewBounds(view);
            viewBounds3.eh = firstColumn.eh;
            mNano.ez = viewBounds3;
        }
        bW();
        List<ComponentKeyMapper> predictedApps = ((PredictionsFloatingHeader) mActivity.getAppsView().getFloatingHeaderView()).getPredictionRowView().getPredictedAppComponents();
        List<b_search> bSearches = new ArrayList<>();
        final int count = Math.min(predictedApps.size(), allAppsCols);
        for (int i = 0; i < count; i++) {
            b_search bSearch = bZ(mActivity.getAppsView().getAppsStore().getApp(predictedApps.get(i).getComponentKey()), i);
            if (bSearch != null) {
                bSearches.add(bSearch);
            }
        }
        mNano.eo = new b_search[bSearches.size()];
        bSearches.toArray(mNano.eo);
    }

    private void cf() {
        int n2 = 0;
        mNano.es = mActivity.getDeviceProfile().inv.numColumns;
        final int width = mActivity.getHotseat().getWidth();
        final int dimensionPixelSize = mActivity.getResources().getDimensionPixelSize(R.dimen.dynamic_grid_edge_margin);
        final a_search en = new a_search();
        en.ef = dimensionPixelSize;
        en.eh = width - dimensionPixelSize - dimensionPixelSize;
        en.ee = mActivity.getDeviceProfile().allAppsCellHeightPx;
        mNano.en = en;
        bW();
        final AlphabeticalAppsList apps = getAppsView().getApps();
        mBubbleTextView = (BubbleTextView) mActivity.getLayoutInflater().inflate(R.layout.all_apps_icon, getAppsView(), false);
        final ViewGroup.LayoutParams layoutParams = mBubbleTextView.getLayoutParams();
        layoutParams.height = en.ee;
        layoutParams.width = en.eh / mNano.es;
        if (!apps.getApps().isEmpty()) {
            mBubbleTextView.applyFromApplicationInfo(apps.getApps().get(0));
        }
        mBubbleTextView.measure(View.MeasureSpec.makeMeasureSpec(layoutParams.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(layoutParams.height, View.MeasureSpec.EXACTLY));
        mBubbleTextView.layout(0, 0, layoutParams.width, layoutParams.height);
        final ArrayList<b_search> list = new ArrayList<>(mNano.es);
        mNano.eo = list.toArray(new b_search[list.size()]);
    }

    private static a_search getViewBounds(final View view) {
        final a_search a = new a_search();
        a.eh = view.getWidth();
        a.ee = view.getHeight();
        final int[] array = new int[2];
        view.getLocationInWindow(array);
        a.ef = array[0];
        a.eg = array[1];
        return a;
    }

    public byte[] build() {
        mNano.em = getBackgroundColor();
        mNano.eq = Themes.getAttrBoolean(mActivity, R.attr.isMainColorDark);
        if (mIsAllApps) {
            ce();
        } else {
            cf();
        }
        mNano.ep = "icon_view_template";
        mBundle.putParcelable(mNano.ep, searchIconTemplate());
        mNano.er = "icon_long_click";
        mBundle.putParcelable(mNano.er, PendingIntent.getBroadcast(mActivity, 2055, new Intent().setComponent(new ComponentName(mActivity, LongClickReceiver.class)), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT));
        LongClickReceiver.bq(mActivity);
        mNano.ev = getViewBounds(mQsbLayout);
        mNano.eA = mIsAllApps;
        if (mIsAllApps) {
            cd();
        }
        final d_search d = new d_search();
        d.eB = mNano;
        return com.google.protobuf.nano.MessageNano.toByteArray(d);
    }

    public Bundle getExtras() {
        return mBundle;
    }
}
