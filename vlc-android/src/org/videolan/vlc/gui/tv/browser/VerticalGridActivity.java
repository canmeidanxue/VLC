/*****************************************************************************
 * VerticalGridActivity.java
 *****************************************************************************
 * Copyright © 2014-2016 VLC authors, VideoLAN and VideoLabs
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
 *****************************************************************************/
package org.videolan.vlc.gui.tv.browser;

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.tv.MainTvActivity;
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserActivityInterface;
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserFragmentInterface;
import org.videolan.vlc.gui.tv.browser.interfaces.DetailsFragment;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class VerticalGridActivity extends BaseTvActivity implements BrowserActivityInterface {

    private static final int GRID_LIMIT = 24;
    BrowserFragmentInterface mFragment;
    ProgressBar mContentLoadingProgressBar;
    TextView mEmptyView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tv_vertical_grid);
        mContentLoadingProgressBar = (ProgressBar) findViewById(R.id.tv_fragment_progress);
        mEmptyView = (TextView) findViewById(R.id.tv_fragment_empty);
        long type = getIntent().getLongExtra(MainTvActivity.BROWSER_TYPE, -1);
        if (type == MainTvActivity.HEADER_VIDEO)
            mFragment = new VideoBrowserFragment();
        else if (type == MainTvActivity.HEADER_CATEGORIES)
            if (getIntent().getLongExtra(MusicFragment.AUDIO_CATEGORY, MusicFragment.CATEGORY_SONGS) == MusicFragment.CATEGORY_SONGS &&
                VLCApplication.getMLInstance().getAudioCount() > GRID_LIMIT) {
                mFragment = new SongsBrowserFragment();
            } else {
                mFragment = new MusicFragment();
                Bundle args = new Bundle();
                args.putParcelable(MusicFragment.AUDIO_ITEM, getIntent().getParcelableExtra(MusicFragment.AUDIO_ITEM));
                ((Fragment)mFragment).setArguments(args);
            }
        else if (type == MainTvActivity.HEADER_NETWORK) {
            Uri uri = getIntent().getData();
            if (uri == null)
                uri = getIntent().getParcelableExtra(SortedBrowserFragment.KEY_URI);
            if (uri == null)
                mFragment = new BrowserGridFragment();
            else
                mFragment = new NetworkBrowserFragment();
        } else if (type == MainTvActivity.HEADER_DIRECTORIES)
            mFragment = new DirectoryBrowserFragment();
        else {
            finish();
            return;
        }
        getSupportFragmentManager().beginTransaction()
                .add(R.id.tv_fragment_placeholder, ((Fragment)mFragment))
                .commit();
    }

    @Override
    protected void refresh() {
        mFragment.refresh();
    }

    @Override
    public void onNetworkConnectionChanged(boolean connected) {
        if (mFragment instanceof NetworkBrowserFragment)
            mFragment.updateList();
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((mFragment instanceof DetailsFragment)
                && (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_BUTTON_Y || keyCode == KeyEvent.KEYCODE_Y)) {
            ((DetailsFragment)mFragment).showDetails();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void showProgress(final boolean show){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mEmptyView.setVisibility(View.GONE);
                mContentLoadingProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    public void updateEmptyView(final boolean empty) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mEmptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
            }
        });
    }
}
