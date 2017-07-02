package com.example.aleks.simplechatroulette;

import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.annotation.NonNull;
import android.Manifest;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.app.AlertDialog;

import com.example.aleks.simplechatroulette.services.WebCoordinatorService;
import com.google.gson.Gson;
import com.opentok.android.Session;
import com.opentok.android.Stream;
import com.opentok.android.Publisher;
import com.opentok.android.PublisherKit;
import com.opentok.android.Subscriber;
import com.opentok.android.BaseVideoRenderer;
import com.opentok.android.OpentokError;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity
        implements EasyPermissions.PermissionCallbacks,
        Session.SessionListener,
        PublisherKit.PublisherListener {
    private static final String LOG_TAG = MainActivity.class.getSimpleName();

    private static final int RC_VIDEO_APP_PERM = 0;

    private DataFragment dataFragment;

    private Session session;
    private Publisher publisher;
    private Subscriber subscriber;
    private String token;

    @BindView(R.id.publisher_container)
    FrameLayout publisherViewContainer;
    @BindView(R.id.subscriber_container)
    FrameLayout subscriberViewContainer;

    WebCoordinatorService webCoordinatorService;
    // flag indicating whether we have called bind on the service
    boolean serviceBound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(LOG_TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(MainActivity.this);

        // initialize view objects from your layout
        publisherViewContainer = (FrameLayout)findViewById(R.id.publisher_container);
        subscriberViewContainer = (FrameLayout)findViewById(R.id.subscriber_container);

        FragmentManager fm = getFragmentManager();
        dataFragment = (DataFragment) fm.findFragmentByTag("data");
        if (dataFragment == null) {
            dataFragment = new DataFragment();
            fm.beginTransaction().add(dataFragment, "data").commit();
        }
        else {
            session = dataFragment.getSession();
            publisher = dataFragment.getPublisher();
            subscriber = dataFragment.getSubscriber();
            token = dataFragment.getToken();
            if (session != null)
                session.setSessionListener(this);
            if (publisher != null) {
                publisher.setPublisherListener(this);
            }
        }

        requestPermissions();
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            WebCoordinatorService.LocalBinder binder = (WebCoordinatorService.LocalBinder) service;
            webCoordinatorService = binder.getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            serviceBound = false;
            webCoordinatorService = null;
        }
    };

    @Override
    protected void onPause() {
        Log.d(LOG_TAG, "onPause");
        super.onPause();
        if (session == null) {
            return;
        }
        session.onPause();
        if (isFinishing()) {
            disconnectSession();
        }
    }

    @Override
    protected void onResume() {
        Log.d(LOG_TAG, "onResume");
        super.onResume();
        if (session != null) {
            session.onResume();
        }
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(messageReceiver, new IntentFilter("WebCoordinatorServiceEvents"));
    }

    @Override
    public void onStart() {
        super.onStart();
        bindService(new Intent(this, WebCoordinatorService.class), serviceConnection, Context.BIND_AUTO_CREATE);
        if (publisher != null)
            publisherViewContainer.addView(publisher.getView());
        if (subscriber != null)
            subscriberViewContainer.addView(subscriber.getView());
    }

    @Override
    public void onStop() {
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
        publisherViewContainer.removeAllViews();
        subscriberViewContainer.removeAllViews();

        dataFragment.setSession(session);
        dataFragment.setPublisher(publisher);
        dataFragment.setSubscriber(subscriber);
        dataFragment.setToken(token);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(LOG_TAG, "onDestroy");
        super.onDestroy();
    }

    private BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String eventType = intent.getStringExtra("eventType");
            String message = intent.getStringExtra("message");
            if (eventType.equals(WebCoordinatorService.WebCoordinatorServiceEventType.SessionConnectionDataReadyEvent.toString())) {
                WebCoordinatorService.SessionConnectionDataReadyEvent messageObj = new Gson().fromJson(message,
                        WebCoordinatorService.SessionConnectionDataReadyEvent.class);
                onSessionConnectionDataReady(messageObj);
            }
            else if (eventType.equals(WebCoordinatorService.WebCoordinatorServiceEventType.SessionConnectionDataReadyEvent.toString())) {
                WebCoordinatorService.WebCoordinatorServiceErrorEvent messageObj = new Gson().fromJson(message,
                        WebCoordinatorService.WebCoordinatorServiceErrorEvent.class);
                onWebServiceCoordinatorError(messageObj);
            }
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted(int requestCode, List<String> perms) {
        Log.d(LOG_TAG, "onPermissionsGranted:" + requestCode + ":" + perms.size());
    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> perms) {
        Log.d(LOG_TAG, "onPermissionsDenied:" + requestCode + ":" + perms.size());
        showFinishingDialog("Sorry", "This app cannot work correctly without the requested permissions");
    }

    @AfterPermissionGranted(RC_VIDEO_APP_PERM)
    private void requestPermissions() {
        String[] perms = { Manifest.permission.INTERNET, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO };
        if (EasyPermissions.hasPermissions(this, perms)) {
            // initialize WebServiceCoordinator and kick off request for session data
            // session initialization occurs once data is returned, in onSessionConnectionDataReady
            startService(new Intent(this, WebCoordinatorService.class));
        }
        else {
            EasyPermissions.requestPermissions(this, getString(R.string.rationale_video_app), RC_VIDEO_APP_PERM, perms);
        }
    }

    private void initializeSession(String apiKey, String sessionId, String token) {
        this.token = token;
        session = new Session.Builder(this, apiKey, sessionId).build();
        session.setSessionListener(this);
        session.connect(token);
    }

    public void onSessionConnectionDataReady(WebCoordinatorService.SessionConnectionDataReadyEvent event) {
        Log.d(LOG_TAG, "ApiKey: " + event.apiKey + " SessionId: " + event.sessionId + " Token: " + event.token);
        disconnectSession();
        initializeSession(event.apiKey, event.sessionId, event.token);
    }

    public void onWebServiceCoordinatorError(WebCoordinatorService.WebCoordinatorServiceErrorEvent event) {
        Log.e(LOG_TAG, "WebServiceCoordinatorError: " + event.errorMessage);
        showFinishingDialog("Error", event.errorMessage);
    }

    @Override
    public void onConnected(Session session) {
        Log.d(LOG_TAG, "onConnected: Connected to session: "+session.getSessionId());

        // initialize Publisher and set this object to listen to Publisher events
        publisher = new Publisher.Builder(this).build();
        publisher.setPublisherListener(this);

        // set publisher video style to fill view
        publisher.getRenderer().setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE,
                BaseVideoRenderer.STYLE_VIDEO_FILL);
        publisherViewContainer.addView(publisher.getView());

        this.session.publish(publisher);
    }

    @Override
    public void onDisconnected(Session session) {
        Log.d(LOG_TAG, "onDisconnected: Disconnected from session: "+session.getSessionId());
    }

    @Override
    public void onStreamReceived(Session session, Stream stream) {
        Log.d(LOG_TAG, "onStreamReceived: New Stream Received "+stream.getStreamId() + " in session: "+session.getSessionId());

        if (subscriber == null) {
            subscriber = new Subscriber.Builder(this, stream).build();
            subscriber.getRenderer().setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL);
            this.session.subscribe(subscriber);
            subscriberViewContainer.addView(subscriber.getView());
        }
    }

    @Override
    public void onStreamDropped(Session session, Stream stream) {
        Log.d(LOG_TAG, "onStreamDropped: Stream Dropped: "+stream.getStreamId() +" in session: "+session.getSessionId());
        if (subscriber != null) {
            subscriber = null;
            subscriberViewContainer.removeAllViews();
        }
        webCoordinatorService.fetchSessionConnectionData(this.session.getSessionId(), token);
    }

    @Override
    public void onError(Session session, OpentokError opentokError) {
        Log.e(LOG_TAG, "onError: "+ opentokError.getErrorDomain() + " : " +
                opentokError.getErrorCode() + " - "+opentokError.getMessage() + " in session: "+ session.getSessionId());

        showFinishingDialog("Error", "Opentok error has occurred");
    }

    @Override
    public void onStreamCreated(PublisherKit publisherKit, Stream stream) {
        Log.d(LOG_TAG, "onStreamCreated: Publisher Stream Created. Own stream "+stream.getStreamId());
    }

    @Override
    public void onStreamDestroyed(PublisherKit publisherKit, Stream stream) {
        Log.d(LOG_TAG, "onStreamDestroyed: Publisher Stream Destroyed. Own stream "+stream.getStreamId());
//        mWebServiceCoordinator.fetchSessionConnectionData();
    }

    @Override
    public void onError(PublisherKit publisherKit, OpentokError opentokError) {
        Log.e(LOG_TAG, "onError: "+ opentokError.getErrorDomain() + " : " +
                opentokError.getErrorCode() +  " - "+opentokError.getMessage());

        showFinishingDialog("Sorry", "Opentok error has occurred");
    }

    private void disconnectSession() {
        if (session == null) {
            return;
        }

        if (subscriber != null) {
            session.unsubscribe(subscriber);
            subscriberViewContainer.removeAllViews();
            subscriber.destroy();
            subscriber = null;
        }

        if (publisher != null) {
            session.unpublish(publisher);
            publisherViewContainer.removeAllViews();
            publisher.destroy();
            publisher = null;
        }

        session.disconnect();
    }

    void showFinishingDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNeutralButton("Ок", (dialog, which) -> MainActivity.this.finish())
                .setOnCancelListener(dialog -> MainActivity.this.finish())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

}
