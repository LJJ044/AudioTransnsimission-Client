package com.example.audiotransmission_client;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.RadioButton;
import android.widget.Toast;

import com.example.audiotransmission_client.R;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class MainActivity extends AppCompatActivity {
    private WifiAdmin am;
    private static final String Wifi_Scan_Result = "wifi_result_obtained";
    private WifiManager.LocalOnlyHotspotReservation mReservation;
    private static final String SSID = "haiyou123";
    private static final String PASS = "12341234";
    private static final int PORT = 7879;
    private ExecutorService mExecutor;
    private boolean isConnected;
    private Socket socket;
    private Handler eHandle;
    private int maxVolume;
    private AudioManager audioManager;
    private RadioButton rb_exit;
    private MediaTransService mService;
    private boolean wifiStateChanged = true;
    private boolean isServiceRunning;
    private Intent mIntent;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        audioManager = (AudioManager) getApplicationContext().getSystemService(AUDIO_SERVICE);
        getServiceRunning();
        if(!isServiceRunning) {
            Log.i("MediaService","未在运行");
            mService = new MediaTransService();
            mIntent = new Intent(this, MediaTransService.class);
            mService.start(this,mIntent);
        }
        rb_exit = (RadioButton) findViewById(R.id.btn_back);
        rb_exit.setButtonDrawable(new StateListDrawable());
        am = new WifiAdmin(this);
        mExecutor = Executors.newCachedThreadPool();

    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private boolean requestPermissions(){
        boolean hasPermission = checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED;
        if(hasPermission) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION
                    , Manifest.permission.RECORD_AUDIO}, 0);
        }else {
            return true;
        }
        return false;
    }
    public void connect_hotspot(View v){
        am.openWifi();
        am.startScan();
        if(!am.isConnected)
        am.addNetwork(am.CreateWifiInfo(SSID,PASS,3));
    }
    public void create_wifi_hotspot(View v){
        setWifiHotSpotEnabled(true);
    }


    /** wifi热点开关
     * @param enabled 是否打开热点
     * @return 热点打开状态
     */
    public boolean setWifiHotSpotEnabled(boolean enabled) {
        //热点的配置类
        WifiConfiguration config = new WifiConfiguration();
        //配置热点的名称(可以在名字后面加点随机数什么的)
        config.SSID = SSID;
        //配置热点的密码
        config.preSharedKey = PASS;
        config.hiddenSSID = true;
        config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        //config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        //通过反射调用设置热点
        if(Build.VERSION.SDK_INT < 26) {
            if (am.mWifiManager.isWifiEnabled()) { // disable WiFi in any case
                //wifi和热点不能同时打开，所以打开热点的时候需要关闭wifi
                am.mWifiManager.setWifiEnabled(false);
            }
            try {
                Method method = am.mWifiManager.getClass().getMethod(
                        "setWifiApEnabled", WifiConfiguration.class, Boolean.TYPE);
                //返回热点打开状态
                return (Boolean) method.invoke(am.mWifiManager, config, enabled);
            } catch (Exception e) {
                return false;
            }
        }else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION
                },0);
                return false;
            }
            am.mWifiManager.startLocalOnlyHotspot(new WifiManager.LocalOnlyHotspotCallback() {
                @Override
                public void onStarted(WifiManager.LocalOnlyHotspotReservation reservation) {
                    mReservation = reservation;
                    //String SSID = reservation.getWifiConfiguration().SSID = "mi6";
                    //String PASS = reservation.getWifiConfiguration().preSharedKey = "66666666";
                    Log.d("热点","SSID:"+reservation.getWifiConfiguration().SSID+" PASS:" +reservation.getWifiConfiguration().preSharedKey);
                }

                @Override
                public void onStopped() {
                    mReservation = null;
                }

                @Override
                public void onFailed(int reason) {
                    Log.d("热点","wifi ap is failed to open");
                }
            }, new Handler());

        }
        return false;
    }

    public void cancel_output_pad(View view){
                audioManager.abandonAudioFocus(acl);

    }
    private AudioManager.OnAudioFocusChangeListener acl = focusChange -> {

    };
    public void onStartAudioReceive(View v) {
        DhcpInfo dhcpInfo = am.mWifiManager.getDhcpInfo();
        String ip = intToIp(dhcpInfo.gateway);
            mExecutor.execute(() -> {
                try {
                    socket = new Socket(ip, PORT);
                    ConnectThread connectThread = new ConnectThread(socket, mService.mHandler);
                    mExecutor.execute(connectThread);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

    }
    public void SendToServer(View v){
        DhcpInfo dhcpInfo = am.mWifiManager.getDhcpInfo();
        String ip = intToIp(dhcpInfo.gateway);
        Toast.makeText(this, "客户端的网关："+ip, Toast.LENGTH_SHORT).show();
        mExecutor.execute(() -> {
            Log.d("SSID的IP: ", ip);
                if (socket == null) {
                    try {
                        socket = new Socket(ip,PORT);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                    ConnectThread connectThread = new ConnectThread(socket, mService.mHandler);
                    connectThread.setHasPermission(true);
                    connectThread.setMsg("你好，服务端");
                    mExecutor.execute(connectThread);


        });
    }
    public void SendToClient(View v){
        DhcpInfo dhcpInfo = am.mWifiManager.getDhcpInfo();
        String ip = intToIp(dhcpInfo.gateway);
        Toast.makeText(this, "客户端的网关："+ip, Toast.LENGTH_SHORT).show();
        mExecutor.execute(() -> {
            Socket socket = null;
            try {
                socket = new Socket(ip,PORT);
                ConnectThread connectThread = new ConnectThread(socket, mService.mHandler);
                mExecutor.execute(connectThread);
            } catch (IOException e) {
                e.printStackTrace();
            }

        });
    }


    /**
     * 获取连接到热点上的手机ip
     *
     * @return
     */
    private ArrayList<String> getConnectedIP() {
        ArrayList<String> connectedIP = new ArrayList<String>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(
                    "/proc/net/arp"));
            String line;
            while ((line = br.readLine()) != null) {
                String[] splitted = line.split(" +");
                if (splitted != null && splitted.length >= 4) {
                    String ip = splitted[0];
                    connectedIP.add(ip);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return connectedIP;
    }

    public void exit(View view){
       /* Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        startActivity(intent);*/
        //android.os.Process.killProcess(android.os.Process.myPid());
        finish();

    }
    private String intToIp(int paramInt)
    {
        return (paramInt & 0xFF) + "." + (0xFF & paramInt >> 8) + "." + (0xFF & paramInt >> 16) + "."
                + (0xFF & paramInt >> 24);
    }
    private boolean shiftToPlay(){
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
    @Override
    protected void onDestroy() {
        AlarmManager alarmManager = (AlarmManager) getApplicationContext().getSystemService(ALARM_SERVICE);
        PendingIntent intent = PendingIntent.getService(this,1,new Intent(this,MediaTransService.class),PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.set(AlarmManager.RTC_WAKEUP,System.currentTimeMillis() + 1000,intent);
        super.onDestroy();
        if(mService != null)
            mService.stopSelf();
    }

}