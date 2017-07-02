package com.example.aleks.simplechatroulette;

import android.app.Fragment;
import android.os.Bundle;

import com.opentok.android.Publisher;
import com.opentok.android.Session;
import com.opentok.android.Subscriber;

/**
 * Created by aleks on 01.07.2017.
 */

public class DataFragment extends Fragment {

    private Session session;
    private Publisher publisher;
    private Subscriber subscriber;
    private String token;

    // this method is only called once for this fragment
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // retain this fragment
        setRetainInstance(true);
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public Publisher getPublisher() {
        return publisher;
    }

    public void setPublisher(Publisher publisher) {
        this.publisher = publisher;
    }

    public Subscriber getSubscriber() {
        return subscriber;
    }

    public void setSubscriber(Subscriber subscriber) {
        this.subscriber = subscriber;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
