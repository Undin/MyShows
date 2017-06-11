package me.myshows.android.api.impl;

import android.content.Context;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.realm.Realm;
import io.realm.Sort;
import me.myshows.android.api.ClientStorage;
import me.myshows.android.api.MyShowsApi;
import me.myshows.android.api.MyShowsClient;
import me.myshows.android.model.EpisodeComments;
import me.myshows.android.model.EpisodeInformation;
import me.myshows.android.model.Feed;
import me.myshows.android.model.NextEpisode;
import me.myshows.android.model.RatingShow;
import me.myshows.android.model.Show;
import me.myshows.android.model.UnwatchedEpisode;
import me.myshows.android.model.User;
import me.myshows.android.model.UserFeed;
import me.myshows.android.model.UserShow;
import me.myshows.android.model.UserShowEpisodes;
import me.myshows.android.model.persistent.PersistentEpisodeComments;
import me.myshows.android.model.persistent.PersistentEpisodeInformation;
import me.myshows.android.model.persistent.PersistentFeed;
import me.myshows.android.model.persistent.PersistentNextEpisode;
import me.myshows.android.model.persistent.PersistentRatingShow;
import me.myshows.android.model.persistent.PersistentShow;
import me.myshows.android.model.persistent.PersistentUnwatchedEpisode;
import me.myshows.android.model.persistent.PersistentUser;
import me.myshows.android.model.persistent.PersistentUserShow;
import me.myshows.android.model.persistent.PersistentUserShowEpisodes;
import me.myshows.android.model.persistent.dao.PersistentEntityConverter;
import me.myshows.android.model.persistent.dao.Predicate;
import me.myshows.android.model.persistent.dao.RealmManager;
import me.myshows.android.model.serialization.JsonMarshaller;
import me.myshows.android.utils.Numbers;
import me.myshows.android.utils.Objects;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Single;
import rx.schedulers.Schedulers;

/**
 * @author Whiplash
 * @date 14.06.2015
 */
public class MyShowsClientImpl implements MyShowsClient {

    private static final String API_URL = "http://api.myshows.ru";

    private static final JsonMarshaller MARSHALLER = new JsonMarshaller();
    private static final PersistentEntityConverter CONVERTER = new PersistentEntityConverter(MARSHALLER);

    private final OkHttpClient okHttpClient;
    private final ClientStorage storage;
    private final Scheduler observerScheduler;

    private final RealmManager manager;
    private final MyShowsApi api;

    private MyShowsClientImpl(@NonNull Context context, @NonNull OkHttpClient okHttpClient,
                              @NonNull ClientStorage storage, @NonNull Scheduler observerScheduler) {
        this.okHttpClient = okHttpClient;
        this.storage = storage;
        this.observerScheduler = observerScheduler;

        // TODO: 07.02.16 extract to constructor arg as part of storage
        this.manager = new RealmManager();

        this.api = new Retrofit.Builder()
                .baseUrl(API_URL)
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .addConverterFactory(JacksonConverterFactory.create(MARSHALLER.getObjectMapper()))
                .client(okHttpClient)
                .build()
                .create(MyShowsApi.class);
    }

    @Override
    public void clear() {
        Realm.deleteRealm(manager.getConfiguration());
        storage.clear();
    }

    @Override
    public Single<Boolean> authentication(Credentials credentials) {
        return Single.create(subscriber -> api.login(credentials.getLogin(), credentials.getPasswordHash())
                .subscribeOn(Schedulers.io())
                .observeOn(observerScheduler)
                .subscribe(
                        response -> {
                            storage.putCredentials(credentials);
                            subscriber.onSuccess(true);
                        },
                        e -> subscriber.onSuccess(false)
                ));
    }

    @Override
    public Single<Boolean> autoAuthentication() {
        if (!hasCredentials()) {
            return Single.error(new IllegalStateException("Client must have credentials to auto authentication"));
        }
        return authentication(storage.getCredentials());
    }

    @Override
    public Observable<User> profile() {
        return Observable.<User>create(subscriber -> {
            User user = manager.selectEntity(PersistentUser.class, CONVERTER::toUser,
                    new Predicate("login", storage.getCredentials().getLogin()));
            if (user != null) {
                subscriber.onNext(user);
            }
            api.profile()
                    .subscribe(
                            u -> subscriber.onNext(manager.upsertEntity(u, CONVERTER::fromUser)),
                            e -> subscriber.onCompleted(),
                            subscriber::onCompleted
                    );
        }).observeOn(observerScheduler).subscribeOn(Schedulers.io());
    }

