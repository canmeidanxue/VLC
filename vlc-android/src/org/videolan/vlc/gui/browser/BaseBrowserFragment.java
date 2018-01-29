/**
 * **************************************************************************
 * BaseBrowserFragment.java
 * ****************************************************************************
 * Copyright © 2015-2017 VLC authors and VideoLAN
 * Author: Geoffrey Métais
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 * ***************************************************************************
 */
package org.videolan.vlc.gui.browser;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.util.SimpleArrayMap;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.TextView;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.libvlc.util.MediaBrowser;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.Storage;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.InfoActivity;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.view.ContextMenuRecyclerView;
import org.videolan.vlc.gui.view.SwipeRefreshLayout;
import org.videolan.vlc.interfaces.Filterable;
import org.videolan.vlc.interfaces.IEventsHandler;
import org.videolan.vlc.interfaces.IRefreshable;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.WeakHandler;

import java.util.ArrayList;
import java.util.LinkedList;


public abstract class BaseBrowserFragment extends SortableFragment<BaseBrowserAdapter> implements IRefreshable, MediaBrowser.EventListener, SwipeRefreshLayout.OnRefreshListener, View.OnClickListener, Filterable, IEventsHandler {
    protected static final String TAG = "VLC/BaseBrowserFragment";

    public static final String KEY_MRL = "key_mrl";
    public static final String KEY_MEDIA = "key_media";
    public static final String KEY_MEDIA_LIST = "key_media_list";
    public static final String KEY_CONTENT_LIST = "key_content_list";
    public static final String KEY_POSITION = "key_list";

    public volatile boolean refreshing = false;
    private ArrayList<MediaLibraryItem> refreshList;

    protected BrowserFragmentHandler mHandler;
    protected MediaBrowser mMediaBrowser;
    protected ContextMenuRecyclerView mRecyclerView;
    private View mSearchButtonView;
    protected LinearLayoutManager mLayoutManager;
    protected TextView mEmptyView;
    public String mMrl;
    protected MediaWrapper mCurrentMedia;
    protected int mSavedPosition = -1, mFavorites = 0;
    public boolean mRoot;
    protected boolean goBack = false;
    private final boolean mShowHiddenFiles;

    private SimpleArrayMap<MediaLibraryItem, ArrayList<MediaLibraryItem>> mFoldersContentLists;
    public int mCurrentParsedPosition = 0;

    protected abstract Fragment createFragment();
    protected abstract void browseRoot();
    protected abstract String getCategoryTitle();

    private Handler mBrowserHandler;

    protected void runOnBrowserThread(Runnable runnable) {
        if (Looper.myLooper() == mBrowserHandler.getLooper())
            runnable.run();
        else
            mBrowserHandler.post(runnable);
    }

    public BaseBrowserFragment() {
        mHandler = new BrowserFragmentHandler(this);
        mAdapter = new BaseBrowserAdapter(this);
        if (mBrowserHandler == null) {
            HandlerThread handlerThread = new HandlerThread("vlc-browser", Process.THREAD_PRIORITY_DEFAULT+Process.THREAD_PRIORITY_LESS_FAVORABLE);
            handlerThread.start();
            mBrowserHandler = new Handler(handlerThread.getLooper());
        }
        mShowHiddenFiles = PreferenceManager.getDefaultSharedPreferences(VLCApplication.getAppContext()).getBoolean("browser_show_hidden_files", false);
    }

