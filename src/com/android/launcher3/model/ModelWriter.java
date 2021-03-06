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

package com.android.launcher3.model;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import ch.deletescape.lawnchair.iconpack.IconPackManager;
import com.android.launcher3.*;
import com.android.launcher3.FolderInfo;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherAppWidgetHost;
import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherModel.Callbacks;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.LauncherSettings.QuickAccess;
import com.android.launcher3.LauncherSettings.AssistantSectionOrder;
import com.android.launcher3.LauncherSettings.SearchHistory;
import com.android.launcher3.LauncherSettings.Settings;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.ContentWriter;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.LooperExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Class for handling model updates.
 */
public class ModelWriter {

    private static final String TAG = "ModelWriter";

    private final Context mContext;
    private final LauncherModel mModel;
    private final BgDataModel mBgDataModel;
    private final Handler mUiHandler;

    private final Executor mWorkerExecutor;
    private final boolean mHasVerticalHotseat;
    private final boolean mVerifyChanges;

    // Keep track of delete operations that occur when an Undo option is present; we may not commit.
    private final List<Runnable> mDeleteRunnables = new ArrayList<>();
    private boolean mPreparingToUndo;

    public ModelWriter(Context context, LauncherModel model, BgDataModel dataModel,
            boolean hasVerticalHotseat, boolean verifyChanges) {
        mContext = context;
        mModel = model;
        mBgDataModel = dataModel;
        mWorkerExecutor = new LooperExecutor(LauncherModel.getWorkerLooper());
        mHasVerticalHotseat = hasVerticalHotseat;
        mVerifyChanges = verifyChanges;
        mUiHandler = new Handler(Looper.getMainLooper());
    }

    private void updateItemInfoProps(
            ItemInfo item, long container, long screenId, int cellX, int cellY) {
        item.container = container;
        item.cellX = cellX;
        item.cellY = cellY;
        // We store hotseat items in canonical form which is this orientation invariant position
        // in the hotseat
        if (container == Favorites.CONTAINER_HOTSEAT) {
            item.screenId = getOrderInHotseat(cellX, cellY, LauncherAppState.getIDP(mContext).numHotseatIcons);
        } else {
            item.screenId = screenId;
        }
    }

    private int getOrderInHotseat(int x, int y, int size) {
        int xOrder = mHasVerticalHotseat ? (size - y - 1) : x;
        int yOrder = mHasVerticalHotseat ? x : y;
        return xOrder + yOrder * size;
    }

    /**
     * Adds an item to the DB if it was not created previously, or move it to a new
     * <container, screen, cellX, cellY>
     */
    public void addOrMoveItemInDatabase(ItemInfo item,
            long container, long screenId, int cellX, int cellY) {
        if (item.container == ItemInfo.NO_ID) {
            // From all apps
            addItemToDatabase(item, container, screenId, cellX, cellY);
        } else {
            // From somewhere else
            moveItemInDatabase(item, container, screenId, cellX, cellY);
        }
    }

    private void checkItemInfoLocked(long itemId, ItemInfo item, StackTraceElement[] stackTrace) {
        ItemInfo modelItem = mBgDataModel.itemsIdMap.get(itemId);
        if (modelItem != null && item != modelItem) {
            // check all the data is consistent
            if (modelItem instanceof ShortcutInfo && item instanceof ShortcutInfo) {
                ShortcutInfo modelShortcut = (ShortcutInfo) modelItem;
                ShortcutInfo shortcut = (ShortcutInfo) item;
                if (modelShortcut.title.toString().equals(shortcut.title.toString()) &&
                        modelShortcut.intent.filterEquals(shortcut.intent) &&
                        modelShortcut.id == shortcut.id &&
                        modelShortcut.itemType == shortcut.itemType &&
                        modelShortcut.container == shortcut.container &&
                        modelShortcut.screenId == shortcut.screenId &&
                        modelShortcut.cellX == shortcut.cellX &&
                        modelShortcut.cellY == shortcut.cellY &&
                        modelShortcut.spanX == shortcut.spanX &&
                        modelShortcut.spanY == shortcut.spanY) {
                    // For all intents and purposes, this is the same object
                    return;
                }
            }

            // the modelItem needs to match up perfectly with item if our model is
            // to be consistent with the database-- for now, just require
            // modelItem == item or the equality check above
            String msg = "item: " + ((item != null) ? item.toString() : "null") +
                    "modelItem: " +
                    ((modelItem != null) ? modelItem.toString() : "null") +
                    "Error: ItemInfo passed to checkItemInfo doesn't match original";
            RuntimeException e = new RuntimeException(msg);
            if (stackTrace != null) {
                e.setStackTrace(stackTrace);
            }
            throw e;
        }
    }

