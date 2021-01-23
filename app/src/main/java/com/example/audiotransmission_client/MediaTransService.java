package com.example.audiotransmission_client;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.IOException;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.example.audiotransmission_client.AudioReader.audioReader;

public class MediaTransService extends Service {
    private static final String Wifi_Scan_Result = "wifi_result_obtained";
    private static final String SSID = "haiyou123";
    private static final String PASS = "12341234";
    private static final int DEVICE_DISCONNETED = 1;
    private static final int DEVICE_CONNECTED = 2;
    private static final int MSG_RECEIVED = 3;
    private static final int MSG_SENDED = 4;
    private static final int MSG_RECEIVED_ERROR = 5;
    private static final int MSG_SENDED_ERROR = 6;
    public ExecutorService mExecutor;
    private boolean wifiStateChanged = true;
    private static final int PORT = 7879;
    private WifiAdmin am;
    private boolean isConnected;
    private int maxVolume;
    private Handler eHandle;
    public Socket socket;
    private AudioManager audioManager;
    private Notification notification;
    private boolean isServiceRunning;
    private NotificationManager nm;
    private Context mContext;
    private ConnectThread connectThread;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        am = new WifiAdmin(this);
        Notification.Builder nb = new Notification.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("StreamTransmitting")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(this.getResources(),R.mipmap.ic_launcher));
        if(Build.VERSION.SDK_INT >= 26) {
            nm = (NotificationManager) getApplicationContext().getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel("aaa", getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(channel);
            nb.setChannelId("aaa");
        }
        notification = nb.build();
        mExecutor = Executors.newCachedThreadPool();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(100,notification);
        Log.i("前台服务","开启中。。。");
        IntentFilter intentFilter =new IntentFilter();
        intentFilter.addAction(Wifi_Scan_Result);
        intentFilter.addAction("wifi_state_change");
        intentFilter.addAction("android.media.VOLUME_CHANGED_ACTION");
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        registerReceiver(commonReceiver,intentFilter);
        return START_STICKY;
    }
    public void start(Context ctx, Intent intent){
        ctx.startService(intent);
        mContext = ctx;
    }

    public void initConnect(){
        if(socket != null) {
            try {
                if(audioReader != null){
                    audioReader.stopRead();
                }
                socket.close(); //wifi断开重连前要先关掉socket
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mExecutor.execute(() -> {
            Looper.prepare();
            eHandle = new Handler();
            eHandle.post(execution);
            Looper.loop();
        });
    }

    //客户端每秒检查是否与服务端建立连接
    private Runnable execution = new Runnable() {
        @Override
        public void run() {
            mHandler.post(() -> {
                mExecutor.execute(() -> {
                    DhcpInfo dhcpInfo = am.mWifiManager.getDhcpInfo();
                    String ip = intToIp(dhcpInfo.gateway);
                    try {
                        socket = new Socket(ip, PORT);
                        connectThread = new ConnectThread(socket, mHandler);
                        connectThread.setPlayFocusAcquire(shiftToPlay());
                        mExecutor.execute(connectThread);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });


            });
            eHandle.postDelayed(this,1000);
        }
    };
    //请求音频焦点
    public boolean shiftToPlay(){
        audioManager = (AudioManager)this.getSystemService(AUDIO_SERVICE);
        if(audioManager.isMusicActive()){
            int result = audioManager.requestAudioFocus(acl,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            if(result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.d("音频焦点", "requestAudioFocus successfully.");
                return true;
            }
            else {
                Log.d("音频焦点", "requestAudioFocus failed.");
                return false;
            }
        }
        return true;

    }
    private AudioManager.OnAudioFocusChangeListener acl = focusChange -> {

    };
    private String intToIp(int paramInt)
    {
        return (paramInt & 0xFF) + "." + (0xFF & paramInt >> 8) + "." + (0xFF & paramInt >> 16) + "."
                + (0xFF & paramInt >> 24);
    }
    private final BroadcastReceiver commonReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case Wifi_Scan_Result:
                    am.addNetwork(am.CreateWifiInfo(SSID, PASS, 3));
                    Log.i("wifi扫描到结果","aaa");

                            if (wifiStateChanged) {
                                initConnect();
                                wifiStateChanged = false;
                            }

                    break;
                case "android.media.VOLUME_CHANGED_ACTION":
                    if (audioManager == null)
                        audioManager = (AudioManager) getApplicationContext().getSystemService(AUDIO_SERVICE);
                    int streamVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    Log.i("设备最大音量", maxVolume + ", " + "设备当前音量:" + streamVolume);
                    break;

                case WifiManager.WIFI_STATE_CHANGED_ACTION:
                    Log.i("Wifi状态", "change");
                    wifiStateChanged = true;
                    am.openWifi();
                    am.startScan();

                    break;

            }
        }
    };
    private boolean getServiceRunning(){
        ActivityManager manager = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);

        for(ActivityManager.RunningServiceInfo serviceInfo : manager.getRunningServices(Integer.MAX_VALUE)){
            if(!serviceInfo.service.getClassName().equals(getPackageName()+".MediaTransService")){
                isServiceRunning = false;
            }else {
                isServiceRunning = true;
                break;
            }
        }
        return isServiceRunning;
    }
    public Handler mHandler = new Handler(){
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case DEVICE_CONNECTED:
                    if(eHandle != null)
                    eHandle.removeCallbacks(execution); //客户端与服务端建立连接，停止每秒的检查
                    DhcpInfo dhcpInfo = am.mWifiManager.getDhcpInfo();
                    String ip = intToIp(dhcpInfo.gateway);
                    Toast.makeText(getApplicationContext(), "wifi已连接到热点IP: " + ip, Toast.LENGTH_SHORT).show();
                    Toast.makeText(getApplicationContext(), "连接成功", Toast.LENGTH_SHORT).show();

                    break;
                case MSG_SENDED:
                    // Toast.makeText(MediaTransService.this, "发送数据 :"+(String) msg.obj, Toast.LENGTH_SHORT).show();
                    break;
                case MSG_RECEIVED:
                    String content = (String) msg.obj;
                    if(content.contains(".")) {                 //接收到音量值（字符串）
                        float valuePercent = Float.parseFloat(content);
                        int value = (int) (valuePercent * maxVolume);
                        if (value <= maxVolume) {
                            for (int i = 0; i < value; i++) {
                                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                            }
                        }
                    }else if(content.equals("0")){                //0切换音源为平板
                        connectThread.setPlayFocusAcquire(shiftToPlay());
                    }else {                                      //1切换音源为车机
                        audioManager.abandonAudioFocus(acl);
                        connectThread.setPlayFocusAcquire(false);
                    }
                    //audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,value ,AudioManager.FLAG_SHOW_UI);
                    //Toast.makeText(MediaTransService.this, "接收数据 :"+(String) msg.obj, Toast.LENGTH_SHORT).show();
                    break;
                case MSG_SENDED_ERROR:
                    //Toast.makeText(MediaTransService.this, "发送数据失败", Toast.LENGTH_SHORT).show();
                    break;
                case MSG_RECEIVED_ERROR:
                    //服务端断开重连
                    initConnect();
                    //Toast.makeText(MediaTransService.this, "接收数据失败", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    @Override
    public boolean stopService(Intent name) {
        Log.i("前台服务","停止中。。。");
        stopForeground(true);
        return super.stopService(name);
    }

    @Override
    public void onDestroy() {
        Log.i("前台服务","销毁中。。。");
        super.onDestroy();
        if(commonReceiver != null)
            unregisterReceiver(commonReceiver);
    }
}
