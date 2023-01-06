package com.HunterWhyte.jumaudio;

import java.io.FileNotFoundException;
import java.security.InvalidParameterException;
import java.util.ArrayList;

import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.SharedLibraryLoader;

public class JumAudio implements Disposable{
    static { // static initialization block
        new SharedLibraryLoader().load("gdx-jumaudio");
    }

    public static class AudioDevice{
        public String name;
        protected int index;
        public AudioDevice(String name, int index){
            this.name = name;
            this.index = index;
        }
    }
    public static class CaptureDevice extends AudioDevice{
        public CaptureDevice(String name, int index) {
            super(name, index);
        }
    }
    public static class PlaybackDevice extends AudioDevice{
        public PlaybackDevice(String name, int index) {
            super(name, index);
        }
    }

    // @off
    /*JNI
    #define JUMAUDIO_IMPLEMENTATION
    #include "jumaudio.h"

    jum_AudioSetup* audio;
    jum_FFTSetup* fft;
    */
    
    // fft + filtering parameters with some default values.
    private int fft_buf_size = 4096;
    // number of callback buffers to predecode ahead of writing to stream
    private int num_bins = 1024;
    // weighting applied to frequency bins, [0] = freq, [1] = weight in db
    private float[][] weights = {{63, -5},    {200, -5},   {250, -5},   {315, -5},
                            {400, -4.8f}, {500, -3.2f}, {630, -1.9f}, {800, -0.8f},
                            {1000, 0.0f}, {1250, 0.6f}, {1600, 1.0f}, {2000, 1.2f},
                            {2500, 3.3f}, {3150, 4.2f}, {4000, 5.0f}};
    // frequency bin distribution
    private float[][] freqs = {{0, 35}, {0.2f, 450},  {0.3f, 700},  {0.4f, 1200},
                            {0.5f, 1700},  {0.6f, 2600}, {0.7f, 4100}, {0.8f, 6500},
                            {0.9f, 10000}, {1.0f, 20000}};

    private int predecode_bufs = 5;
    private int audio_buf_size = fft_buf_size*(predecode_bufs+5);
    private int period_in_frames = fft_buf_size;
    private boolean audio_initialized = false;
    private boolean fft_initialized = false;

    public float[] fft_raw;
    public float[] fft_result;

    public ArrayList<CaptureDevice> capture_devices;
    private int num_capture_devices;
    public ArrayList<PlaybackDevice> playback_devices;
    private int num_playback_devices;

    private native int jniInitAudio(int buffer_size, int predecode_bufs, int period);/*
        audio = jum_initAudio(buffer_size, predecode_bufs, period);
        return (audio == NULL);
    */
    public JumAudio(int audio_buf_size, int predecode_bufs, int period_in_frames){
        this.audio_buf_size = audio_buf_size;
        this.predecode_bufs = predecode_bufs;
        this.period_in_frames = period_in_frames;
        jniInitAudio(audio_buf_size, predecode_bufs, period_in_frames);
        getDevices();    
        audio_initialized = true;
    }
    public JumAudio(){
        jniInitAudio(audio_buf_size, predecode_bufs, period_in_frames);
        getDevices();    
        audio_initialized = true;
    }

    private native int jniGetNumCaptureDevices();/*
        if(audio == NULL)
            return 0;
        else
            return audio->capture_device_count;
    */
    private native int jniGetNumPlaybackDevices();/*
        if(audio == NULL)
            return 0;
        else
            return audio->playback_device_count;
    */
    private native void jniGetAudioDevices(String[] capture_devices, String[] playback_devices);/*
        for (ma_uint32 i = 0; i < audio->capture_device_count; ++i) {
            env->SetObjectArrayElement(capture_devices, i, env->NewStringUTF(audio->capture_device_info[i].name));
        }
        for (ma_uint32 i = 0; i < audio->playback_device_count; ++i) {
            env->SetObjectArrayElement(playback_devices, i, env->NewStringUTF(audio->playback_device_info[i].name));
        }
    */
    private void getDevices(){
        // get enumerated capture and playback devices
        num_capture_devices = jniGetNumCaptureDevices();
        num_playback_devices = jniGetNumPlaybackDevices();
        String[] capture_device_names = new String[num_capture_devices];
        String[] playback_device_names = new String[num_playback_devices];
        capture_devices = new ArrayList<>(num_capture_devices);
        playback_devices = new ArrayList<>(num_playback_devices);
        jniGetAudioDevices(capture_device_names, playback_device_names);
        for(int i = 0; i < num_capture_devices; i++){
            capture_devices.add(new CaptureDevice(capture_device_names[i], i));
        }
        for(int i = 0; i < num_playback_devices; i++){
            playback_devices.add(new PlaybackDevice(playback_device_names[i], i));
        }
    }

    