    /**
     * Move an item in the DB to a new <container, screen, cellX, cellY>
     */
    public void moveItemInDatabase(final ItemInfo item,
            long container, long screenId, int cellX, int cellY) {
        updateItemInfoProps(item, container, screenId, cellX, cellY);

        final ContentWriter writer = new ContentWriter(mContext)
                .put(Favorites.CONTAINER, item.container)
                .put(Favorites.CELLX, item.cellX)
                .put(Favorites.CELLY, item.cellY)
                .put(Favorites.RANK, item.rank)
                .put(Favorites.SCREEN, item.screenId);

        enqueueDeleteRunnable(new UpdateItemRunnable(item, writer));
    }

    /**
     * Move items in the DB to a new <container, screen, cellX, cellY>. We assume that the
     * cellX, cellY have already been updated on the ItemInfos.
     */
    public void moveItemsInDatabase(final ArrayList<ItemInfo> items, long container, int screen) {
        ArrayList<ContentValues> contentValues = new ArrayList<>();
        int count = items.size();

        for (int i = 0; i < count; i++) {
            ItemInfo item = items.get(i);
            updateItemInfoProps(item, container, screen, item.cellX, item.cellY);

            final ContentValues values = new ContentValues();
            values.put(Favorites.CONTAINER, item.container);
            values.put(Favorites.CELLX, item.cellX);
            values.put(Favorites.CELLY, item.cellY);
            values.put(Favorites.RANK, item.rank);
            values.put(Favorites.SCREEN, item.screenId);

            contentValues.add(values);
        }
        enqueueDeleteRunnable(new UpdateItemsRunnable(items, contentValues));
    }

    /**
     * Move and/or resize item in the DB to a new <container, screen, cellX, cellY, spanX, spanY>
     */
    public void modifyItemInDatabase(final ItemInfo item,
            long container, long screenId, int cellX, int cellY, int spanX, int spanY) {
        updateItemInfoProps(item, container, screenId, cellX, cellY);
        item.spanX = spanX;
        item.spanY = spanY;

        final ContentWriter writer = new ContentWriter(mContext)
                .put(Favorites.CONTAINER, item.container)
                .put(Favorites.CELLX, item.cellX)
                .put(Favorites.CELLY, item.cellY)
                .put(Favorites.RANK, item.rank)
                .put(Favorites.SPANX, item.spanX)
                .put(Favorites.SPANY, item.spanY)
                .put(Favorites.SCREEN, item.screenId);

        mWorkerExecutor.execute(new UpdateItemRunnable(item, writer));
    }

    private void executeUpdateItem(ItemInfo item, ContentWriter writer) {
        mWorkerExecutor.execute(new UpdateItemRunnable(item, writer));
    }

    public static void modifyItemInDatabase(Context context, final ItemInfo item, String alias,
                                            String swipeUpAction,
                                            IconPackManager.CustomIconEntry iconEntry, Bitmap icon,
                                            boolean updateIcon, boolean reload) {
        final ContentWriter writer = new ContentWriter(context);
        writer.put(Favorites.TITLE_ALIAS, alias);
        writer.put(Favorites.SWIPE_UP_ACTION, swipeUpAction);
        if (updateIcon) {
            writer.put(Favorites.CUSTOM_ICON, icon != null ? Utilities.flattenBitmap(icon) : null);
            writer.put(Favorites.CUSTOM_ICON_ENTRY, iconEntry != null ? iconEntry.toString() : null);
        }

        if (reload) {
            LauncherAppState.getInstance(context).getLauncher().getModelWriter().executeUpdateItem(item, writer);
            LauncherAppState.getInstance(context).getModel().forceReload();
        }
    }

