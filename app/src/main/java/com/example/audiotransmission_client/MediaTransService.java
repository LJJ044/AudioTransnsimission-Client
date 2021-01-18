package com.example.audiotransmission_client;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

public class MediaTransService extends Service {
    private Notification notification;
    private NotificationManager nm;
    private Context mContext;
    private static final String NITIFICATION_CHANEL_ID = "TransService";
    public static final int NOTIFICATION_ID = 100;
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notification = new Notification.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("StreamTransmitting")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(this.getResources(),R.mipmap.ic_launcher))
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(100,notification);
        Log.i("前台服务","开启中。。。");
        return START_STICKY;
    }
    public void start(Context ctx, Intent intent){
        ctx.startService(intent);
    }
}