    private native int jniStartPlayback(String filepath, int device_index);/*
        return jum_startPlayback(audio, filepath, device_index);
    */
    public void startPlayback(String filepath, PlaybackDevice device) throws FileNotFoundException, IllegalStateException{
        // make sure audio hasnt been disposed
        if(!audio_initialized){
            throw new IllegalStateException("Audio playback without initialized audio context");
        }
        int index = -1;
        if(playback_devices.contains(device)){
            index = device.index;
        }
        if(jniStartPlayback(filepath, index) != 0){
            throw new FileNotFoundException("Could not start playback for file " + filepath);
        }
    }
    public void startPlayback(String filepath) throws FileNotFoundException, IllegalStateException{
        startPlayback(filepath, null);
    }


    private native int jniStartCapture(int device_index);/*
        return jum_startCapture(audio, device_index);
    */
    public void startCapture(CaptureDevice device) throws IllegalStateException{
        // make sure audio hasnt been disposed
        if(!audio_initialized){
            throw new IllegalStateException("Audio capture without initialized audio context");
        }
        int index = -1;
        if(capture_devices.contains(device)){
            index = device.index;
        }
        if(jniStartCapture(index) != 0){
            throw new IllegalStateException("Could not start audio capture");
        }
    }
    public void startCapture() throws IllegalStateException{
        startCapture(null);
    }

    private native void jniInitFFT(float[][] freqs, int num_freqs, float[][] weights, int num_weights, int fft_buf_size, int num_bins); /*
        // copy java object array of float[] into a c++ double array of float[][]
        int i;
        float c_freqs[num_freqs][2];
        for(i = 0; i<num_freqs; i++){
            jfloatArray jarray = (jfloatArray) (env->GetObjectArrayElement(freqs, i));
            jfloat carray[2];
            env->GetFloatArrayRegion(jarray, 0, 2, carray);
            c_freqs[i][0] = carray[0];
            c_freqs[i][1] = carray[1];
        }

        float c_weights[num_weights][2];
        for(i = 0; i<num_weights; i++){
            jfloatArray jarray = (jfloatArray) (env->GetObjectArrayElement(weights, i));
            jfloat carray[2];
            env->GetFloatArrayRegion(jarray, 0, 2, carray);
            c_weights[i][0] = carray[0];
            c_weights[i][1] = carray[1];
        }

        fft = jum_initFFT(c_freqs, num_freqs, c_weights, num_weights, fft_buf_size, num_bins);
    */
    public void initFFT(float[][] freqs, float[][] weights, int fft_buf_size, int num_bins){
        this.freqs = freqs;
        this.weights = weights;
        this.fft_buf_size = fft_buf_size;
        this.num_bins = num_bins;
        initFFT();
    }

    public void initFFT(){
        jniInitFFT(freqs, freqs.length, weights, weights.length, fft_buf_size, num_bins);
        this.fft_result = new float[num_bins];
        this.fft_raw = new float[num_bins];
        fft_initialized = true;
    }


    private native int jniAnalyzeFFT(float[] result, float[] raw, int msec); /*
        if(!audio || !fft)
            return -1;
        
        jum_analyze(fft, audio, msec);
        env->SetFloatArrayRegion(obj_result, 0, fft->num_bins, fft->result);
        env->SetFloatArrayRegion(obj_raw, 0, fft->num_bins, fft->raw);
        return 0;
    */
    public void analyzeFFT(int msec){
        if(msec < 0){
            throw new InvalidParameterException("invalid params provided to analyzeFFT");
        }
        if(!fft_initialized){
            // should initialize fft before trying to do this, but if not just init with defaults
            initFFT();
        }
        jniAnalyzeFFT(this.fft_result, this.fft_raw, msec);
    }

    private native void jniSetAmplitude(float amplitude);/*
        jum_setAmplitude(audio, amplitude);
    */
    public void setAmplitude(float amplitude){
        jniSetAmplitude(amplitude);
    }

    private native float jniGetCursor();/*
        return jum_getCursor(audio);
    */
    public float getCursor(){
        return jniGetCursor();
    }

    private native void jniPausePlayback();/*
        return jum_pausePlayback(audio);
    */
    public void pause(){
        jniPausePlayback();
    }

    private native void jniResumePlayback();/*
        return jum_resumePlayback(audio);
    */
    public void resume(){
        jniResumePlayback();
    }

    private native void jniDisposeAudio();/*
        jum_deinitAudio(audio);
    */
    private native void jniDisposeFFT();/*
        jum_deinitFFT(fft);
    */
    @Override
    public void dispose() {
        if(audio_initialized){
            jniDisposeAudio();
        }
        if(fft_initialized){
            jniDisposeFFT();
        }
    }


    public static void main (String[] args) throws Exception {
        JumAudio audio = new JumAudio();
        audio.initFFT();
        for(PlaybackDevice p : audio.playback_devices){
            System.out.println(p.name);
        }
        for(CaptureDevice c : audio.capture_devices){
            System.out.println(c.name);
        }

        audio.startPlayback("/home/hunter/Music/test.flac");
        audio.setAmplitude(0.3f);
        // audio.startCapture();
        for(int i = 0; i < 1000; i++){
            Thread.sleep(16, 0);
            audio.analyzeFFT(16);
        }
        audio.dispose();
    }
}