    /**
     * Update an item to the database in a specified container.
     */
    public void updateItemInDatabase(ItemInfo item) {
        ContentWriter writer = new ContentWriter(mContext);
        item.onAddToDatabase(writer);
        mWorkerExecutor.execute(new UpdateItemRunnable(item, writer));
    }

    //Get all displaying shortcuts from access table
    //Returning _ID, SHORTCUT_ID, PACKAGE_NAME
    public Cursor getQuickAccessShortcuts(){
        final ContentResolver cr = mContext.getContentResolver();
        return cr.query(QuickAccess.CONTENT_URI,
                new String[] {QuickAccess.SHORTCUT_ID, QuickAccess.PACKAGE_NAME},
                "", null, "");
    }

    //Update data in quick access table
    public void updateQuickAccessShortcuts(List<ShortcutInfo> shortcutInfoList){
        final ContentResolver cr = mContext.getContentResolver();

        //First delete all from the table
        cr.delete(QuickAccess.CONTENT_URI, "", null);

        //Then add all shortcut items
        for (int i = 0; i < shortcutInfoList.size(); i ++){
            ShortcutInfo info = shortcutInfoList.get(i);

            final ContentWriter writer = new ContentWriter(mContext);
            writer.put(QuickAccess._ID, i);
            writer.put(QuickAccess.SHORTCUT_ID, info.getDeepShortcutId());
            writer.put(QuickAccess.PACKAGE_NAME, info.getPackageName());

            cr.insert(QuickAccess.CONTENT_URI, writer.getValues(mContext));
        }
    }

    //get assistant memory section current order and pin status
    public Cursor getAssistantEstimationSectionOrder() {
        final ContentResolver cr = mContext.getContentResolver();
        return cr.query(AssistantSectionOrder.CONTENT_URI,
                new String[] {AssistantSectionOrder.ESTIMATION_CURRENT_INDEX, AssistantSectionOrder.ESTIMATION_PINNED_STATUS},
                "", null, "");
    }

    //get assistant calendar section current order and pin status
    public Cursor getAssistantCalendarSectionOrder() {
        final ContentResolver cr = mContext.getContentResolver();
        return cr.query(AssistantSectionOrder.CONTENT_URI,
                new String[] {AssistantSectionOrder.CALENDAR_CURRENT_INDEX, AssistantSectionOrder.CALENDAR_PINNED_STATUS},
                "", null, "");
    }

    //get assistant clock section current order and pin status
    public Cursor getAssistantClockSectionOrder() {
        final ContentResolver cr = mContext.getContentResolver();
        return cr.query(AssistantSectionOrder.CONTENT_URI,
                new String[] {AssistantSectionOrder.CLOCK_CURRENT_INDEX, AssistantSectionOrder.CLOCK_PINNED_STATUS},
                "", null, "");
    }

    //get assistant contacts section current order and pin status
    public Cursor getAssistantContactsSectionOrder() {
        final ContentResolver cr = mContext.getContentResolver();
        return cr.query(AssistantSectionOrder.CONTENT_URI,
                new String[] {AssistantSectionOrder.CONTACTS_CURRENT_INDEX, AssistantSectionOrder.CONTACTS_PINNED_STATUS},
                "", null, "");
    }

    //get memory, calendar, clock, contacts sections orders and pin status
    public Cursor getAssistantSectionOrder() {
        final ContentResolver cr = mContext.getContentResolver();
        return cr.query(AssistantSectionOrder.CONTENT_URI,
                new String[] { AssistantSectionOrder.ESTIMATION_CURRENT_INDEX, AssistantSectionOrder.ESTIMATION_PINNED_STATUS,
                        AssistantSectionOrder.CALENDAR_CURRENT_INDEX, AssistantSectionOrder.CLOCK_PINNED_STATUS,
                        AssistantSectionOrder.CLOCK_CURRENT_INDEX, AssistantSectionOrder.CLOCK_PINNED_STATUS,
                        AssistantSectionOrder.CONTACTS_CURRENT_INDEX, AssistantSectionOrder.CONTACTS_PINNED_STATUS },
                "", null, "");
    }

