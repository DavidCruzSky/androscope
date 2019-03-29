package nl.ngti.androscope;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import fi.iki.elonen.AndroscopeHttpServer;
import fi.iki.elonen.IoServerRunner;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHttpListener;

public class AndroscopeService extends Service {

    private static final String TAG = AndroscopeService.class.getSimpleName();

    private static final String ACTION_START_WEB_SERVER = "nl.ngti.androscope.action.START_WEB_SERVER";
    private static final String ACTION_STOP_WEB_SERVER = "nl.ngti.androscope.action.STOP_WEB_SERVER";

    private static final String KEY_FORCE = "nl.ngti.androscope.key.FORCE";
    private static final String KEY_CALLBACK = "nl.ngti.androscope.key.CALLBACK";

    private static final String RESULT_MESSAGE = "nl.ngti.androscope.result.MESSAGE";

    private NanoHTTPD mServer;

    public static void startServer(Context context, boolean force, @Nullable ResultReceiver callback) {
        Intent intent = new Intent(context, AndroscopeService.class);
        intent.setAction(ACTION_START_WEB_SERVER);
        intent.putExtra(KEY_FORCE, force);
        intent.putExtra(KEY_CALLBACK, callback);
        ContextCompat.startForegroundService(context, intent);
    }

    public static String getResultMessage(Bundle bundle) {
        return bundle != null ? bundle.getString(RESULT_MESSAGE) : null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            final String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case ACTION_START_WEB_SERVER:
                        final boolean force = intent.getBooleanExtra(KEY_FORCE, false);
                        final ResultReceiver callback = intent.getParcelableExtra(KEY_CALLBACK);
                        handleServerStart(force, callback);
                        break;
                    case ACTION_STOP_WEB_SERVER:
                        handleServerStop();
                        break;
                }
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopServer();
    }

    private boolean stopServer() {
        if (mServer != null) {
            mServer.stop();
            mServer = null;
            return true;
        }
        return false;
    }

    private void handleServerStart(boolean force, @Nullable ResultReceiver callback) {
        if (mServer != null && mServer.isAlive()) {
            showToast("Androscope is already running");
            IoServerRunner.notifyServerStarted(this, mServer, new NanoListener(callback));
            return;
        }

        mServer = AndroscopeHttpServer.newInstance(this, force);
        if (mServer != null) {
            showNotification();

            if (IoServerRunner.executeInstance(mServer)) {
                showToast("Androscope was started");
                IoServerRunner.notifyServerStarted(this, mServer, new NanoListener(callback));
            } else {
                showToast("Error starting Androscope");
            }
        }
    }

    private void handleServerStop() {
        if (stopServer()) {
            stopForeground(true);
        }
    }

    private void showNotification() {
        final Intent intent = new Intent(this, AndroscopeService.class);
        intent.setAction(ACTION_STOP_WEB_SERVER);

        final PendingIntent pendingIntent = PendingIntent.getService(this,
                R.id.androscope_notification_request_code_stop_server, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        final NotificationCompat.Action action = new NotificationCompat.Action.Builder(0, getString(R.string.androscope_stop_server), pendingIntent)
                .build();

        final Notification notification = getNotificationBuilder()
                .setSmallIcon(R.drawable.androscope_notification_icon)
                .setContentText("Androscope is running")
                .setContentIntent(PendingIntent.getActivity(this,
                        R.id.androscope_notification_request_code_open_androscope_activity,
                        new Intent(this, AndroscopeActivity.class), PendingIntent.FLAG_UPDATE_CURRENT))
                .addAction(action)
                .build();

        startForeground(R.id.androscope_notification_id, notification);
    }

    @NonNull
    private NotificationCompat.Builder getNotificationBuilder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final String channelId = getString(R.string.androscope_channel_id);
            final String channelName = getString(R.string.androscope_channel_name);
            final NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_MIN);
            getNotificationManager().createNotificationChannel(channel);

            return new NotificationCompat.Builder(this, channelId);
        } else {
            //noinspection deprecation
            return new NotificationCompat.Builder(this);
        }
    }

    @NonNull
    private NotificationManager getNotificationManager() {
        //noinspection ConstantConditions
        return (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private void showToast(String message) {
        new Handler(Looper.getMainLooper()).post(new ShowMessageRunnable(this, message));
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static final class NanoListener implements NanoHttpListener {

        @Nullable
        private final ResultReceiver mCallback;

        NanoListener(@Nullable ResultReceiver callback) {
            mCallback = callback;
        }

        @Override
        public void serverReady(final String ip, final String port) {
            final String message =
                    "Androscope is running!\n" +
                            "Address: [ http://" + ip + ":" + port + " ]\n\n" +
                            "Local server at [ http://127.0.0.1:" + port + " ]\n\n" +
                            "For GENYMOTION this ip doesn't work, use the ip of the emulator returned by 'adb devices'";
            // Logs on some devices are polluted when activity is started, here we add some delay,
            // so the IP address will be logged in the end and user will not need to scroll up.
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, message);
                }
            }, 200);

            if (mCallback != null) {
                final Bundle result = new Bundle(1);
                result.putString(RESULT_MESSAGE, message);
                mCallback.send(0, result);
            }
        }
    }

    private static final class ShowMessageRunnable implements Runnable {

        private final Context mContext;
        private final String mMessage;

        private ShowMessageRunnable(Context context, String message) {
            mContext = context;
            mMessage = message;
        }

        @Override
        public void run() {
            Toast.makeText(mContext, mMessage, Toast.LENGTH_LONG).show();
        }
    }

}
