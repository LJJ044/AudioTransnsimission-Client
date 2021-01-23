package com.example.audiotransmission_client;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

public class MediaTransReceiver extends BroadcastReceiver {
    private boolean isServiceRunning;
    private Handler mHandler = new Handler();
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        getServiceRunning(context);
                        startService(context);
                        mHandler.postDelayed(this,2000);
                    }

                });

        }
    }
    private void startService(Context context){
        if (!isServiceRunning) {
            Toast.makeText(context, "服务开启中....", Toast.LENGTH_SHORT).show();
            MediaTransService mediaTransService = new MediaTransService();
            mediaTransService.start(context,new Intent(context, MediaTransService.class));
        }
    }
    private boolean getServiceRunning(Context ctx){
        ActivityManager manager = (ActivityManager)ctx.getSystemService(Context.ACTIVITY_SERVICE);

        for(ActivityManager.RunningServiceInfo serviceInfo : manager.getRunningServices(Integer.MAX_VALUE)){
            if(!serviceInfo.service.getClassName().equals(ctx.getPackageName()+".MediaTransService")){
                isServiceRunning = false;
            }else {
                isServiceRunning = true;
                break;
            }
        }
        return isServiceRunning;
    }
}
