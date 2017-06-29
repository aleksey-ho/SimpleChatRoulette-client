package com.example.aleks.simplechatroulette;

import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;

public class WebServiceCoordinator {
    private static final String LOG_TAG = WebServiceCoordinator.class.getSimpleName();
    private WebSocketClient mWebSocketClient;

    private static final String CHAT_SERVER_URL = "ws://192.168.1.3:8887";
    private static final String SESSION_INFO_ENDPOINT = CHAT_SERVER_URL + "/session";


    public void connectWebSocket() {
        URI uri;
        try {
            uri = new URI(SESSION_INFO_ENDPOINT);
        } catch (URISyntaxException e) {
            EventBus.getDefault().post(new WebServiceCoordinatorErrorEvent("Session Endpoint is not a valid URL"));
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
                    Log.d(LOG_TAG, "WebServiceCoordinator got session information");
                    EventBus.getDefault().post(new SessionConnectionDataReadyEvent(apiKey, sessionId, token));
                } catch (JSONException e) {
                    EventBus.getDefault().post(new WebServiceCoordinatorErrorEvent("Cannot parse JSONObject from Message"));
                }
            }

            @Override
            public void onClose(int i, String reason, boolean b) {
                Log.d(LOG_TAG, "WebSocket was closed. Reason: " + reason);
                EventBus.getDefault().post(new WebServiceCoordinatorErrorEvent("Cannot connect to server. WebSocket was closed"));
            }

            @Override
            public void onError(Exception e) {
                Log.e(LOG_TAG, "WebSocket threw exception: "  + e.getMessage());
            }
        };
        Log.d(LOG_TAG, "Trying to connect WebSocket");
        mWebSocketClient.connect();
    }

//    public void fetchSessionConnectionData() {
//        if (mWebSocketClient.isOpen()) {
//            mWebSocketClient.send("");
//        }
//        else
//            connectWebSocket();
//    }

    public void fetchSessionConnectionData(String sessionId, String token) {
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
    }

    class SessionConnectionDataReadyEvent {
        public final String apiKey, sessionId, token;
        public SessionConnectionDataReadyEvent(String apiKey, String sessionId, String token) {
            this.apiKey = apiKey;
            this.sessionId = sessionId;
            this.token = token;
        }
    }

    class WebServiceCoordinatorErrorEvent {
        public final String errorMessage;
        public WebServiceCoordinatorErrorEvent(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }

}
