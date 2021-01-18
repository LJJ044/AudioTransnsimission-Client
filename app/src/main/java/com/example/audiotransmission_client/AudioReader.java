package com.example.audiotransmission_client;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

/**
 * 实现录音
 *
 */
public class AudioReader {
    //采用频率
    //44100是目前的标准，但是某些设备仍然支持22050，16000，11025
    //采样频率一般共分为22.05KHz、44.1KHz、48KHz三个等级
    private final static int AUDIO_SAMPLE_RATE = 16000;
    //声道 单声道
    private final static int AUDIO_CHANNEL = AudioFormat.CHANNEL_OUT_MONO;
    //编码
    private final static int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    // 缓冲区字节大小
    public int bufferSizeInBytes = 2048;

    public PLayStatus getStatus() {
        return status;
    }

    private PLayStatus status = PLayStatus.STATUS_AUDIO_NO_READY;
    public static AudioReader audioReader;
    //播放对象
    public AudioTrack audioTrack;

    private AudioReader() {
    }
    public static AudioReader getInstance(){
        if(audioReader == null){
            audioReader = new AudioReader();
        }
        return audioReader;
    }
    public void createDefaultAudio() {
        // 获得缓冲区字节大小
        bufferSizeInBytes = AudioTrack.getMinBufferSize(AUDIO_SAMPLE_RATE,
               AUDIO_CHANNEL, AUDIO_ENCODING);
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSizeInBytes, AudioTrack.MODE_STREAM);
        status = PLayStatus.STATUS_AUDIO_READY;
    }

        public void readAudio(){
        if(status == PLayStatus.STATUS_AUDIO_NO_READY)
            Log.d("AudioReader---","===play error===");
        status = PLayStatus.STATUS_AUDIO_PLAY;
        audioTrack.play();
        }
        public void stopRead(){
        audioTrack.stop();
        status = PLayStatus.STATUS_AUDIO_STOP;
        }
      enum PLayStatus{
        STATUS_AUDIO_READY,
        STATUS_AUDIO_NO_READY,
        STATUS_AUDIO_RESUME,
        STATUS_AUDIO_PLAY,
        STATUS_AUDIO_PAUSE,
        STATUS_AUDIO_STOP
    }

}