    @Override
    public Observable<User> profile(String login) {
        return Observable.<User>create(subscriber -> {
            User user = manager.selectEntity(PersistentUser.class, CONVERTER::toUser,
                    new Predicate("login", login));
            if (user != null) {
                subscriber.onNext(user);
            }
            api.profile(login)
                    .subscribe(
                            u -> subscriber.onNext(manager.upsertEntity(u, CONVERTER::fromUser)),
                            e -> subscriber.onCompleted(),
                            subscriber::onCompleted
                    );
        }).observeOn(observerScheduler).subscribeOn(Schedulers.io());
    }

    @Override
    public Observable<List<UserShow>> profileShows() {
        return Observable.<List<UserShow>>create(subscriber -> {
            Class<PersistentUserShow> clazz = PersistentUserShow.class;
            List<UserShow> userShows = manager.selectEntities(clazz, CONVERTER::toUserShow);
            if (userShows != null) {
                subscriber.onNext(userShows);
            }
            api.profileShows()
                    .subscribe(
                            us -> subscriber.onNext(manager.truncateAndInsertEntities(new ArrayList<>(us.values()), clazz, CONVERTER::fromUserShow)),
                            e -> subscriber.onCompleted(),
                            subscriber::onCompleted
                    );
        }).observeOn(observerScheduler).subscribeOn(Schedulers.io());
    }

    @Override
    public Observable<UserShowEpisodes> profileEpisodesOfShow(int showId) {
        return Observable.<UserShowEpisodes>create(subscriber -> {
            Class<PersistentUserShowEpisodes> clazz = PersistentUserShowEpisodes.class;
            UserShowEpisodes episodes = manager.selectEntity(clazz, CONVERTER::toUserShowEpisodes,
                    new Predicate("showId", showId));
            if (episodes != null) {
                subscriber.onNext(episodes);
            }
            api.profileEpisodesOfShow(showId)
                    .subscribe(
                            ue -> subscriber.onNext(manager.upsertEntity(new UserShowEpisodes(showId, new ArrayList<>(ue.values())), CONVERTER::fromUserShowEpisodes)),
                            e -> subscriber.onCompleted(),
                            subscriber::onCompleted
                    );
        }).observeOn(observerScheduler).subscribeOn(Schedulers.io());
    }

    @Override
    public Observable<List<UnwatchedEpisode>> profileUnwatchedEpisodes() {
        return Observable.<List<UnwatchedEpisode>>create(subscriber -> {
            Class<PersistentUnwatchedEpisode> clazz = PersistentUnwatchedEpisode.class;
            List<UnwatchedEpisode> unwatchedEpisodes = manager.selectEntities(clazz, CONVERTER::toUnwatchedEpisode);
            if (unwatchedEpisodes != null) {
                subscriber.onNext(unwatchedEpisodes);
            }
            api.profileUnwatchedEpisodes()
                    .subscribe(
                            uep -> subscriber.onNext(manager.truncateAndInsertEntities(new ArrayList<>(uep.values()), clazz, CONVERTER::fromUnwatchedEpisode)),
                            e -> subscriber.onCompleted(),
                            subscriber::onCompleted
                    );
        }).observeOn(observerScheduler).subscribeOn(Schedulers.io());
    }

    @Override
    public Observable<List<NextEpisode>> profileNextEpisodes() {
        return Observable.<List<NextEpisode>>create(subscriber -> {
            Class<PersistentNextEpisode> clazz = PersistentNextEpisode.class;
            List<NextEpisode> unwatchedEpisodePreviews = manager.selectEntities(clazz, CONVERTER::toNextEpisode);
            if (unwatchedEpisodePreviews != null) {
                subscriber.onNext(unwatchedEpisodePreviews);
            }
            api.profileNextEpisodes()
                    .subscribe(
                            nep -> subscriber.onNext(manager.truncateAndInsertEntities(new ArrayList<>(nep.values()), clazz, CONVERTER::fromNextEpisode)),
                            e -> subscriber.onCompleted(),
                            subscriber::onCompleted
                    );
        }).observeOn(observerScheduler).subscribeOn(Schedulers.io());
    }

    @Override
    public Observable<Show> showInformation(int showId) {
        return Observable.<Show>create(subscriber -> {
            Show show = manager.selectEntity(PersistentShow.class, CONVERTER::toShow,
                    new Predicate("id", showId));
            if (show != null) {
                subscriber.onNext(show);
            }
            api.showInformation(showId)
                    .subscribe(
                            s -> subscriber.onNext(manager.upsertEntity(s, CONVERTER::fromShow)),
                            e -> subscriber.onCompleted(),
                            subscriber::onCompleted
                    );
        }).observeOn(observerScheduler).subscribeOn(Schedulers.io());
    }

