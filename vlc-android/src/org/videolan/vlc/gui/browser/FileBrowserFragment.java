/*
 * *************************************************************************
 *  FileBrowserFragment.java
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

import android.content.Context;
import android.content.DialogInterface;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatEditText;
import android.text.InputType;
import android.text.TextUtils;
import android.view.MenuItem;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.Storage;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.AudioPlayerContainerActivity;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.CustomDirectories;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.Strings;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class FileBrowserFragment extends BaseBrowserFragment {

    private AlertDialog mAlertDialog;

    @Override
    protected Fragment createFragment() {
        return new FileBrowserFragment();
    }

    public String getTitle() {
        if (mRoot)
            return getCategoryTitle();
        else {
            String title;
            if (mCurrentMedia != null) {
                if (TextUtils.equals(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY, Strings.removeFileProtocole(mMrl)))
                    title = getString(R.string.internal_memory);
                else
                    title = this instanceof FilePickerFragment ? mCurrentMedia.getUri().toString() : mCurrentMedia.getTitle();
            } else
                title = this instanceof FilePickerFragment ? mMrl : FileUtils.getFileNameFromPath(mMrl);
            return title;
        }
    }

    @Override
    protected String getCategoryTitle() {
        return getString(R.string.directories);
    }

    @Override
    protected void browseRoot() {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                String storages[] = AndroidDevices.getMediaDirectories();
                MediaWrapper directory;
                final ArrayList<MediaLibraryItem> devices = new ArrayList<>(storages.length);
                for (String mediaDirLocation : storages) {
                    if (!(new File(mediaDirLocation).exists()))
                        continue;
                    directory = new MediaWrapper(AndroidUtil.PathToUri(mediaDirLocation));
                    directory.setType(MediaWrapper.TYPE_DIR);
                    if (TextUtils.equals(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY, mediaDirLocation))
                        directory.setDisplayTitle(VLCApplication.getAppResources().getString(R.string.internal_memory));
                    devices.add(directory);
                }
                VLCApplication.runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.update(devices);
                    }
                });
                mHandler.sendEmptyMessage(BrowserFragmentHandler.MSG_HIDE_LOADING);
                mRoot = true;
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mAlertDialog != null && mAlertDialog.isShowing())
            mAlertDialog.dismiss();
    }

    public void showAddDirectoryDialog() {
        final Context context = getActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final AppCompatEditText input = new AppCompatEditText(context);
        if (!AndroidUtil.isHoneycombOrLater) {
            input.setTextColor(getResources().getColor(R.color.grey50));
        }
        input.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        builder.setTitle(R.string.add_custom_path);
        builder.setMessage(R.string.add_custom_path_description);
        builder.setView(input);
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int which) {}
        });
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String path = input.getText().toString().trim();
                File f = new File(path);
                if (!f.exists() || !f.isDirectory()) {
                    UiTools.snacker(getView(), getString(R.string.directorynotfound, path));
                    return;
                }

                try {
                    CustomDirectories.addCustomDirectory(f.getCanonicalPath());
                    ((AudioPlayerContainerActivity)getActivity()).updateLib();
                } catch (IOException ignored) {}
            }
        });
        mAlertDialog = builder.show();
    }

    @Override
    protected boolean handleContextItemSelected(MenuItem item, int position) {
        if (mRoot) {
            if (item.getItemId() == R.id.directory_remove_custom_path){
                Storage storage = (Storage) mAdapter.getItem(position);
                MediaDatabase.getInstance().recursiveRemoveDir(storage.getUri().getPath());
                CustomDirectories.removeCustomDirectory(storage.getUri().getPath());
                mAdapter.removeItem(position);
                ((AudioPlayerContainerActivity)getActivity()).updateLib();
                return true;
            } else
                return false;
        } else
            return super.handleContextItemSelected(item, position);
    }

    public boolean isSortEnabled() {
        return !mRoot;
    }
}
