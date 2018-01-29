/*
 * *************************************************************************
 *  StorageBrowserAdapter.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.browser;

import android.view.View;
import android.widget.CheckBox;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.Storage;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.helpers.MedialibraryUtils;
import org.videolan.vlc.gui.helpers.ThreeStatesCheckbox;
import org.videolan.vlc.util.CustomDirectories;

import java.util.ArrayList;
import java.util.Arrays;

class StorageBrowserAdapter extends BaseBrowserAdapter {

    private static ArrayList<String> mMediaDirsLocation;
    private static ArrayList<String> mCustomDirsLocation;

    StorageBrowserAdapter(BaseBrowserFragment fragment) {
        super(fragment);
        updateMediaDirs();
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        final MediaViewHolder vh = (MediaViewHolder) holder;
        MediaLibraryItem storage = getItem(position);

        if (storage.getItemType() == MediaLibraryItem.TYPE_MEDIA)
            storage = new Storage(((MediaWrapper)storage).getUri());
        String storagePath = ((Storage)storage).getUri().getPath();
        if (!storagePath.endsWith("/"))
            storagePath += "/";
        boolean hasContextMenu = mCustomDirsLocation.contains(storagePath);
        boolean checked = ((StorageBrowserFragment) fragment).mScannedDirectory || mMediaDirsLocation.contains(storagePath);
        vh.binding.setItem(storage);
        vh.binding.setHasContextMenu(hasContextMenu);
        if (checked)
            vh.binding.browserCheckbox.setState(ThreeStatesCheckbox.STATE_CHECKED);
        else if (hasDiscoveredChildren(storagePath))
            vh.binding.browserCheckbox.setState(ThreeStatesCheckbox.STATE_PARTIAL);
        else
            vh.binding.browserCheckbox.setState(ThreeStatesCheckbox.STATE_UNCHECKED);
        vh.binding.setCheckEnabled(!((StorageBrowserFragment) fragment).mScannedDirectory);
        if (hasContextMenu)
            vh.setContextMenuListener();
    }

    private boolean hasDiscoveredChildren(String path) {
        for (String directory : mMediaDirsLocation)
            if (directory.startsWith(path))
                return true;
        return false;
    }

    public void addItem(MediaLibraryItem item, boolean top, int position) {
        if (item.getItemType() == MediaLibraryItem.TYPE_MEDIA)
            item = new Storage(((MediaWrapper)item).getUri());
        else if (item.getItemType() != MediaLibraryItem.TYPE_STORAGE)
            return;
        super.addItem(item, top, position);
    }

    void updateMediaDirs() {
        if (mMediaDirsLocation != null)
            mMediaDirsLocation.clear();
        String folders[] = VLCApplication.getMLInstance().getFoldersList();
        mMediaDirsLocation = new ArrayList<>(folders.length);
        for (String folder : folders) {
            mMediaDirsLocation.add(folder.substring(7));
        }
        mCustomDirsLocation = new ArrayList<>(Arrays.asList(CustomDirectories.getCustomDirectories()));
    }

    protected void checkBoxAction(View v, String mrl) {
        ThreeStatesCheckbox tscb = (ThreeStatesCheckbox) v;
        int state = tscb.getState();
        if (state == ThreeStatesCheckbox.STATE_CHECKED)
            MedialibraryUtils.addDir(mrl);
        else
            MedialibraryUtils.removeDir(mrl);
        ((StorageBrowserFragment)fragment).processEvent((CheckBox) v, mrl);
    }
}
