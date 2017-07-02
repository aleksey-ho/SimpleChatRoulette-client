package com.example.aleks.simplechatroulette.services;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.example.aleks.simplechatroulette.BuildConfig;
import com.google.gson.Gson;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;

public class WebCoordinatorService extends Service {
    private static final String LOG_TAG = WebCoordinatorService.class.getSimpleName();

    private WebSocketClient mWebSocketClient;
    private static final String SESSION_INFO_ENDPOINT = BuildConfig.SERVER_URL + "/session";

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public WebCoordinatorService getService() {
            return WebCoordinatorService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent,int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        connect();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void connect() {
        if (mWebSocketClient == null || !mWebSocketClient.isOpen())
            new Thread(() -> connectWebSocket()).start();
    }

    public void connectWebSocket() {
        URI uri;
        try {
            uri = new URI(SESSION_INFO_ENDPOINT);
        } catch (URISyntaxException e) {
            Intent intent = new Intent("WebCoordinatorServiceEvents");
            intent.putExtra("eventType", WebCoordinatorServiceEventType.WebCoordinatorServiceErrorEvent.toString());
            intent.putExtra("message", new Gson().toJson(new WebCoordinatorServiceErrorEvent("Session Endpoint is not a valid URL")));
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            return;
        }

        mWebSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                Log.d(LOG_TAG, "WebSocket was opened");
            }

            @Override
            public void onMessage(String s) {
                JSONObject response;
                try {
                    response = new JSONObject(s);
                    String apiKey = response.getString("apiKey");
                    String sessionId = response.getString("sessionId");
                    String token = response.getString("token");
                    Log.d(LOG_TAG, "WebCoordinatorService got session information");

                    Intent intent = new Intent("WebCoordinatorServiceEvents");
                    intent.putExtra("eventType", WebCoordinatorServiceEventType.SessionConnectionDataReadyEvent.toString());
                    intent.putExtra("message", new Gson().toJson(
                            new SessionConnectionDataReadyEvent(apiKey, sessionId, token)));
                    LocalBroadcastManager.getInstance(WebCoordinatorService.this).sendBroadcast(intent);
                } catch (JSONException e) {
                    Intent intent = new Intent("WebCoordinatorServiceEvents");
                    intent.putExtra("eventType", WebCoordinatorServiceEventType.WebCoordinatorServiceErrorEvent.toString());
                    intent.putExtra("message", new Gson().toJson(
                            new WebCoordinatorServiceErrorEvent("Cannot parse JSONObject from Message")));
                    LocalBroadcastManager.getInstance(WebCoordinatorService.this).sendBroadcast(intent);
                }
            }

            @Override
            public void onClose(int i, String reason, boolean b) {
                Log.d(LOG_TAG, "WebSocket was closed. Reason: " + reason);

                Intent intent = new Intent("WebCoordinatorServiceEvents");
                intent.putExtra("eventType", WebCoordinatorServiceEventType.WebCoordinatorServiceErrorEvent.toString());
                intent.putExtra("message", new Gson().toJson(
                        new WebCoordinatorServiceErrorEvent("Cannot connect to server. WebSocket was closed")));
                LocalBroadcastManager.getInstance(WebCoordinatorService.this).sendBroadcast(intent);
            }

            @Override
            public void onError(Exception e) {
                Log.e(LOG_TAG, "WebSocket threw exception: "  + e.getMessage());
            }
        };
        Log.d(LOG_TAG, "Trying to connect WebSocket");
        mWebSocketClient.connect();
    }

    public void fetchSessionConnectionData(String sessionId, String token) {
        new Thread(() -> {
            if (mWebSocketClient.isOpen()) {
                JSONObject json = new JSONObject();
                try {
                    json.put("sessionId", sessionId);
                    json.put("token", token);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                mWebSocketClient.send(json.toString());
            }
            else
                connectWebSocket();
        }).start();
    }

    public enum WebCoordinatorServiceEventType {
        SessionConnectionDataReadyEvent, WebCoordinatorServiceErrorEvent;
    }

    public static class SessionConnectionDataReadyEvent {
        public final String apiKey, sessionId, token;
        public SessionConnectionDataReadyEvent(String apiKey, String sessionId, String token) {
            this.apiKey = apiKey;
            this.sessionId = sessionId;
            this.token = token;
        }
    }

    public static class WebCoordinatorServiceErrorEvent {
        public final String errorMessage;
        public WebCoordinatorServiceErrorEvent(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }

}