    //Update data in assistant section order table
    public void updateAssistantSectionOrder(int memoryCurrentIndex, int memoryPinnedStatus,
                                            int calendarCurrentIndex, int calendarPinnedStatus,
                                            int clockCurrentIndex, int clockPinnedStatus,
                                            int contactsCurrentIndex, int contactsPinnedStatus) {
        final ContentResolver cr = mContext.getContentResolver();

        //First delete all from the table
        cr.delete(AssistantSectionOrder.CONTENT_URI, "", null);

        final ContentWriter writer = new ContentWriter(mContext);
        writer.put(AssistantSectionOrder._ID, 0);
        writer.put(AssistantSectionOrder.ESTIMATION_CURRENT_INDEX, memoryCurrentIndex);
        writer.put(AssistantSectionOrder.ESTIMATION_PINNED_STATUS, memoryPinnedStatus);
        writer.put(AssistantSectionOrder.CALENDAR_CURRENT_INDEX, calendarCurrentIndex);
        writer.put(AssistantSectionOrder.CALENDAR_PINNED_STATUS, calendarPinnedStatus);
        writer.put(AssistantSectionOrder.CLOCK_CURRENT_INDEX, clockCurrentIndex);
        writer.put(AssistantSectionOrder.CLOCK_PINNED_STATUS, clockPinnedStatus);
        writer.put(AssistantSectionOrder.CONTACTS_CURRENT_INDEX, contactsCurrentIndex);
        writer.put(AssistantSectionOrder.CONTACTS_PINNED_STATUS, contactsPinnedStatus);

        cr.insert(AssistantSectionOrder.CONTENT_URI, writer.getValues(mContext));

    }

    //get search history
    public Cursor getSearchHistory() {
        final ContentResolver cr = mContext.getContentResolver();
        return cr.query(SearchHistory.CONTENT_URI, null, null, null, "modified DESC LIMIT 15");
    }

    public void clearSearchHistory() {
        ContentResolver cr = mContext.getContentResolver();
        cr.delete(SearchHistory.CONTENT_URI, null, null);
    }

