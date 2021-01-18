package com.example.audiotransmission_client;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
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
    private static final String SSID = "Device";
    private static final String PASS = "12345678";
    private static final int PORT = 7879;
    private static final int DEVFICE_CONNECTING = 1;
    private static final int DEVFICE_CONNECTED = 2;
    private static final int MSG_RECEIVED = 3;
    private static final int MSG_SENDED = 4;
    private static final int MSG_RECEIVED_ERROR = 5;
    private static final int MSG_SENDED_ERROR = 6;
    private ExecutorService mExecutor;
    private ListenerThread listenerThread;
    private AudioReader audioReader;
    private boolean isConnected;
    private Handler eHandle;
    private Socket socket;
    private int maxVolume;
    private AudioManager audioManager;
    private RadioButton rb_exit;
    private boolean isPlayFocusAcquire;
    private MediaTransService mService;
    private boolean wifiStateChanged = true;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mService = new MediaTransService();
        mService.start(this,new Intent(this,MediaTransService.class));
        rb_exit = (RadioButton) findViewById(R.id.btn_back);
        rb_exit.setButtonDrawable(new StateListDrawable());
        am = new WifiAdmin(this);
        mExecutor = Executors.newCachedThreadPool();
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        requestPermissions();
        IntentFilter intentFilter =new IntentFilter();
        intentFilter.addAction(Wifi_Scan_Result);
        intentFilter.addAction("wifi_state_change");
        intentFilter.addAction("android.media.VOLUME_CHANGED_ACTION");
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        //intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(commonReceiver,intentFilter);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private boolean requestPermissions(){
        boolean hasPermission = checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED||
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
    private final BroadcastReceiver commonReceiver = new BroadcastReceiver() {
        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case Wifi_Scan_Result:
                    am.addNetwork(am.CreateWifiInfo(SSID, PASS, 3));
                    Log.i("wifi扫描到结果","aaa");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (requestPermissions()) {
                            if (wifiStateChanged) {
                                initConnect();
                                wifiStateChanged = false;
                            }
                        }
                    } else {
                        if(wifiStateChanged) {
                            initConnect();
                            wifiStateChanged = false;
                        }
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
                case ConnectivityManager.CONNECTIVITY_ACTION:
                    Log.i("网络连接状态", "change");
                    ConnectivityManager cm = (ConnectivityManager) getApplicationContext().getSystemService(CONNECTIVITY_SERVICE);
                    NetworkInfo networkInfo = cm.getActiveNetworkInfo();
                    if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI && networkInfo.getState() == NetworkInfo.State.CONNECTED) {
                        WifiInfo wifiInfo = am.mWifiManager.getConnectionInfo();
                        if (wifiInfo != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                if (requestPermissions())
                                    initConnect();
                            } else {
                                initConnect();
                            }

                        } else {
                            Toast.makeText(context, "wifi未连接到热点", Toast.LENGTH_SHORT).show();
                        }
                        break;
                    }
            }
        }
    };

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
    private void initConnect(){
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
        DhcpInfo dhcpInfo = am.mWifiManager.getDhcpInfo();
        String ip = intToIp(dhcpInfo.gateway);
        Toast.makeText(this, "wifi已连接到热点IP: " + ip, Toast.LENGTH_SHORT).show();
            mExecutor.execute(() -> {
                Looper.prepare();
                eHandle = new Handler();
                eHandle.post(execution);
                Looper.loop();

            });

               /* mExecutor.execute(() -> {
                    try {
                        socket = new Socket(ip, PORT);
                        ConnectThread connectThread = new ConnectThread(socket, mHandler);
                        mExecutor.execute(connectThread);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });*/

           // listenerThread = new ListenerThread(PORT,mHandler);
           // mExecutor.execute(listenerThread);
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
                        ConnectThread connectThread = new ConnectThread(socket, mHandler);
                        mExecutor.execute(connectThread);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });


            });
          eHandle.postDelayed(this,1000);
        }
    };
    public void output_pad(View view){
        DhcpInfo dhcpInfo = am.mWifiManager.getDhcpInfo();
        String ip = intToIp(dhcpInfo.gateway);
        isPlayFocusAcquire = shiftToPlay();
        mExecutor.execute(() -> {
            try {
                socket = new Socket(ip, PORT);
                ConnectThread connectThread = new ConnectThread(socket, mHandler);
                mExecutor.execute(connectThread);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

    }
    public void cancel_output_pad(View view){
                audioManager.abandonAudioFocus(acl);
                isPlayFocusAcquire = false;
    }
    public void onStartAudioReceive(View v) {
        DhcpInfo dhcpInfo = am.mWifiManager.getDhcpInfo();
        String ip = intToIp(dhcpInfo.gateway);
            mExecutor.execute(() -> {
                try {
                    socket = new Socket(ip, PORT);
                    ConnectThread connectThread = new ConnectThread(socket, mHandler);
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
                    ConnectThread connectThread = new ConnectThread(socket, mHandler);
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
                ConnectThread connectThread = new ConnectThread(socket, mHandler);
                mExecutor.execute(connectThread);
            } catch (IOException e) {
                e.printStackTrace();
            }

        });
    }
   private boolean shiftToPlay(){
        audioManager = (AudioManager) getApplicationContext().getSystemService(AUDIO_SERVICE);
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
    class ConnectThread implements Runnable{
        private Socket socket;
        private Handler mHandler;
        private InputStream is;
        private OutputStream os;
        private Gson gson;
        private com.google.gson.stream.JsonReader jr;
        private boolean hasPermission;
        private String msg;
        private byte[] stream;

        public byte[] getStream() {
            return stream;
        }

        public void setStream(byte[] stream) {
            this.stream = stream;
        }

        public String getMsg() {
            return msg;
        }

        public void setMsg(String msg) {
            this.msg = msg;
        }


        public ConnectThread(Socket socket, Handler mHandler) {
            this.socket = socket;
            this.mHandler = mHandler;
            gson = new Gson();
        }
        @Override
        public void run() {
            if(socket == null) {
                Log.i("客户端socket","为空");
                return;
            }
            mHandler.sendEmptyMessage(DEVFICE_CONNECTED);
            if(audioReader == null) {
                audioReader = AudioReader.getInstance();
                audioReader.createDefaultAudio();
            }
            try{
                //os =socket.getOutputStream();
                is = socket.getInputStream();
                jr = new JsonReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                jr.setLenient(true);
                isPlayFocusAcquire = shiftToPlay();
                if(isPlayFocusAcquire) {
                    if (audioReader.getStatus() != AudioReader.PLayStatus.STATUS_AUDIO_PLAY)
                        audioReader.readAudio();
                }else {
                        audioReader.stopRead();
                }
                    while (jr.hasNext()) {
                        if (isPlayFocusAcquire) {
                            TransmissionBean transmissionBean = gson.fromJson(jr, TransmissionBean.class);
                            if (transmissionBean.contentType == 0) {    //读取音频流
                                byte[] data = Base64.decode(transmissionBean.content.getBytes(), Base64.DEFAULT);
                                audioReader.audioTrack.write(data, 0, data.length);
                                // Log.i("读取音频长度",data.length+"");
                            } else {                                     //读取字符串
                                obtainString(transmissionBean.content);
                            }
                        }else {
                            audioReader.stopRead();
                            socket.close();
                            break;
                        }

                    }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        private void obtainAudio(){
            audioReader.readAudio();
            while (true){
                try {
                    byte[] buffer = new byte[audioReader.bufferSizeInBytes];
                    int length;
                    length = is.read(buffer);
                    if(length <0)
                        break;
                    audioReader.audioTrack.write(buffer,0,length);
                   // Log.i("读取音频长度",buffer.length+"");
                } catch (IOException e) {
                    e.printStackTrace();
                    mHandler.sendEmptyMessage(MSG_RECEIVED_ERROR);
                }

            }
        }
        private void sendData(String str){
            try {
                os.write(str.getBytes());
                Message msg = Message.obtain();
                msg.what = MSG_SENDED;
                msg.obj = str;
                mHandler.sendMessage(msg);
            } catch (IOException e) {
                e.printStackTrace();
                mHandler.sendEmptyMessage(MSG_SENDED_ERROR);
            }
        }
        private void sendAudioBytes(byte[] audioData){
            try {
                os.write(audioData);
            } catch (IOException e) {
                e.printStackTrace();
                mHandler.sendEmptyMessage(MSG_SENDED_ERROR);
            }
        }
        private void obtainString(String str){
            Message msg = Message.obtain();
            msg.what = MSG_RECEIVED;
            msg.obj = str;
            mHandler.sendMessage(msg);
        }
        private void obtainData(){
                byte[] buffer = new byte[1024];
                int length;
                while (true){
                    try {
                        if((length = is.read(buffer)) <0)
                            break;
                        byte[] data= new byte[length];
                        System.arraycopy(buffer, 0, data, 0, length);
                        Message msg = Message.obtain();
                        msg.what = MSG_RECEIVED;
                        msg.obj = new String(data);
                        mHandler.sendMessage(msg);
                    } catch (IOException e) {
                        e.printStackTrace();
                        mHandler.sendEmptyMessage(MSG_RECEIVED_ERROR);
                    }

                }

        }
        public boolean isHasPermission() {
            return hasPermission;
        }

        public void setHasPermission(boolean hasPermission) {
            this.hasPermission = hasPermission;
        }
    }
    class ListenerThread implements Runnable{
        private Handler mHandlerR;
        private ServerSocket serverSocket;
        private Socket socket;
        private int port;
        public ListenerThread(int Port,Handler mHandler) {
            this.mHandlerR = mHandler;
            this.port = Port;
            try {
                serverSocket = new ServerSocket(port);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            while (true) {
                try {
                    if(serverSocket != null)
                        socket = serverSocket.accept();
                        Message msg = Message.obtain();
                        msg.what = DEVFICE_CONNECTING;
                        msg.obj = "我来咯";
                        mHandlerR.sendMessage(msg);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        public Socket getSocket() {
            return socket;
        }

    }
    public void exit(View view){
        finish();
        android.os.Process.killProcess(android.os.Process.myPid());
    }
    private String intToIp(int paramInt)
    {
        return (paramInt & 0xFF) + "." + (0xFF & paramInt >> 8) + "." + (0xFF & paramInt >> 16) + "."
                + (0xFF & paramInt >> 24);
    }
    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case DEVFICE_CONNECTING:
                     Toast.makeText(MainActivity.this, "接收到客户端的连接 ："+(String) msg.obj, Toast.LENGTH_SHORT).show();
                     mExecutor.execute(new ConnectThread(listenerThread.getSocket(),mHandler));
                    break;
                case DEVFICE_CONNECTED:
                    eHandle.removeCallbacks(execution); //客户端与服务端建立连接，停止每秒的检查
                    if(!isConnected) {
                        Toast.makeText(MainActivity.this, "连接成功", Toast.LENGTH_SHORT).show();
                        isConnected = true;
                    }
                    break;
                case MSG_SENDED:
                   // Toast.makeText(MainActivity.this, "发送数据 :"+(String) msg.obj, Toast.LENGTH_SHORT).show();
                    break;
                case MSG_RECEIVED://接收到音量（字符串）
                    float valuePercent = Float.parseFloat((String) msg.obj);
                    int value = (int) (valuePercent * maxVolume);
                    if(value <= maxVolume) {
                        for (int i = 0; i < value; i++) {
                            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0);
                        }
                    }
                    //audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,value ,AudioManager.FLAG_SHOW_UI);
                    //Toast.makeText(MainActivity.this, "接收数据 :"+(String) msg.obj, Toast.LENGTH_SHORT).show();
                    break;
                case MSG_SENDED_ERROR:
                    //Toast.makeText(MainActivity.this, "发送数据失败", Toast.LENGTH_SHORT).show();
                    break;
                case MSG_RECEIVED_ERROR:
                    //Toast.makeText(MainActivity.this, "接收数据失败", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mService != null) {
            Log.i("前台服务","已销毁");
            mService.stopForeground(true);
        }
       if(commonReceiver != null)
            unregisterReceiver(commonReceiver);
    }

}