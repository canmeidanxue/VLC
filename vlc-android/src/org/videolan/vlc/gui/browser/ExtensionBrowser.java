package org.videolan.vlc.gui.browser;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.extensions.ExtensionListing;
import org.videolan.vlc.extensions.ExtensionManagerService;
import org.videolan.vlc.extensions.Utils;
import org.videolan.vlc.extensions.api.VLCExtensionItem;
import org.videolan.vlc.gui.view.ContextMenuRecyclerView;
import org.videolan.vlc.gui.view.SwipeRefreshLayout;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.WeakHandler;

import java.util.ArrayList;
import java.util.List;

public class ExtensionBrowser extends Fragment implements View.OnClickListener, android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener {

    public static final String TAG = "VLC/ExtensionBrowser";

    public static final String KEY_ITEMS_LIST = "key_items_list";
    public static final String KEY_SHOW_FAB = "key_fab";
    public static final String KEY_TITLE = "key_title";


    private static final int ACTION_HIDE_REFRESH = 42;
    private static final int ACTION_SHOW_REFRESH = 43;

    private static final int REFRESH_TIMEOUT = 5000;

    private String mTitle;
    private FloatingActionButton mAddDirectoryFAB;
    private ExtensionAdapter mAdapter;
    protected ContextMenuRecyclerView mRecyclerView;
    protected TextView mEmptyView;
    protected SwipeRefreshLayout mSwipeRefreshLayout;

    private ExtensionManagerService mExtensionManagerService;
    private boolean showSettings = false;
    private boolean mustBeTerminated = false;

    public void setExtensionService(ExtensionManagerService service) {
        mExtensionManagerService = service;
    }

    public ExtensionBrowser() {
        mAdapter = new ExtensionAdapter(this);
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        if (bundle == null)
            bundle = getArguments();
        if (bundle != null) {
            mTitle = bundle.getString(KEY_TITLE);
            showSettings = bundle.getBoolean(KEY_SHOW_FAB);
            List<VLCExtensionItem> list = bundle.getParcelableArrayList(KEY_ITEMS_LIST);
            if (list != null)
                mAdapter.addAll(list);
        }
        setHasOptionsMenu(true);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        View v = inflater.inflate(R.layout.directory_browser, container, false);
        mRecyclerView = v.findViewById(R.id.network_list);
        mEmptyView = v.findViewById(android.R.id.empty);
        mEmptyView.setText(R.string.extension_empty);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mRecyclerView.setAdapter(mAdapter);
        registerForContextMenu(mRecyclerView);

        mSwipeRefreshLayout = v.findViewById(R.id.swipeLayout);
        mSwipeRefreshLayout.setOnRefreshListener(this);

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mustBeTerminated)
            getActivity().getSupportFragmentManager().beginTransaction().remove(this).commit();
        mustBeTerminated = true;
    }

    @Override
    public void onStart() {
        super.onStart();
        setTitle(mTitle);
        updateDisplay();
        if (showSettings) {
            if (mAddDirectoryFAB == null) mAddDirectoryFAB = getActivity().findViewById(R.id.fab);
            mAddDirectoryFAB.setImageResource(R.drawable.ic_fab_add);
            mAddDirectoryFAB.setVisibility(View.VISIBLE);
            mAddDirectoryFAB.setOnClickListener(this);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (showSettings) {
            mAddDirectoryFAB.setVisibility(View.GONE);
            mAddDirectoryFAB.setOnClickListener(null);
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        setTitle(mTitle);
        if (mAddDirectoryFAB != null)
            mAddDirectoryFAB.setVisibility((!isHidden() && showSettings) ? View.VISIBLE : View.GONE);
    }

    private void setTitle(String title) {
        final AppCompatActivity activity = (AppCompatActivity)getActivity();
        if (activity != null && activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setTitle(title);
            getActivity().supportInvalidateOptionsMenu();
        }
    }

    public void goBack(){
        if (!getActivity().getSupportFragmentManager().popBackStackImmediate())
            getActivity().finish();
    }

    public void doRefresh(String title, List<VLCExtensionItem> items) {
        setTitle(title);
        mAdapter.addAll(items);
    }

    private void updateDisplay() {
        if (mAdapter.getItemCount() > 0) {
            mEmptyView.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
        } else {
            mEmptyView.setVisibility(View.VISIBLE);
            mRecyclerView.setVisibility(View.GONE);
        }
    }

    public void browseItem(VLCExtensionItem item) {
        mExtensionManagerService.browse(item.stringId);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == mAddDirectoryFAB.getId()){
            ExtensionListing extension = mExtensionManagerService.getCurrentExtension();
            if (extension == null)
                return;
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setComponent(extension.settingsActivity());
            startActivity(intent);
        }
    }

    @Override
    public void onRefresh() {
        mExtensionManagerService.refresh();
        mHandler.sendEmptyMessageDelayed(ACTION_HIDE_REFRESH, REFRESH_TIMEOUT);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (menuInfo == null)
            return;
        ContextMenuRecyclerView.RecyclerContextMenuInfo info = (ContextMenuRecyclerView
                .RecyclerContextMenuInfo) menuInfo;
        VLCExtensionItem item = mAdapter.getItem(info.position);
        if (item.type == VLCExtensionItem.TYPE_DIRECTORY)
            return;
        boolean isVideo = item.type == VLCExtensionItem.TYPE_VIDEO;
        getActivity().getMenuInflater().inflate(R.menu.extension_context_menu, menu);
        menu.findItem(R.id.extension_item_view_play_audio).setVisible(isVideo);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        ContextMenuRecyclerView.RecyclerContextMenuInfo info = (ContextMenuRecyclerView
                .RecyclerContextMenuInfo) item.getMenuInfo();
        return info != null && handleContextItemSelected(item, info.position);
    }

    public void openContextMenu(final int position) {
        mRecyclerView.openContextMenu(position);
    }

    protected boolean handleContextItemSelected(MenuItem item, final int position) {
        switch (item.getItemId()) {
            case R.id.extension_item_view_play_all:
                List<VLCExtensionItem> items = mAdapter.getAll();
                ArrayList<MediaWrapper> medias = new ArrayList<>(items.size());
                for (VLCExtensionItem vlcItem : items) {
                    medias.add(Utils.mediawrapperFromExtension(vlcItem));
                }
                MediaUtils.openList(getActivity(), medias, position);
                return true;
            case R.id.extension_item_view_append:
                MediaUtils.appendMedia(getActivity(), Utils.mediawrapperFromExtension(mAdapter.getItem(position)));
                return true;
            case R.id.extension_item_view_play_audio:
                MediaWrapper mw = Utils.mediawrapperFromExtension(mAdapter.getItem(position));
                mw.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
                MediaUtils.openMedia(getActivity(), mw);
                return true;
            case R.id.extension_item_download:
                //TODO
            default:return false;

        }
    }

    private Handler mHandler = new ExtensionBrowserHandler(this);

    private class ExtensionBrowserHandler extends WeakHandler<ExtensionBrowser> {

        ExtensionBrowserHandler(ExtensionBrowser owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ACTION_HIDE_REFRESH:
                    removeMessages(ACTION_SHOW_REFRESH);
                    getOwner().mSwipeRefreshLayout.setRefreshing(false);
                    break;
                case ACTION_SHOW_REFRESH:
                    removeMessages(ACTION_HIDE_REFRESH);
                    getOwner().mSwipeRefreshLayout.setRefreshing(true);
                    sendEmptyMessageDelayed(ACTION_HIDE_REFRESH, REFRESH_TIMEOUT);
                    break;
            }
        }
    }
}
