package com.example.aleks.simplechatroulette;

import android.support.v7.app.AppCompatActivity;
import android.support.annotation.NonNull;
import android.Manifest;
import android.os.Bundle;
import android.util.Log;
import android.widget.FrameLayout;
import android.app.AlertDialog;
import android.widget.Toast;

import com.opentok.android.Session;
import com.opentok.android.Stream;
import com.opentok.android.Publisher;
import com.opentok.android.PublisherKit;
import com.opentok.android.Subscriber;
import com.opentok.android.BaseVideoRenderer;
import com.opentok.android.OpentokError;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

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

    // Suppressing this warning. mWebServiceCoordinator will get GarbageCollected if it is local.
    @SuppressWarnings("FieldCanBeLocal")
    private WebServiceCoordinator mWebServiceCoordinator;

    private Session mSession;
    private Publisher mPublisher;
    private Subscriber mSubscriber;
    private String token;

    @BindView(R.id.publisher_container)
    FrameLayout mPublisherViewContainer;
    @BindView(R.id.subscriber_container)
    FrameLayout mSubscriberViewContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(LOG_TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(MainActivity.this);

        // initialize view objects from your layout
        mPublisherViewContainer = (FrameLayout)findViewById(R.id.publisher_container);
        mSubscriberViewContainer = (FrameLayout)findViewById(R.id.subscriber_container);

        requestPermissions();
    }

    @Override
    protected void onPause() {
        Log.d(LOG_TAG, "onPause");
        super.onPause();
        if (mSession == null) {
            return;
        }
        mSession.onPause();
        if (isFinishing()) {
            disconnectSession();
        }
    }

    @Override
    protected void onResume() {
        Log.d(LOG_TAG, "onResume");
        super.onResume();
        if (mSession != null) {
            mSession.onResume();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(LOG_TAG, "onDestroy");
        disconnectSession();
        super.onDestroy();
    }

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
            mWebServiceCoordinator = new WebServiceCoordinator();
            mWebServiceCoordinator.connectWebSocket();
        }
        else {
            EasyPermissions.requestPermissions(this, getString(R.string.rationale_video_app), RC_VIDEO_APP_PERM, perms);
        }
    }

    private void initializeSession(String apiKey, String sessionId, String token) {
        this.token = token;
        mSession = new Session.Builder(this, apiKey, sessionId).build();
        mSession.setSessionListener(this);
        mSession.connect(token);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSessionConnectionDataReady(WebServiceCoordinator.SessionConnectionDataReadyEvent event) {
        Log.d(LOG_TAG, "ApiKey: " + event.apiKey + " SessionId: " + event.sessionId + " Token: " + event.token);
        disconnectSession();
        initializeSession(event.apiKey, event.sessionId, event.token);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onWebServiceCoordinatorError(WebServiceCoordinator.WebServiceCoordinatorErrorEvent event) {
        Log.e(LOG_TAG, "WebServiceCoordinatorError: " + event.errorMessage);
        MainActivity.this.runOnUiThread(() ->showFinishingDialog("Error", event.errorMessage));
    }

    @Override
    public void onConnected(Session session) {
        Log.d(LOG_TAG, "onConnected: Connected to session: "+session.getSessionId());

        // initialize Publisher and set this object to listen to Publisher events
        mPublisher = new Publisher.Builder(this).build();
        mPublisher.setPublisherListener(this);

        // set publisher video style to fill view
        mPublisher.getRenderer().setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE,
                BaseVideoRenderer.STYLE_VIDEO_FILL);
        mPublisherViewContainer.addView(mPublisher.getView());

        mSession.publish(mPublisher);
    }

    @Override
    public void onDisconnected(Session session) {
        Log.d(LOG_TAG, "onDisconnected: Disconnected from session: "+session.getSessionId());
    }

    @Override
    public void onStreamReceived(Session session, Stream stream) {
        Log.d(LOG_TAG, "onStreamReceived: New Stream Received "+stream.getStreamId() + " in session: "+session.getSessionId());

        if (mSubscriber == null) {
            mSubscriber = new Subscriber.Builder(this, stream).build();
            mSubscriber.getRenderer().setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL);
            mSession.subscribe(mSubscriber);
            mSubscriberViewContainer.addView(mSubscriber.getView());
        }
    }

    @Override
    public void onStreamDropped(Session session, Stream stream) {
        Log.d(LOG_TAG, "onStreamDropped: Stream Dropped: "+stream.getStreamId() +" in session: "+session.getSessionId());
        if (mSubscriber != null) {
            mSubscriber = null;
            mSubscriberViewContainer.removeAllViews();
        }
        mWebServiceCoordinator.fetchSessionConnectionData(mSession.getSessionId(), token);
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
        if (mSession == null) {
            return;
        }

        if (mSubscriber != null) {
            mSession.unsubscribe(mSubscriber);
            mSubscriberViewContainer.removeAllViews();
            mSubscriber.destroy();
            mSubscriber = null;
        }

        if (mPublisher != null) {
            mSession.unpublish(mPublisher);
            mPublisherViewContainer.removeAllViews();
            mPublisher.destroy();
            mPublisher = null;
        }

        mSession.disconnect();
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
