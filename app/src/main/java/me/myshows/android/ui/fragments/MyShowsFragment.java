package me.myshows.android.ui.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import me.myshows.android.R;
import me.myshows.android.api.MyShowsClient;
import me.myshows.android.api.MyShowsClientImpl;
import me.myshows.android.entities.UserShow;
import me.myshows.android.ui.views.ListShowView;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;

/**
 * Created by warrior on 06.07.15.
 */
public class MyShowsFragment extends Fragment {

    private Subscription subscription;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.my_shows_fragment, container, false);

        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setHasFixedSize(true);

        MyShowsClient client = MyShowsClientImpl.get(getActivity());
        subscription = client.profileShows()
                .observeOn(AndroidSchedulers.mainThread())
                .toList()
                .subscribe(shows -> {
                    ShowAdapter adapter = new ShowAdapter(shows);
                    recyclerView.setAdapter(adapter);
                });

        return view;
    }

    @Override
    public void onDestroyView() {
        if (subscription != null && !subscription.isUnsubscribed()) {
            subscription.unsubscribe();
        }
        super.onDestroyView();
    }

    private static class ShowAdapter extends RecyclerView.Adapter<ShowHolder> {

        private final List<UserShow> shows;

        public ShowAdapter(List<UserShow> shows) {
            this.shows = shows;
        }

        @Override
        public ShowHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ListShowView view = (ListShowView) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.list_show_view, parent, false);
            return new ShowHolder(view);
        }

        @Override
        public void onBindViewHolder(ShowHolder holder, int position) {
            holder.bind(shows.get(position), position);
        }

        @Override
        public int getItemCount() {
            return shows.size();
        }
    }

    private static class ShowHolder extends RecyclerView.ViewHolder {

        public ShowHolder(ListShowView itemView) {
            super(itemView);
        }

        public void bind(UserShow show, int position) {
            ((ListShowView) itemView).bind(show, position);
        }
    }
}