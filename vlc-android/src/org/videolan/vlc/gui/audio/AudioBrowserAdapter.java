/*
 * *************************************************************************
 *  BaseAdapter.java
 * **************************************************************************
 *  Copyright © 2016-2017 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.audio;

import android.content.Context;
import android.databinding.ViewDataBinding;
import android.graphics.drawable.BitmapDrawable;
import android.support.annotation.MainThread;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;

import org.videolan.medialibrary.media.DummyItem;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.BR;
import org.videolan.vlc.R;
import org.videolan.vlc.SortableAdapter;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.databinding.AudioBrowserItemBinding;
import org.videolan.vlc.databinding.AudioBrowserSeparatorBinding;
import org.videolan.vlc.gui.helpers.AsyncImageLoader;
import org.videolan.vlc.gui.helpers.SelectorViewHolder;
import org.videolan.vlc.gui.view.FastScroller;
import org.videolan.vlc.interfaces.IEventsHandler;
import org.videolan.vlc.util.MediaItemFilter;
import org.videolan.vlc.util.MediaLibraryItemComparator;
import org.videolan.vlc.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.videolan.medialibrary.media.MediaLibraryItem.FLAG_SELECTED;

public class AudioBrowserAdapter extends SortableAdapter<MediaLibraryItem, AudioBrowserAdapter.ViewHolder> implements FastScroller.SeparatedAdapter, Filterable {

    private static final String TAG = "VLC/AudioBrowserAdapter";

    private boolean mMakeSections = true;

    private ArrayList<MediaLibraryItem> mOriginalDataSet;
    private ItemFilter mFilter = new ItemFilter();
    private IEventsHandler mIEventsHandler;
    private int mSelectionCount = 0;
    private int mType;
    private int mParentType = 0;
    private BitmapDrawable mDefaultCover;

    public AudioBrowserAdapter(int type, IEventsHandler eventsHandler, boolean sections) {
        mIEventsHandler = eventsHandler;
        mMakeSections = sections;
        mType = type;
        mDefaultCover = getIconDrawable();
    }

    public int getAdapterType() {
        return mType;
    }

    public void setParentAdapterType(int type) {
        mParentType = type;
    }

    public int getParentAdapterType() {
        return mParentType;
    }


    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (viewType == MediaLibraryItem.TYPE_DUMMY) {
            AudioBrowserSeparatorBinding binding = AudioBrowserSeparatorBinding.inflate(inflater, parent, false);
            return new ViewHolder(binding);
        } else {
            AudioBrowserItemBinding binding = AudioBrowserItemBinding.inflate(inflater, parent, false);
            return new MediaItemViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        if (position >= mDataset.size())
            return;
        holder.binding.setVariable(BR.item, mDataset.get(position));
        if (holder.getType() == MediaLibraryItem.TYPE_MEDIA) {
            final boolean isSelected = mDataset.get(position).hasStateFlags(FLAG_SELECTED);
            ((MediaItemViewHolder)holder).setCoverlay(isSelected);
            holder.selectView(isSelected);
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position, List<Object> payloads) {
        if (Util.isListEmpty(payloads))
            onBindViewHolder(holder, position);
        else {
            final boolean isSelected = ((MediaLibraryItem)payloads.get(0)).hasStateFlags(FLAG_SELECTED);
            MediaItemViewHolder miv = (MediaItemViewHolder) holder;
            miv.setCoverlay(isSelected);
            miv.selectView(isSelected);
        }

    }

    @Override
    public void onViewRecycled(ViewHolder holder) {
        if (mDefaultCover != null)
            holder.binding.setVariable(BR.cover, mDefaultCover);
    }

    @Override
    public int getItemCount() {
        return mDataset.size();
    }

    public MediaLibraryItem getItem(int position) {
        return isPositionValid(position) ? mDataset.get(position) : null;
    }

    private boolean isPositionValid(int position) {
        return position >= 0 || position < mDataset.size();
    }

    public ArrayList<MediaLibraryItem> getAll() {
        return mDataset;
    }

    ArrayList<MediaLibraryItem> getMediaItems() {
        ArrayList<MediaLibraryItem> list = new ArrayList<>();
        for (MediaLibraryItem item : mDataset)
            if (!(item.getItemType() == MediaLibraryItem.TYPE_DUMMY)) list.add(item);
        return list;
    }

    int getListWithPosition(ArrayList<MediaLibraryItem> list, int position) {
        int offset = 0, count = getItemCount();
        for (int i = 0; i < count; ++i)
            if (mDataset.get(i).getItemType() == MediaLibraryItem.TYPE_DUMMY) {
                if (i < position)
                    ++offset;
            } else
                list.add(mDataset.get(i));
        return position-offset;
    }

    @Override
    public long getItemId(int position) {
        return isPositionValid(position) ? mDataset.get(position).getId() : -1;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).getItemType();
    }

    public boolean hasSections() {
        return mMakeSections;
    }

    @Override
    public String getSectionforPosition(int position) {
        if (mMakeSections)
            for (int i = position; i >= 0; --i)
                if (mDataset.get(i).getItemType() == MediaLibraryItem.TYPE_DUMMY) return mDataset.get(i).getTitle();
        return "";
    }

    @MainThread
    public boolean isEmpty() {
        return (peekLast().size() == 0);
    }

    public void clear() {
        mDataset.clear();
        mOriginalDataSet = null;
    }

    public void addAll(ArrayList<MediaLibraryItem> items) {
        mDataset = new ArrayList<>(items);
    }

    private ArrayList<MediaLibraryItem> removeSections(ArrayList<MediaLibraryItem> items) {
        ArrayList<MediaLibraryItem> newList = new ArrayList<>();
        for (MediaLibraryItem item : items)
            if (item.getItemType() != MediaLibraryItem.TYPE_DUMMY)
                newList.add(item);
        return newList;
    }

    private ArrayList<MediaLibraryItem> generateSections(ArrayList<? extends MediaLibraryItem> items, int sortby) {
        ArrayList<MediaLibraryItem> datalist = new ArrayList<>();
        switch(sortby) {
            case MediaLibraryItemComparator.SORT_BY_TITLE:
                String currentLetter = null;
                for (MediaLibraryItem item : items) {
                    if (item.getItemType() == MediaLibraryItem.TYPE_DUMMY)
                        continue;
                    String title = item.getTitle();
                    String letter = (title.isEmpty() || !Character.isLetter(title.charAt(0))) ? "#" : title.substring(0, 1).toUpperCase();
                    if (currentLetter == null || !TextUtils.equals(currentLetter, letter)) {
                        currentLetter = letter;
                        DummyItem sep = new DummyItem(currentLetter);
                        datalist.add(sep);
                    }
                    datalist.add(item);
                }
                break;
            case MediaLibraryItemComparator.SORT_BY_ARTIST:
                String currentArtist = null;
                for (MediaLibraryItem item : items) {
                    if (item.getItemType() == MediaLibraryItem.TYPE_DUMMY)
                        continue;
                    String artist = ((MediaWrapper)item).getArtist();
                    if (artist == null)
                        artist = "";
                    if (currentArtist == null || !TextUtils.equals(currentArtist, artist)) {
                        currentArtist = artist;
                        DummyItem sep = new DummyItem(TextUtils.isEmpty(currentArtist)
                                ? VLCApplication.getAppResources().getString(R.string.unknown_artist)
                                : currentArtist);
                        datalist.add(sep);
                    }
                    datalist.add(item);
                }
                break;
            case MediaLibraryItemComparator.SORT_BY_ALBUM:
                String currentAlbum = null;
                for (MediaLibraryItem item : items) {
                    if (item.getItemType() == MediaLibraryItem.TYPE_DUMMY)
                        continue;
                    String album = ((MediaWrapper)item).getAlbum();
                    if (album == null)
                        album = "";
                    if (currentAlbum == null || !TextUtils.equals(currentAlbum, album)) {
                        currentAlbum = album;
                        DummyItem sep = new DummyItem(TextUtils.isEmpty(currentAlbum)
                                ? VLCApplication.getAppResources().getString(R.string.unknown_album)
                                : currentAlbum);
                        datalist.add(sep);
                    }
                    datalist.add(item);
                }
                break;
            case MediaLibraryItemComparator.SORT_BY_LENGTH :
                String currentLengthCategory = null;
                for (MediaLibraryItem item : items) {
                    if (item.getItemType() == MediaLibraryItem.TYPE_DUMMY)
                        continue;
                    int length = MediaLibraryItemComparator.getLength(item);
                    String lengthCategory = MediaLibraryItemComparator.lengthToCategory(length);
                    if (currentLengthCategory == null || !TextUtils.equals(currentLengthCategory, lengthCategory)) {
                        currentLengthCategory = lengthCategory;
                        DummyItem sep = new DummyItem(currentLengthCategory);
                        datalist.add(sep);
                    }
                    datalist.add(item);
                }
                break;
            case MediaLibraryItemComparator.SORT_BY_DATE :
                String currentYear = null;
                for (MediaLibraryItem item : items) {
                    if (item.getItemType() == MediaLibraryItem.TYPE_DUMMY)
                        continue;
                    String year = MediaLibraryItemComparator.getYear(item);
                    if (currentYear == null || !TextUtils.equals(currentYear, year)) {
                        currentYear = year;
                        DummyItem sep = new DummyItem(currentYear);
                        datalist.add(sep);
                    }
                    datalist.add(item);
                }
                break;
            case MediaLibraryItemComparator.SORT_BY_NUMBER :
                int currentNumber = 0;
                for (MediaLibraryItem item : items) {
                    if (item.getItemType() == MediaLibraryItem.TYPE_DUMMY)
                        continue;
                    int number = MediaLibraryItemComparator.getTracksCount(item);
                    if (currentNumber != number) {
                        currentNumber = number;
                        DummyItem sep = new DummyItem(currentNumber == 0
                                ? VLCApplication.getAppResources().getString(R.string.unknown_number)
                                : VLCApplication.getAppResources().getQuantityString(R.plurals.songs_quantity, currentNumber, currentNumber));
                        datalist.add(sep);
                    }
                    datalist.add(item);
                }
                break;
        }
        return datalist;
    }

    public void remove(final MediaLibraryItem item) {
        final ArrayList<MediaLibraryItem> referenceList = new ArrayList<>(peekLast());
        if (referenceList.size() == 0) return;
        final ArrayList<MediaLibraryItem> dataList = new ArrayList<>(referenceList);
        dataList.remove(item);
        update(dataList);
    }


    public void addItem(final int position, final MediaLibraryItem item) {
        final ArrayList<MediaLibraryItem> referenceList = peekLast();
        final ArrayList<MediaLibraryItem> dataList = new ArrayList<>(referenceList);
        dataList.add(position,item);
        update(dataList);
    }

    public void restoreList() {
        if (mOriginalDataSet != null) {
            update(new ArrayList<>(mOriginalDataSet));
            mOriginalDataSet = null;
        }
    }

    @Override
    protected void onUpdateFinished() {
        super.onUpdateFinished();
        mIEventsHandler.onUpdateFinished(AudioBrowserAdapter.this);
    }

    protected ArrayList<MediaLibraryItem> prepareList(ArrayList<MediaLibraryItem> items) {
        if (!isSortAllowed(getSortBy()))
            sMediaComparator.setSortDefault();
        if (mMakeSections) {
            if (sMediaComparator.sortBy == MediaLibraryItemComparator.SORT_DEFAULT) {
                return generateSections(items, getDefaultSort());
            } else {
                ArrayList<MediaLibraryItem> newList = removeSections(items);
                Collections.sort(newList, sMediaComparator);
                return generateSections(newList, sMediaComparator.sortBy);
            }
        } else {
            Collections.sort(items, sMediaComparator);
            return items;
        }
    }

    @MainThread
    public List<MediaLibraryItem> getSelection() {
        List<MediaLibraryItem> selection = new LinkedList<>();
        for (MediaLibraryItem item : mDataset)
            if (item.hasStateFlags(FLAG_SELECTED))
                selection.add(item);
        return selection;
    }

    @MainThread
    public int getSelectionCount() {
        return mSelectionCount;
    }

    @MainThread
    public void resetSelectionCount() {
        mSelectionCount = 0;
    }

    @MainThread
    public void updateSelectionCount(boolean selected) {
        mSelectionCount += selected ? 1 : -1;
    }

    private BitmapDrawable getIconDrawable() {
        switch (mType) {
            case MediaLibraryItem.TYPE_ALBUM:
                return AsyncImageLoader.DEFAULT_COVER_ALBUM_DRAWABLE;
            case MediaLibraryItem.TYPE_ARTIST:
                return AsyncImageLoader.DEFAULT_COVER_ARTIST_DRAWABLE;
            case MediaLibraryItem.TYPE_MEDIA:
                return AsyncImageLoader.DEFAULT_COVER_AUDIO_DRAWABLE;
            default:
                return null;
        }
    }

    public class ViewHolder< T extends ViewDataBinding> extends SelectorViewHolder<T> {

        public ViewHolder(T vdb) {
            super(vdb);
            this.binding = vdb;
        }

        public int getType() {
            return MediaLibraryItem.TYPE_DUMMY;
        }
    }

    public class MediaItemViewHolder extends ViewHolder<AudioBrowserItemBinding> implements View.OnFocusChangeListener {
        int coverlayResource = 0;

        MediaItemViewHolder(AudioBrowserItemBinding binding) {
            super(binding);
            binding.setHolder(this);
            if (mDefaultCover != null)
                binding.setCover(mDefaultCover);
        }

        public void onClick(View v) {
            if (mIEventsHandler != null) {
                int position = getLayoutPosition();
                mIEventsHandler.onClick(v, position, mDataset.get(position));
            }
        }

        public void onMoreClick(View v) {
            if (mIEventsHandler != null) {
                int position = getLayoutPosition();
                mIEventsHandler.onCtxClick(v, position, mDataset.get(position));
            }
        }

        public boolean onLongClick(View view) {
            int position = getLayoutPosition();
            return mIEventsHandler.onLongClick(view, position, mDataset.get(position));
        }

        private void setCoverlay(boolean selected) {
            int resId = selected ? R.drawable.ic_action_mode_select : 0;
            if (resId != coverlayResource) {
                binding.mediaCover.setImageResource(selected ? R.drawable.ic_action_mode_select : 0);
                coverlayResource = resId;
            }
        }

        public int getType() {
            return MediaLibraryItem.TYPE_MEDIA;
        }

        @Override
        protected boolean isSelected() {
            return getItem(getLayoutPosition()).hasStateFlags(FLAG_SELECTED);
        }

//        private void setViewBackground(boolean focused, boolean selected) {
//            int selectionColor = selected || focused ? UiTools.ITEM_SELECTION_ON : 0;
//            if (selectionColor != this.selectionColor) {
//                itemView.setBackgroundColor(selectionColor);
//                this.selectionColor = selectionColor;
//            }
//        }
    }

    @Override
    public Filter getFilter() {
        return mFilter;
    }

    private class ItemFilter extends MediaItemFilter {

        @Override
        protected List<MediaLibraryItem> initData() {
            if (mOriginalDataSet == null) {
                mOriginalDataSet = new ArrayList<>(mDataset);
            }
            if (referenceList == null) {
                referenceList = new ArrayList<>(mDataset);
            }
            return referenceList;
        }

        @Override
        protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
            update((ArrayList<MediaLibraryItem>) filterResults.values);
        }

    }

    public void sortBy(int sortby, int direction) {
        boolean sort = isSortAllowed(sortby);
        if (sort) {
            super.sortBy(sortby, direction);
        }
    }

    @Override
    public int getDefaultSort() {
        switch (mParentType) {
            case MediaLibraryItem.TYPE_ARTIST:
                return mType == MediaLibraryItem.TYPE_ALBUM ? MediaLibraryItemComparator.SORT_BY_DATE : MediaLibraryItemComparator.SORT_BY_ALBUM;
            case MediaLibraryItem.TYPE_GENRE:
                return mType == MediaLibraryItem.TYPE_ALBUM ? MediaLibraryItemComparator.SORT_BY_TITLE : MediaLibraryItemComparator.SORT_BY_ALBUM;
            default:
                return MediaLibraryItemComparator.SORT_BY_TITLE;
        }
    }

    @Override
    public int getDefaultDirection() {
        return mParentType == MediaLibraryItem.TYPE_ARTIST ? -1 : 1;
    }

    @Override
    protected boolean isSortAllowed(int sort) {
        switch (sort) {
            case MediaLibraryItemComparator.SORT_BY_TITLE:
                return true;
            case MediaLibraryItemComparator.SORT_BY_DATE:
                return mType == MediaLibraryItem.TYPE_ALBUM;
            case MediaLibraryItemComparator.SORT_BY_LENGTH:
                return mType == MediaLibraryItem.TYPE_ALBUM || mType == MediaLibraryItem.TYPE_MEDIA;
            case MediaLibraryItemComparator.SORT_BY_NUMBER:
                return mType == MediaLibraryItem.TYPE_ALBUM || mType == MediaLibraryItem.TYPE_PLAYLIST;
            case MediaLibraryItemComparator.SORT_BY_ALBUM:
                return mParentType != 0 && mType == MediaLibraryItem.TYPE_MEDIA;
            case MediaLibraryItemComparator.SORT_BY_ARTIST:
                return mParentType == MediaLibraryItem.TYPE_GENRE && mType == MediaLibraryItem.TYPE_MEDIA;
            default:
                return false;
        }
    }
}
