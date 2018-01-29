package org.videolan.vlc.gui;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.vlc.databinding.SearchItemBinding;
import org.videolan.vlc.gui.helpers.UiTools;


public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.ViewHolder> {

    MediaLibraryItem[] mDataList;
    SearchActivity.ClickHandler mClickHandler;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        return new ViewHolder(SearchItemBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        if (TextUtils.isEmpty(mDataList[position].getArtworkMrl()))
            holder.binding.setCover(UiTools.getDefaultCover(mDataList[position]));
        holder.binding.setItem(mDataList[position]);
    }

    public void add(MediaLibraryItem[] newList) {
        mDataList = newList;
        notifyDataSetChanged();
    }

    void setClickHandler(SearchActivity.ClickHandler clickHandler) {
        mClickHandler = clickHandler;
    }

    @Override
    public int getItemCount() {
        return mDataList == null ? 0 : mDataList.length;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        public SearchItemBinding binding;

        public ViewHolder(SearchItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.setHolder(this);
            binding.setHandler(mClickHandler);
        }
    }
}