    public void addSearchHistoryItem(String newQuery) {
        Cursor cursor = mContext.getContentResolver().query(SearchHistory.CONTENT_URI, null, null, null, "modified DESC LIMIT 15");
        ContentResolver cr = mContext.getContentResolver();
        ContentWriter writer = new ContentWriter(mContext);
        if (cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                writer.put(SearchHistory._ID, cursor.getInt(cursor.getColumnIndex(SearchHistory._ID)) + 1);
                break;
            }
            if (cursor.moveToFirst()) {
                do {
                    String oldQuery = cursor.getString(cursor.getColumnIndex(SearchHistory.SEARCH_QUERY));
                    if (newQuery.equals(oldQuery)) {
                        cr.delete(SearchHistory.CONTENT_URI, SearchHistory.SEARCH_QUERY + "=?", new String[]{oldQuery});
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();
        } else {
            writer.put(SearchHistory._ID, 1);
        }
        writer.put(SearchHistory.SEARCH_QUERY, newQuery);

        cr.insert(SearchHistory.CONTENT_URI, writer.getValues(mContext));
    }

    /**
     * Add an item to the database in a specified container. Sets the container, screen, cellX and
     * cellY fields of the item. Also assigns an ID to the item.
     */
    public void addItemToDatabase(final ItemInfo item,
            long container, long screenId, int cellX, int cellY) {
        updateItemInfoProps(item, container, screenId, cellX, cellY);

        final ContentWriter writer = new ContentWriter(mContext);
        final ContentResolver cr = mContext.getContentResolver();
        item.onAddToDatabase(writer);

        item.id = Settings.call(cr, Settings.METHOD_NEW_ITEM_ID).getLong(Settings.EXTRA_VALUE);
        writer.put(Favorites._ID, item.id);

        ModelVerifier verifier = new ModelVerifier();

        final StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        mWorkerExecutor.execute(() -> {
            cr.insert(Favorites.CONTENT_URI, writer.getValues(mContext));

            synchronized (mBgDataModel) {
                checkItemInfoLocked(item.id, item, stackTrace);
                mBgDataModel.addItem(mContext, item, true);
                verifier.verifyModel();
            }
        });
    }

    /**
     * Removes the specified item from the database
     */
    public void deleteItemFromDatabase(ItemInfo item) {
        deleteItemsFromDatabase(Arrays.asList(item));
    }

    /**
     * Removes all the items from the database matching {@param matcher}.
     */
    public void deleteItemsFromDatabase(ItemInfoMatcher matcher) {
        deleteItemsFromDatabase(matcher.filterItemInfos(mBgDataModel.itemsIdMap));
    }

    /**
     * Removes the specified items from the database
     */
    public void deleteItemsFromDatabase(final Iterable<? extends ItemInfo> items) {
        ModelVerifier verifier = new ModelVerifier();

        enqueueDeleteRunnable(() -> {
            for (ItemInfo item : items) {
                final Uri uri = Favorites.getContentUri(item.id);
                mContext.getContentResolver().delete(uri, null, null);

                mBgDataModel.removeItem(mContext, item);
                verifier.verifyModel();
            }
        });
    }

    /**
     * Remove the specified folder and all its contents from the database.
     */
    public void deleteFolderAndContentsFromDatabase(final FolderInfo info) {
        ModelVerifier verifier = new ModelVerifier();

        enqueueDeleteRunnable(() -> {
            ContentResolver cr = mContext.getContentResolver();
            cr.delete(LauncherSettings.Favorites.CONTENT_URI,
                    LauncherSettings.Favorites.CONTAINER + "=" + info.id, null);
            mBgDataModel.removeItem(mContext, info.contents);
            info.contents.clear();

            cr.delete(LauncherSettings.Favorites.getContentUri(info.id), null, null);
            mBgDataModel.removeItem(mContext, info);
            verifier.verifyModel();
        });
    }

    /**
     * Deletes the widget info and the widget id.
     */
    public void deleteWidgetInfo(final LauncherAppWidgetInfo info, LauncherAppWidgetHost host) {
        if (host != null && !info.isCustomWidget() && info.isWidgetIdAllocated()) {
            // Deleting an app widget ID is a void call but writes to disk before returning
            // to the caller...
            enqueueDeleteRunnable(() -> host.deleteAppWidgetId(info.appWidgetId));
        }
        deleteItemFromDatabase(info);
    }

    /**
     * Delete operations tracked using {@link #enqueueDeleteRunnable} will only be called
     * if {@link #commitDelete} is called. Note that one of {@link #commitDelete()} or
     * {@link #abortDelete()} MUST be called after this method, or else all delete
     * operations will remain uncommitted indefinitely.
     */
    public void prepareToUndoDelete() {
        if (!mPreparingToUndo) {
            if (!mDeleteRunnables.isEmpty() && FeatureFlags.IS_DOGFOOD_BUILD) {
                throw new IllegalStateException("There are still uncommitted delete operations!");
            }
            mDeleteRunnables.clear();
            mPreparingToUndo = true;
        }
    }

    /**
     * If {@link #prepareToUndoDelete} has been called, we store the Runnable to be run when
     * {@link #commitDelete()} is called (or abandoned if {@link #abortDelete()} is called).
     * Otherwise, we run the Runnable immediately.
     */
    public void enqueueDeleteRunnable(Runnable r) {
        if (mPreparingToUndo) {
            mDeleteRunnables.add(r);
        } else {
            mWorkerExecutor.execute(r);
        }
    }

    public void commitDelete() {
        mPreparingToUndo = false;
        for (Runnable runnable : mDeleteRunnables) {
            mWorkerExecutor.execute(runnable);
        }
        mDeleteRunnables.clear();
    }

    public void abortDelete(int pageToBindFirst) {
        mPreparingToUndo = false;
        mDeleteRunnables.clear();
        // We do a full reload here instead of just a rebind because Folders change their internal
        // state when dragging an item out, which clobbers the rebind unless we load from the DB.
        mModel.forceReload(pageToBindFirst);
    }

    private class UpdateItemRunnable extends UpdateItemBaseRunnable {
        private final ItemInfo mItem;
        private final ContentWriter mWriter;
        private final long mItemId;

        UpdateItemRunnable(ItemInfo item, ContentWriter writer) {
            mItem = item;
            mWriter = writer;
            mItemId = item.id;
        }

        @Override
        public void run() {
            Uri uri = Favorites.getContentUri(mItemId);
            mContext.getContentResolver().update(uri, mWriter.getValues(mContext), null, null);
            updateItemArrays(mItem, mItemId);
        }
    }

    private class UpdateItemsRunnable extends UpdateItemBaseRunnable {
        private final ArrayList<ContentValues> mValues;
        private final ArrayList<ItemInfo> mItems;

        UpdateItemsRunnable(ArrayList<ItemInfo> items, ArrayList<ContentValues> values) {
            mValues = values;
            mItems = items;
        }

        @Override
        public void run() {
            ArrayList<ContentProviderOperation> ops = new ArrayList<>();
            int count = mItems.size();
            for (int i = 0; i < count; i++) {
                ItemInfo item = mItems.get(i);
                final long itemId = item.id;
                final Uri uri = Favorites.getContentUri(itemId);
                ContentValues values = mValues.get(i);

                ops.add(ContentProviderOperation.newUpdate(uri).withValues(values).build());
                updateItemArrays(item, itemId);
            }
            try {
                mContext.getContentResolver().applyBatch(LauncherProvider.AUTHORITY, ops);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private abstract class UpdateItemBaseRunnable implements Runnable {
        private final StackTraceElement[] mStackTrace;
        private final ModelVerifier mVerifier = new ModelVerifier();

        UpdateItemBaseRunnable() {
            mStackTrace = new Throwable().getStackTrace();
        }

        protected void updateItemArrays(ItemInfo item, long itemId) {
            // Lock on mBgLock *after* the db operation
            synchronized (mBgDataModel) {
                checkItemInfoLocked(itemId, item, mStackTrace);

                if (item.container != Favorites.CONTAINER_DESKTOP &&
                        item.container != Favorites.CONTAINER_HOTSEAT) {
                    // Item is in a folder, make sure this folder exists
                    if (!mBgDataModel.folders.containsKey(item.container)) {
                        // An items container is being set to a that of an item which is not in
                        // the list of Folders.
                        String msg = "item: " + item + " container being set to: " +
                                item.container + ", not in the list of folders";
                        Log.e(TAG, msg);
                    }
                }

                // Items are added/removed from the corresponding FolderInfo elsewhere, such
                // as in Workspace.onDrop. Here, we just add/remove them from the list of items
                // that are on the desktop, as appropriate
                ItemInfo modelItem = mBgDataModel.itemsIdMap.get(itemId);
                if (modelItem != null &&
                        (modelItem.container == Favorites.CONTAINER_DESKTOP ||
                                modelItem.container == Favorites.CONTAINER_HOTSEAT)) {
                    switch (modelItem.itemType) {
                        case Favorites.ITEM_TYPE_APPLICATION:
                        case Favorites.ITEM_TYPE_SHORTCUT:
                        case Favorites.ITEM_TYPE_DEEP_SHORTCUT:
                        case Favorites.ITEM_TYPE_FOLDER:
                            if (!mBgDataModel.workspaceItems.contains(modelItem)) {
                                mBgDataModel.workspaceItems.add(modelItem);
                            }
                            break;
                        default:
                            break;
                    }
                } else {
                    mBgDataModel.workspaceItems.remove(modelItem);
                }
                mVerifier.verifyModel();
            }
        }
    }

    /**
     * Utility class to verify model updates are propagated properly to the callback.
     */
    public class ModelVerifier {

        final int startId;

        ModelVerifier() {
            startId = mBgDataModel.lastBindId;
        }

        void verifyModel() {
            if (!mVerifyChanges || mModel.getCallback() == null) {
                return;
            }

            int executeId = mBgDataModel.lastBindId;

            mUiHandler.post(() -> {
                int currentId = mBgDataModel.lastBindId;
                if (currentId > executeId) {
                    // Model was already bound after job was executed.
                    return;
                }
                if (executeId == startId) {
                    // Bound model has not changed during the job
                    return;
                }
                // Bound model was changed between submitting the job and executing the job
                Callbacks callbacks = mModel.getCallback();
                if (callbacks != null) {
                    callbacks.rebindModel();
                }
            });
        }
    }
}
