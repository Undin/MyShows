package me.myshows.android;

import android.app.Application;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import me.myshows.android.api.impl.MyShowsClientImpl;
import me.myshows.android.api.impl.PreferenceStorage;
import rx.android.schedulers.AndroidSchedulers;

/**
 * Created by warrior on 05.08.15.
 */
public class MyShowsApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        RealmConfiguration configuration = new RealmConfiguration.Builder(this)
                .schemaVersion(0)
                .deleteRealmIfMigrationNeeded()
                .build();
        Realm.setDefaultConfiguration(configuration);
        MyShowsClientImpl.init(new PreferenceStorage(this), AndroidSchedulers.mainThread());
    }
}
