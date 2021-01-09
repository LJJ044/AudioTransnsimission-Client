package com.example.audiotransnsimission_client;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity {
    private WifiAdmin am;
    private String Wifi_Scan_Result = "wifi_result_obtained";
    private WifiManager.LocalOnlyHotspotReservation mReservation;
    String SSID = "OnePlus 7";
    String PASS = "12345678";
    private static final int PORT = 7879;
    private static final int DEVFICE_CONNECTING = 1;
    private static final int DEVFICE_CONNECTED = 2;
    private static final int MSG_RECEIVED = 3;
    private static final int MSG_SENDED = 4;
    private static final int MSG_RECEIVED_ERROR = 5;
    private static final int MSG_SENDED_ERROR = 6;
    private ExecutorService mExecutor;
    private ListenerThread listenerThread;
    private WifiApConnectReceiver wifiApConnectReceiver = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        am = new WifiAdmin(this);
        mExecutor = Executors.newCachedThreadPool();
        //registerReceiver(WifiReceiver,new IntentFilter(Wifi_Scan_Result));
    }

    public void connect_hotspot(View v){
        am.openWifi();
        am.startScan();
        if(!am.isConnected)
        am.addNetwork(am.CreateWifiInfo("OnePlus 7","12345678",3));
    }
    public void create_wifi_hotspot(View v){
        setWifiHotSpotEnabled(true);
    }
    private final BroadcastReceiver WifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
           if(!am.isConnected)
                am.addNetwork(am.CreateWifiInfo("OnePlus 7","12345678",3));
        }
    };
    // wifi热点开关
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
                    reservation.getWifiConfiguration().SSID = SSID;
                    reservation.getWifiConfiguration().preSharedKey = PASS;
                  //  callbak.onConnected("", sid, pwd);
                    Log.d("热点","SSID:"+SSID+" PASS:" +PASS);
                }

                @Override
                public void onStopped() {
                    mReservation = null;
                }

                @Override
                public void onFailed(int reason) {
                    Log.d("热点","wifi ap is failed to open");
                  //  callbak.onConnected("wifi ap is failed to open", null, null);
                }
            }, new Handler());
//            am.mWifiManager.setWifiApConfiguration(config);
//            ConnectivityManager cm = (ConnectivityManager) getApplicationContext().getSystemService(CONNECTIVITY_SERVICE);

        }
        return false;
    }
    private void initConnect(){
        DhcpInfo dhcpInfo = am.mWifiManager.getDhcpInfo();
        String ip = intToIp(dhcpInfo.gateway);
        Toast.makeText(this, "wifi已连接到热点IP: " + ip, Toast.LENGTH_SHORT).show();
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket socket = new Socket(ip, PORT);
                    Log.d("SSID的IP: ", ip);
                    if (socket != null) {
                        ConnectThread connectThread = new ConnectThread(socket, mHandler);
                        mExecutor.execute(connectThread);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
       // listenerThread = new ListenerThread(PORT,mHandler);
        //mExecutor.execute(listenerThread);
    }
    public void onStartAudioSend(View v) throws UnknownHostException {
            initConnect();
    }
    public void SendToServer(View v){
        DhcpInfo dhcpInfo = am.mWifiManager.getDhcpInfo();
        String ip = intToIp(dhcpInfo.gateway);
       // wifiApConnectReceiver = new WifiApConnectReceiver();
       // registerReceiver(wifiApConnectReceiver,new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket socket = new Socket(ip, PORT);
                    Log.d("SSID的IP: ", ip);
                    if (socket != null) {
                        ConnectThread connectThread = new ConnectThread(socket, mHandler);
                        connectThread.setHasPermission(true);
                        connectThread.setMsg("你好，服务端");
                        mExecutor.execute(connectThread);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
    public void SendToClient(View v){
        DhcpInfo dhcpInfo = am.mWifiManager.getDhcpInfo();
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                Socket socket = null;
                socket = listenerThread.getSocket();
                Log.d("SSID的IP: ",intToIp(dhcpInfo.ipAddress));
                if (socket != null) {
                    ConnectThread connectThread = new ConnectThread(socket, mHandler);
                    mExecutor.execute(connectThread);
                }
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
    class ConnectThread implements Runnable{
        private Socket socket;
        private Handler mHandler;
        private InputStream is;
        private OutputStream os;
        private boolean hasPermission;

        public String getMsg() {
            return msg;
        }

        public void setMsg(String msg) {
            this.msg = msg;
        }

        private String msg;
        public ConnectThread(Socket socket, Handler mHandler) {
            this.socket = socket;
            this.mHandler = mHandler;

        }
        @Override
        public void run() {
            if(socket == null)
                return;
            mHandler.sendEmptyMessage(DEVFICE_CONNECTED);
            try{
                os =socket.getOutputStream();
                is = socket.getInputStream();
                if(os != null && hasPermission)
                    sendData(msg);
                if(is != null)
                obtainData();
            }catch (IOException e){
                e.printStackTrace();
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
            this.serverSocket = serverSocket;
            this.mHandlerR = mHandler;
            this.port = Port;
            try {
                serverSocket = new ServerSocket(Port);
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
    public class WifiApConnectReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)){
                    WifiInfo wifiInfo = am.mWifiManager.getConnectionInfo();
                    if(wifiInfo != null) {
                        DhcpInfo dhcpInfo = am.mWifiManager.getDhcpInfo();
                        Toast.makeText(context, "wifi已连接到热点SSID: " + intToIp(dhcpInfo.ipAddress), Toast.LENGTH_SHORT).show();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                Socket socket = null;
                                try {
                                    socket = new Socket(intToIp(dhcpInfo.ipAddress), PORT);
                                    Log.d("IP OF SSID: ",intToIp(dhcpInfo.ipAddress));
                                    if (socket != null) {
                                        ConnectThread connectThread = new ConnectThread(socket, mHandler);
                                        mExecutor.execute(connectThread);
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }).start();

                    }

            }else {
                Toast.makeText(context, "wifi未连接到热点", Toast.LENGTH_SHORT).show();
            }
        }
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
                    Toast.makeText(MainActivity.this, "连接成功", Toast.LENGTH_SHORT).show();
                    break;
                case MSG_SENDED:
                    Toast.makeText(MainActivity.this, "发送数据 :"+(String) msg.obj, Toast.LENGTH_SHORT).show();
                    break;
                case MSG_RECEIVED:
                    Toast.makeText(MainActivity.this, "接收数据 :"+(String) msg.obj, Toast.LENGTH_SHORT).show();
                    break;
                case MSG_SENDED_ERROR:
                    Toast.makeText(MainActivity.this, "发送数据失败", Toast.LENGTH_SHORT).show();
                    break;
                case MSG_RECEIVED_ERROR:
                    Toast.makeText(MainActivity.this, "接收数据失败", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };
    @Override
    protected void onDestroy() {
        super.onDestroy();
//        if(WifiReceiver != null)
//            unregisterReceiver(WifiReceiver);
        if(wifiApConnectReceiver != null)
            unregisterReceiver(wifiApConnectReceiver);

    }


}