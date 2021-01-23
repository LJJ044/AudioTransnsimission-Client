package com.example.audiotransmission_client;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ConnectThread implements Runnable{
    private static final int DEVICE_DISCONNECTED = 1;
    private static final int DEVICE_CONNECTED = 2;
    private static final int MSG_RECEIVED = 3;
    private static final int MSG_SENDED = 4;
    private static final int MSG_RECEIVED_ERROR = 5;
    private static final int MSG_SENDED_ERROR = 6;
    private AudioReader audioReader;

    public void setPlayFocusAcquire(boolean playFocusAcquire) {
        isPlayFocusAcquire = playFocusAcquire;
    }

    private boolean isPlayFocusAcquire;
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
        mHandler.sendEmptyMessage(DEVICE_CONNECTED);
        if(audioReader == null) {
            audioReader = AudioReader.getInstance();
            audioReader.createDefaultAudio();
        }
        try{
            //os =socket.getOutputStream();
            is = socket.getInputStream();
            jr = new JsonReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            jr.setLenient(true);
            if (isPlayFocusAcquire) {
                if (audioReader.getStatus() != AudioReader.PLayStatus.STATUS_AUDIO_PLAY)
                    audioReader.readAudio();
            } else {
                audioReader.stopRead();
            }

                while (jr.hasNext()) {
                    if (isPlayFocusAcquire) {
                            try {
                                if (audioReader.getStatus() != AudioReader.PLayStatus.STATUS_AUDIO_PLAY)
                                    audioReader.readAudio();
                                TransmissionBean transmissionBean = gson.fromJson(jr, TransmissionBean.class);
                                if (transmissionBean.contentType == 0) {    //读取音频流
                                    byte[] data = Base64.decode(transmissionBean.content.getBytes(), Base64.DEFAULT);
                                    audioReader.audioTrack.write(data, 0, data.length);
                                    Log.i("读取音频长度", data.length + "");
                                } else {                                     //读取字符串
                                    obtainString(transmissionBean.content);
                                }
                            }catch(Exception e){
                                e.printStackTrace();
                                mHandler.sendEmptyMessage(MSG_RECEIVED_ERROR);
                                break;
                            }
                        } else{
                            audioReader.stopRead();
                            TransmissionBean transmissionBean = gson.fromJson(jr, TransmissionBean.class);
                            if(transmissionBean.contentType == 1)
                            obtainString(transmissionBean.content);
                        }

                }


        }catch (Exception e){
            e.printStackTrace();
            mHandler.sendEmptyMessage(MSG_RECEIVED_ERROR);
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