    @Override
    public Observable<EpisodeInformation> episodeInformation(int episodeId) {
        return Observable.<EpisodeInformation>create(subscriber -> {
            EpisodeInformation episode = manager.selectEntity(PersistentEpisodeInformation.class, CONVERTER::toEpisodeInformation,
                    new Predicate("id", episodeId));
            if (episode != null) {
                subscriber.onNext(episode);
            }
            api.episodeInformation(episodeId)
                    .subscribe(
                            e -> subscriber.onNext(manager.upsertEntity(e, CONVERTER::fromEpisodeInformation)),
                            e -> subscriber.onCompleted(),
                            subscriber::onCompleted
                    );
        }).observeOn(observerScheduler).subscribeOn(Schedulers.io());
    }

    @Override
    public Observable<List<Feed>> friendsNews() {
        return Observable.<List<Feed>>create(subscriber -> {
            Class<PersistentFeed> clazz = PersistentFeed.class;
            List<Feed> feeds = manager.selectSortedEntities(clazz, CONVERTER::toFeed, "date", Sort.DESCENDING);
            if (feeds != null) {
                subscriber.onNext(feeds);
            }
            api.friendsNews()
                    .subscribe(
                            uf -> {
                                manager.truncateAndInsertEntities(generateFeeds(uf), PersistentFeed.class, CONVERTER::fromFeed);
                                subscriber.onNext(manager.selectSortedEntities(clazz, CONVERTER::toFeed, "date", Sort.DESCENDING));
                            },
                            e -> subscriber.onCompleted(),
                            subscriber::onCompleted
                    );
        }).observeOn(observerScheduler).subscribeOn(Schedulers.io());
    }

    public Observable<List<RatingShow>> ratingShows() {
        return Observable.<List<RatingShow>>create(subscriber -> {
            List<RatingShow> ratingShows = manager.selectSortedEntities(PersistentRatingShow.class, CONVERTER::toRatingShow, "place", Sort.ASCENDING);
            if (ratingShows != null) {
                subscriber.onNext(ratingShows);
            }
            api.ratingShows()
                    .map(shows -> {
                        Collections.sort(shows, (s1, s2) -> Numbers.compare(s1.getPlace(), s2.getPlace()));
                        return shows;
                    })
                    .subscribe(
                            shows -> subscriber.onNext(manager.truncateAndInsertEntities(shows, PersistentRatingShow.class, CONVERTER::fromRatingShow)),
                            e -> subscriber.onCompleted(),
                            subscriber::onCompleted
                    );
        }).observeOn(observerScheduler).subscribeOn(Schedulers.io());
    }

    @Override
    public Observable<EpisodeComments> comments(int episodeId) {
        return Observable.<EpisodeComments>create(subscriber -> {
            EpisodeComments information = manager.selectEntity(PersistentEpisodeComments.class,
                    CONVERTER::toEpisodeComments, new Predicate("episodeId", episodeId));
            if (information != null) {
                subscriber.onNext(information);
            }
            api.comments(episodeId)
                    .subscribe(
                            info -> subscriber.onNext(manager.upsertEntity(info, entity -> CONVERTER.fromEpisodeComments(episodeId, info))),
                            e -> subscriber.onCompleted(),
                            subscriber::onCompleted
                    );
        }).observeOn(observerScheduler).subscribeOn(Schedulers.io());
    }

    private List<Feed> generateFeeds(Map<String, List<UserFeed>> userFeeds) {
        List<Feed> feeds = new ArrayList<>();
        for (Map.Entry<String, List<UserFeed>> rawFeed : userFeeds.entrySet()) {
            feeds.add(new Feed(rawFeed.getKey(), rawFeed.getValue()));
        }
        return feeds;
    }

    @Override
    public boolean hasCredentials() {
        return storage.getCredentials() != null;
    }

    public static class Builder {

        private final Context context;

        private OkHttpClient client;
        private ClientStorage storage;
        private Scheduler scheduler;

        public Builder(@NonNull Context context) {
            this.context = context;
        }

        public Builder client(@NonNull OkHttpClient client) {
            this.client = client;
            return this;
        }

        public Builder storage(@NonNull ClientStorage storage) {
            this.storage = storage;
            return this;
        }

        public Builder observerScheduler(@NonNull Scheduler scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        public MyShowsClientImpl build() {
            Objects.requireNonNull(client, "OkHttpClient must be not null");
            Objects.requireNonNull(storage, "ClientStorage must be not null");
            Objects.requireNonNull(scheduler, "ObserverScheduler must be not null");

            return new MyShowsClientImpl(context, client, storage, scheduler);
        }
    }
}