    @SuppressWarnings("unchecked")
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        if (bundle == null)
            bundle = getArguments();
        if (bundle != null) {
            if (VLCApplication.hasData(KEY_CONTENT_LIST))
                mFoldersContentLists = (SimpleArrayMap<MediaLibraryItem, ArrayList<MediaLibraryItem>>) VLCApplication.getData(KEY_CONTENT_LIST);
            mCurrentMedia = bundle.getParcelable(KEY_MEDIA);
            if (mCurrentMedia != null)
                mMrl = mCurrentMedia.getLocation();
            else
                mMrl = bundle.getString(KEY_MRL);
            mSavedPosition = bundle.getInt(KEY_POSITION);
        } else if (getActivity().getIntent() != null){
            mMrl = getActivity().getIntent().getDataString();
            getActivity().setIntent(null);
        }
        mRoot = defineIsRoot();
        if (mFoldersContentLists == null)
            mFoldersContentLists = new SimpleArrayMap<>();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.ml_menu_filter).setVisible(enableSearchOption());
    }

    protected int getLayoutId(){
        return R.layout.directory_browser;
    }

    protected boolean defineIsRoot() {
        return mMrl == null;
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(getLayoutId(), container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mRecyclerView = view.findViewById(R.id.network_list);
        mEmptyView = view.findViewById(android.R.id.empty);
        mSwipeRefreshLayout = view.findViewById(R.id.swipeLayout);
        mSearchButtonView = view.findViewById(R.id.searchButton);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mLayoutManager = new LinearLayoutManager(getActivity());
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setAdapter(mAdapter);
        registerForContextMenu(mRecyclerView);
        mSwipeRefreshLayout.setOnRefreshListener(this);

        VLCApplication.storeData(KEY_CONTENT_LIST+mMrl, mFoldersContentLists);
        @SuppressWarnings("unchecked")
        final SimpleArrayMap<MediaLibraryItem, ArrayList<MediaLibraryItem>> content = (SimpleArrayMap<MediaLibraryItem, ArrayList<MediaLibraryItem>>) VLCApplication.getData(KEY_CONTENT_LIST + mMrl);
        if (content != null)
            mFoldersContentLists = content;
        @SuppressWarnings("unchecked")
        final ArrayList<MediaLibraryItem> mediaList = (ArrayList<MediaLibraryItem>) VLCApplication.getData(KEY_MEDIA_LIST + mMrl);
        if (!Util.isListEmpty(mediaList))
            mAdapter.update(mediaList);
        else
            refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mCurrentMedia != null)
        setSearchVisibility(false);
        if (goBack)
            goBack();
        else
            restoreList();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            releaseBrowser();
        } else if (mFabPlay != null) {
            mFabPlay.setImageResource(R.drawable.ic_fab_play);
            updateFab();
        }
    }

    private void releaseBrowser() {
        runOnBrowserThread(new Runnable() {
            @Override
            public void run() {
                if (mMediaBrowser != null) {
                    mMediaBrowser.release();
                    mMediaBrowser = null;
                }
            }
        });
    }

    public void onSaveInstanceState(Bundle outState){
        outState.putString(KEY_MRL, mMrl);
        outState.putParcelable(KEY_MEDIA, mCurrentMedia);
        VLCApplication.storeData(KEY_MEDIA_LIST+mMrl, mAdapter.getAll());
        VLCApplication.storeData(KEY_CONTENT_LIST+mMrl, mFoldersContentLists);
        if (mRecyclerView != null)
            outState.putInt(KEY_POSITION, mLayoutManager.findFirstCompletelyVisibleItemPosition());
        super.onSaveInstanceState(outState);
    }

    public boolean isRootDirectory(){
        return mRoot;
    }

    public String getTitle(){
        if (mRoot)
            return getCategoryTitle();
        else
            return mCurrentMedia != null ? mCurrentMedia.getTitle() : mMrl;
    }

    public String getSubTitle(){
        if (mRoot)
            return null;
        String mrl = Strings.removeFileProtocole(mMrl);
        if (!TextUtils.isEmpty(mrl)) {
            if (this instanceof FileBrowserFragment && mrl.startsWith(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY))
                mrl = getString(R.string.internal_memory)+mrl.substring(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY.length());
            mrl = Uri.decode(mrl).replaceAll("://", " ").replaceAll("/", " > ");
        }
        return mCurrentMedia != null ? mrl : null;
    }

    public void goBack(){
        final FragmentActivity activity = getActivity();
        if (activity == null)
            return;
        if (!mRoot) {
            if (!activity.getSupportFragmentManager().popBackStackImmediate() && activity instanceof MainActivity)
                ((MainActivity)activity).showFragment(this instanceof NetworkBrowserFragment ? R.id.nav_network : R.id.nav_directories);
        } else
            activity.finish();
    }

    public void browse(MediaWrapper media, int position, boolean save) {
        if (!isResumed() || isRemoving())
            return;
        mBrowserHandler.removeCallbacksAndMessages(null);
        final FragmentTransaction ft = getActivity().getSupportFragmentManager().beginTransaction();
        final Fragment next = createFragment();
        final Bundle args = new Bundle();
        VLCApplication.storeData(KEY_MEDIA_LIST+mMrl, mAdapter.getAll());
        VLCApplication.storeData(KEY_CONTENT_LIST+ mMrl, mFoldersContentLists);
        final ArrayList<MediaLibraryItem> list = mFoldersContentLists.get(media);
        if (!Util.isListEmpty(list) && !(this instanceof StorageBrowserFragment))
            VLCApplication.storeData(KEY_MEDIA_LIST+ media.getLocation(), list);
        args.putParcelable(KEY_MEDIA, media);
        next.setArguments(args);
        if (isRootDirectory())
            ft.hide(this);
        else
            ft.remove(this);
        if (save)
            ft.addToBackStack(mRoot ? "root" : mMrl);
        ft.add(R.id.fragment_placeholder, next, media.getLocation());
        ft.commit();
    }

    @Override
    public void onMediaAdded(final int index, final Media media) {
        if (refreshing && !mRoot) {
            MediaWrapper mediaWrapper = getMediaWrapper(new MediaWrapper(media));
            refreshList.add(mediaWrapper);
            return;
        }
        final boolean wasEmpty = mAdapter.isEmpty();
        final MediaWrapper mediaWrapper = getMediaWrapper(new MediaWrapper(media));
        VLCApplication.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                publishMedia(mediaWrapper, wasEmpty);
            }
        });
    }

    private void publishMedia(MediaWrapper mw, boolean wasEmpty) {
        mAdapter.addItem(mw, false);
        if (wasEmpty) {
            updateEmptyView();
            mHandler.sendEmptyMessage(BrowserFragmentHandler.MSG_HIDE_LOADING);
        }
    }

    @Override
    public void onMediaRemoved(int index, final Media media) {
        VLCApplication.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                mAdapter.removeItem(media.getUri().toString());
            }
        });
    }

    @Override
    public void onBrowseEnd() {
        if (!isAdded())
            return;
        if (refreshing && !mRoot) {
            refreshing = false;
            mAdapter.update(refreshList);
        } else
            refreshList = null;

        mHandler.sendEmptyMessage(BrowserFragmentHandler.MSG_HIDE_LOADING);
        releaseBrowser();
        VLCApplication.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                onUpdateFinished(mAdapter);
            }
        });
    }

    @Override
    public void onRefresh() {
        mSavedPosition = mLayoutManager.findFirstCompletelyVisibleItemPosition();
        refresh();
    }

    /**
     * Update views visibility and emptiness info
     */
    protected void updateEmptyView() {
        if (mSwipeRefreshLayout == null)
            return;
        if (mAdapter.isEmpty()) {
            if (mSwipeRefreshLayout.isRefreshing()) {
                mEmptyView.setText(R.string.loading);
                mEmptyView.setVisibility(View.VISIBLE);
                mRecyclerView.setVisibility(View.GONE);
            } else {
                mEmptyView.setText(R.string.directory_empty);
                mEmptyView.setVisibility(View.VISIBLE);
                mRecyclerView.setVisibility(View.GONE);
            }
        } else if (mEmptyView.getVisibility() == View.VISIBLE) {
            mEmptyView.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void refresh() {
        if (isSortEnabled()) {
            refreshList = new ArrayList<>();
            refreshing = true;
        } else
            mAdapter.clear();
        mBrowserHandler.removeCallbacksAndMessages(null);
        mHandler.sendEmptyMessageDelayed(BrowserFragmentHandler.MSG_SHOW_LOADING, 300);

        runOnBrowserThread(new Runnable() {
            @Override
            public void run() {
                if (mFoldersContentLists != null)
                    mFoldersContentLists.clear();
                initMediaBrowser(BaseBrowserFragment.this);
                mCurrentParsedPosition = 0;
                if (mRoot)
                    VLCApplication.runOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            browseRoot();
                        }
                    });
                else
                    browse(mCurrentMedia != null ? mCurrentMedia.getUri() : Uri.parse(mMrl), getBrowserFlags());
            }
        });
    }

    private void browse(final Uri uri, final int flags) {
        runOnBrowserThread(new Runnable() {
            @Override
            public void run() {
                mMediaBrowser.browse(uri, flags);
            }
        });
    }

    protected void initMediaBrowser(final MediaBrowser.EventListener listener) {
        runOnBrowserThread(new Runnable() {
            @Override
            public void run() {
                if (mMediaBrowser == null)
                    mMediaBrowser = new MediaBrowser(VLCInstance.get(), listener, mBrowserHandler);
                else
                    mMediaBrowser.changeEventListener(listener);
            }
        });
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.fab:
                playAll(null);
        }
    }

    protected int getBrowserFlags() {
        int flags = MediaBrowser.Flag.Interact;
        if (mShowHiddenFiles)
            flags |= MediaBrowser.Flag.ShowHiddenFiles;
        return flags;
    }

    static class BrowserFragmentHandler extends WeakHandler<BaseBrowserFragment> {

        static final int MSG_SHOW_LOADING = 0;
        static final int MSG_HIDE_LOADING = 1;
        static final int MSG_REFRESH = 3;

        BrowserFragmentHandler(BaseBrowserFragment owner) {
            super(owner);
        }
        @Override
        public void handleMessage(Message msg) {
            final BaseBrowserFragment fragment = getOwner();
            if (fragment == null)
                return;
            switch (msg.what){
                case MSG_SHOW_LOADING:
                    if (fragment.mSwipeRefreshLayout != null)
                        fragment.mSwipeRefreshLayout.setRefreshing(true);
                    break;
                case MSG_HIDE_LOADING:
                    removeMessages(MSG_SHOW_LOADING);
                    if (fragment.mSwipeRefreshLayout != null)
                        fragment.mSwipeRefreshLayout.setRefreshing(false);
                    break;
                case MSG_REFRESH:
                    if (fragment != null && !fragment.isDetached())
                        fragment.refresh();
            }
        }
    }

    public void clear(){
        mAdapter.clear();
    }

    @Override
    protected void inflate(Menu menu, int position) {
        MediaWrapper mw = (MediaWrapper) mAdapter.getItem(position);
        int type = mw.getType();
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(type == MediaWrapper.TYPE_DIR ? R.menu.directory_view_dir : R.menu.directory_view_file, menu);
    }

    protected void setContextMenuItems(Menu menu, int position) {
        final MediaWrapper mw = (MediaWrapper) mAdapter.getItem(position);
        final int type = mw.getType();
        boolean canWrite = this instanceof FileBrowserFragment && FileUtils.canWrite(mw.getUri().getPath());
        if (type == MediaWrapper.TYPE_DIR) {
            final boolean isEmpty = Util.isListEmpty(mFoldersContentLists.get(mw));
//                if (canWrite) {
//                    boolean nomedia = new File(mw.getLocation() + "/.nomedia").exists();
//                    menu.findItem(R.id.directory_view_hide_media).setVisible(!nomedia);
//                    menu.findItem(R.id.directory_view_show_media).setVisible(nomedia);
//                } else {
//                    menu.findItem(R.id.directory_view_hide_media).setVisible(false);
//                    menu.findItem(R.id.directory_view_show_media).setVisible(false);
//                }
            menu.findItem(R.id.directory_view_play_folder).setVisible(!isEmpty);
            menu.findItem(R.id.directory_view_delete).setVisible(canWrite);
            if (this instanceof NetworkBrowserFragment) {
                MediaDatabase db = MediaDatabase.getInstance();
                if (db.networkFavExists(mw.getUri())) {
                    menu.findItem(R.id.network_remove_favorite).setVisible(true);
                    menu.findItem(R.id.network_edit_favorite).setVisible(!TextUtils.equals(mw.getUri().getScheme(), "upnp"));
                } else
                    menu.findItem(R.id.network_add_favorite).setVisible(true);
            }
        } else {
            boolean canPlayInList =  mw.getType() == MediaWrapper.TYPE_AUDIO ||
                    (mw.getType() == MediaWrapper.TYPE_VIDEO && AndroidUtil.isHoneycombOrLater);
            menu.findItem(R.id.directory_view_play_all).setVisible(canPlayInList);
            menu.findItem(R.id.directory_view_append).setVisible(canPlayInList);
            menu.findItem(R.id.directory_view_delete).setVisible(canWrite);
            menu.findItem(R.id.directory_view_info).setVisible(type == MediaWrapper.TYPE_VIDEO || type == MediaWrapper.TYPE_AUDIO);
            menu.findItem(R.id.directory_view_play_audio).setVisible(type != MediaWrapper.TYPE_AUDIO);
            menu.findItem(R.id.directory_view_add_playlist).setVisible(type == MediaWrapper.TYPE_AUDIO);
            menu.findItem(R.id.directory_subtitles_download).setVisible(type == MediaWrapper.TYPE_VIDEO);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void openContextMenu(final int position) {
        mRecyclerView.openContextMenu(position);
    }

    protected boolean handleContextItemSelected(MenuItem item, final int position) {
        int id = item.getItemId();
        if (! (mAdapter.getItem(position) instanceof MediaWrapper))
            return super.onContextItemSelected(item);
        Uri uri = ((MediaWrapper) mAdapter.getItem(position)).getUri();
        MediaWrapper mwFromMl = "file".equals(uri.getScheme()) ? mMediaLibrary.getMedia(uri) : null;
        final MediaWrapper mw = mwFromMl != null ? mwFromMl : (MediaWrapper) mAdapter.getItem(position);
        switch (id){
            case R.id.directory_view_play_all:
                mw.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
                playAll(mw);
                return true;
            case R.id.directory_view_append: {
                if (mService != null)
                    mService.append(mw);
                return true;
            }
            case R.id.directory_view_delete:
                mAdapter.removeItem(position);
                UiTools.snackerWithCancel(getView(), getString(R.string.file_deleted), new Runnable() {
                    @Override
                    public void run() {
                        deleteMedia(mw, false);
                    }
                }, new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.addItem(mw, true, position);
                    }
                });
                return true;
            case  R.id.directory_view_info:
                showMediaInfo(mw);
                return true;
            case R.id.directory_view_play_audio:
                if (mService != null) {
                    mw.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
                    mService.load(mw);
                }
                return true;
            case R.id.directory_view_play_folder:
                ArrayList<MediaWrapper> newMediaList = new ArrayList<>();
                for (MediaLibraryItem mediaItem : mFoldersContentLists.get(mAdapter.get(position))){
                    if (((MediaWrapper)mediaItem).getType() == MediaWrapper.TYPE_AUDIO || (AndroidUtil.isHoneycombOrLater && ((MediaWrapper)mediaItem).getType() == MediaWrapper.TYPE_VIDEO))
                        newMediaList.add((MediaWrapper)mediaItem);
                }
                MediaUtils.openList(getActivity(), newMediaList, 0);
                return true;
            case R.id.directory_view_add_playlist:
                FragmentManager fm = getActivity().getSupportFragmentManager();
                SavePlaylistDialog savePlaylistDialog = new SavePlaylistDialog();
                Bundle infoArgs = new Bundle();
                infoArgs.putParcelableArray(SavePlaylistDialog.KEY_NEW_TRACKS, mw.getTracks());
                savePlaylistDialog.setArguments(infoArgs);
                savePlaylistDialog.show(fm, "fragment_add_to_playlist");
                return true;
            case R.id.directory_subtitles_download:
                MediaUtils.getSubs(getActivity(), mw);
                return true;
//            case R.id.directory_view_hide_media:
//                try {
//                    if (new File(mw.getLocation()+"/.nomedia").createNewFile())
//                        updateLib();
//                } catch (IOException e) {}
//                return true;
//            case R.id.directory_view_show_media:
//                if (new File(mw.getLocation()+"/.nomedia").delete())
//                    updateLib();
//                return true;

        }
        return false;
    }

    private void showMediaInfo(MediaWrapper mw) {
        Intent i = new Intent(getActivity(), InfoActivity.class);
        i.putExtra(InfoActivity.TAG_ITEM, mw);
        startActivity(i);
    }

    private void playAll(MediaWrapper mw) {
        int positionInPlaylist = 0;
        LinkedList<MediaWrapper> mediaLocations = new LinkedList<>();
        MediaWrapper media;
        for (Object file : mAdapter.getAll())
            if (file instanceof MediaWrapper) {
                media = (MediaWrapper) file;
                if ((AndroidUtil.isHoneycombOrLater && media.getType() == MediaWrapper.TYPE_VIDEO) || media.getType() == MediaWrapper.TYPE_AUDIO) {
                    mediaLocations.add(media);
                    if (mw != null && media.equals(mw))
                        positionInPlaylist = mediaLocations.size() - 1;
                }
            }
        MediaUtils.openList(getActivity(), mediaLocations, positionInPlaylist);
    }

    ArrayList<MediaLibraryItem> currentMediaList = new ArrayList<>();
    protected void parseSubDirectories() {
        synchronized (currentMediaList) {
            currentMediaList = new ArrayList<>(mAdapter.getAll());
            if ((mRoot && this instanceof NetworkBrowserFragment) || mCurrentParsedPosition == -1 ||
                    currentMediaList.isEmpty() || this instanceof FilePickerFragment)
                return;
        }
        runOnBrowserThread(new Runnable() {
            @Override
            public void run() {
                synchronized (currentMediaList) {
                    mFoldersContentLists.clear();
                    initMediaBrowser(mFoldersBrowserListener);
                    mCurrentParsedPosition = 0;
                    MediaLibraryItem item;
                    MediaWrapper mw;
                    while (mCurrentParsedPosition < currentMediaList.size()) {
                        item = currentMediaList.get(mCurrentParsedPosition);
                        if (item.getItemType() == MediaLibraryItem.TYPE_STORAGE) {
                            mw = new MediaWrapper(((Storage) item).getUri());
                            mw.setType(MediaWrapper.TYPE_DIR);
                        } else if (item.getItemType() == MediaLibraryItem.TYPE_MEDIA) {
                            mw = (MediaWrapper) item;
                        } else
                            mw = null;
                        if (mw != null) {
                            if (mw.getType() == MediaWrapper.TYPE_DIR || mw.getType() == MediaWrapper.TYPE_PLAYLIST) {
                                final Uri uri = mw.getUri();
                                browse(uri, mShowHiddenFiles ? MediaBrowser.Flag.ShowHiddenFiles : 0);
                                return;
                            }
                        }
                        ++mCurrentParsedPosition;
                    }
                }
            }
        });
    }

    private MediaBrowser.EventListener mFoldersBrowserListener = new MediaBrowser.EventListener(){
        ArrayList<MediaWrapper> directories = new ArrayList<>();
        ArrayList<MediaWrapper> files = new ArrayList<>();

        @Override
        public void onMediaAdded(int index, final Media media) {
            final int type = media.getType();
            final MediaWrapper mw = getMediaWrapper(new MediaWrapper(media));
            if (type == Media.Type.Directory)
                directories.add(mw);
            else if (type == Media.Type.File)
                files.add(mw);
        }

        @Override
        public void onMediaRemoved(int index, Media media) {}

        @Override
        public void onBrowseEnd() {
            synchronized (currentMediaList) {
                if (currentMediaList.isEmpty() || !isAdded()) {
                    mCurrentParsedPosition = -1;
                    releaseBrowser();
                    return;
                }
                final String holderText = getDescription(directories.size(), files.size());
                MediaWrapper mw = null;

                if (!TextUtils.equals(holderText, "")) {
                    MediaLibraryItem item = currentMediaList.get(mCurrentParsedPosition);
                    item.setDescription(holderText);
                    final int position = mCurrentParsedPosition;
                    VLCApplication.runOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            mAdapter.notifyItemChanged(position, holderText);
                        }
                    });
                    directories.addAll(files);
                    mFoldersContentLists.put(item, new ArrayList<MediaLibraryItem>(directories));
                }
                while (++mCurrentParsedPosition < currentMediaList.size()){ //skip media that are not browsable
                    MediaLibraryItem item = currentMediaList.get(mCurrentParsedPosition);
                    if (item.getItemType() == MediaLibraryItem.TYPE_MEDIA) {
                        mw = (MediaWrapper) item;
                        if (mw.getType() == MediaWrapper.TYPE_DIR || mw.getType() == MediaWrapper.TYPE_PLAYLIST)
                            break;
                    } else if (item.getItemType() == MediaLibraryItem.TYPE_STORAGE) {
                        mw = new MediaWrapper(((Storage) item).getUri());
                        break;
                    } else
                        mw = null;
                }

                if (mw != null) {
                    if (mCurrentParsedPosition < currentMediaList.size()) {
                        browse(mw.getUri(), 0);
                    } else {
                        mCurrentParsedPosition = -1;
                        currentMediaList = new ArrayList<>();
                        releaseBrowser();
                    }
                } else {
                    releaseBrowser();
                    currentMediaList = new ArrayList<>();
                }
                directories .clear();
                files.clear();
            }
        }

        private String getDescription(int folderCount, int mediaFileCount) {
            String holderText = "";
            if (folderCount > 0) {
                holderText += VLCApplication.getAppResources().getQuantityString(
                        R.plurals.subfolders_quantity, folderCount, folderCount
                );
                if (mediaFileCount > 0)
                    holderText += ", ";
            }
            if (mediaFileCount > 0)
                holderText += VLCApplication.getAppResources().getQuantityString(
                        R.plurals.mediafiles_quantity, mediaFileCount,
                        mediaFileCount);
            else if (folderCount == 0 && mediaFileCount == 0)
                holderText = getString(R.string.directory_empty);
            return holderText;
        }
    };

    @NonNull
    private MediaWrapper getMediaWrapper(MediaWrapper media) {
        MediaWrapper mw = null;
        Uri uri = media.getUri();
        if ((media.getType() == MediaWrapper.TYPE_AUDIO
                || media.getType() == MediaWrapper.TYPE_VIDEO)
                && "file".equals(uri.getScheme()))
            mw = mMediaLibrary.getMedia(uri);
        if (mw == null)
            return media;
        return mw;
    }

    @Override
    public boolean enableSearchOption() {
        return !isRootDirectory();
    }

    public Filter getFilter() {
        return mAdapter.getFilter();
    }

    public void restoreList() {
        if (mAdapter != null && mEmptyView != null)
            mAdapter.restoreList();
    }
    public void setSearchVisibility(boolean visible) {
        UiTools.setViewVisibility(mSearchButtonView, visible ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.action_mode_browser_file, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        int count = mAdapter.getSelectionCount();
        if (count == 0) {
            stopActionMode();
            return false;
        }
        boolean single = this instanceof FileBrowserFragment && count == 1;
        final ArrayList<MediaWrapper> selection = single ? mAdapter.getSelection() : null;
        int type = !Util.isListEmpty(selection) ? selection.get(0).getType() : -1;
        menu.findItem(R.id.action_mode_file_info).setVisible(single && (type == MediaWrapper.TYPE_AUDIO || type == MediaWrapper.TYPE_VIDEO));
        menu.findItem(R.id.action_mode_file_append).setVisible(mService.hasMedia());
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        ArrayList<MediaWrapper> list = mAdapter.getSelection();
        if (!list.isEmpty()) {
            switch (item.getItemId()) {
                case R.id.action_mode_file_play:
                    mService.load(list, 0);
                    break;
                case R.id.action_mode_file_append:
                    mService.append(list);
                    break;
                case R.id.action_mode_file_delete:
                    for (MediaWrapper media : list)
                        deleteMedia(media, true);
                    break;
                case R.id.action_mode_file_add_playlist:
                    UiTools.addToPlaylist(getActivity(), list);
                    break;
                case R.id.action_mode_file_info:
                    showMediaInfo(list.get(0));
                    break;
                default:
                    stopActionMode();
                    return false;
            }
        }
        stopActionMode();
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mActionMode = null;
        int index = -1;
        for (MediaLibraryItem media : mAdapter.getAll()) {
            ++index;
            if (media.hasStateFlags(MediaLibraryItem.FLAG_SELECTED)) {
                media.removeStateFlags(MediaLibraryItem.FLAG_SELECTED);
                mAdapter.notifyItemChanged(index, media);
            }
        }
        mAdapter.resetSelectionCount();
    }

    public void onClick(View v, int position, MediaLibraryItem item) {
        MediaWrapper mediaWrapper = (MediaWrapper) item;
            if (mActionMode != null) {
                if (mediaWrapper.getType() == MediaWrapper.TYPE_AUDIO ||
                        mediaWrapper.getType() == MediaWrapper.TYPE_VIDEO ||
                        mediaWrapper.getType() == MediaWrapper.TYPE_DIR) {
                    item.toggleStateFlag(MediaLibraryItem.FLAG_SELECTED);
                    mAdapter.updateSelectionCount(mediaWrapper.hasStateFlags(MediaLibraryItem.FLAG_SELECTED));
                    mAdapter.notifyItemChanged(position, item);
                    invalidateActionMode();
                }
            } else {
                mediaWrapper.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
                if (mediaWrapper.getType() == MediaWrapper.TYPE_DIR)
                    browse(mediaWrapper, position, true);
                else
                    MediaUtils.openMedia(v.getContext(), mediaWrapper);
            }
    }

    public boolean onLongClick(View v, int position, MediaLibraryItem item) {
        if (mActionMode != null)
            return false;
        MediaWrapper mediaWrapper = (MediaWrapper) item;
        if (mediaWrapper.getType() == MediaWrapper.TYPE_AUDIO ||
                mediaWrapper.getType() == MediaWrapper.TYPE_VIDEO ||
                mediaWrapper.getType() == MediaWrapper.TYPE_DIR) {
            if (mActionMode != null)
                return false;
            item.setStateFlags(MediaLibraryItem.FLAG_SELECTED);
            mAdapter.updateSelectionCount(mediaWrapper.hasStateFlags(MediaLibraryItem.FLAG_SELECTED));
            mAdapter.notifyItemChanged(position, item);
            startActionMode();
        } else
            mRecyclerView.openContextMenu(position);
        return true;
    }

    public void onCtxClick(View v, int position, MediaLibraryItem item) {
        if (mActionMode == null && item.getItemType() == MediaLibraryItem.TYPE_MEDIA)
            mRecyclerView.openContextMenu(position);
    }

    public void onUpdateFinished(RecyclerView.Adapter adapter) {
        mHandler.sendEmptyMessage(BrowserFragmentHandler.MSG_HIDE_LOADING);
        updateEmptyView();
        if (!mAdapter.isEmpty()) {
            parseSubDirectories();
            if (mSavedPosition > 0) {
                mLayoutManager.scrollToPositionWithOffset(mSavedPosition, 0);
                mSavedPosition = 0;
            }
        }
        if (!mRoot)
            updateFab();
    }

    private void updateFab() {
        if (mFabPlay != null) {
            if (mAdapter.getMediaCount() > 0) {
                mFabPlay.setVisibility(View.VISIBLE);
                mFabPlay.setOnClickListener(this);
            } else {
                mFabPlay.setVisibility(View.GONE);
                mFabPlay.setOnClickListener(null);
            }
        }
    }

    public boolean isSortEnabled() {
        return false;
    }
}
