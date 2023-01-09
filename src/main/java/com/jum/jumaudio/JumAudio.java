package com.jum.jumaudio;

import java.io.FileNotFoundException;
import java.security.InvalidParameterException;
import java.util.ArrayList;

import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.SharedLibraryLoader;

public class JumAudio implements Disposable{
    static { // static initialization block
        new SharedLibraryLoader().load("gdx-jumaudio");
    }

    public static enum FFT_INPUT_MODE{
        NONE,
        PLAYBACK,
        CAPTURE
    }
    public static class Sound{
        public String filepath;
        protected int handle;
        public Sound(String filepath, int handle){
            this.filepath = filepath;
            this.handle = handle;
        }
    }

    public static class AudioDevice{
        public String name;
        protected int index;
        public boolean is_default;
        public AudioDevice(String name, int index, boolean is_default){
            this.name = name;
            this.index = index;
            this.is_default = is_default;
        }
    }
    public static class CaptureDevice extends AudioDevice{
        public CaptureDevice(String name, int index, boolean is_default) {
            super(name, index, is_default);
        }
    }
    public static class PlaybackDevice extends AudioDevice{
        public PlaybackDevice(String name, int index, boolean is_default) {
            super(name, index, is_default);
        }
    }

    // @off
    /*JNI
    #include "jumaudio.h"
    #include <stdlib.h>
    #define MAX_SOUNDS 32

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
    private boolean engine_initialized = false;
    private boolean audio_initialized = false;
    private boolean fft_initialized = false;

    public float[] fft_raw;
    public float[] fft_result;

    public ArrayList<CaptureDevice> capture_devices;
    private int num_capture_devices;
    public ArrayList<PlaybackDevice> playback_devices;
    private int num_playback_devices;

    public ArrayList<Sound> sounds = new ArrayList<>();

    public float repeat_delay = 0.05F;

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
    private native int jniGetDefaultCaptureIndex();/*
        for (ma_uint32 i = 0; i < audio->capture_device_count; i++) {
            if (audio->capture_device_info[i].isDefault) {
                return i;
            }
        }
        return 0;
    */
    private native int jniGetDefaultPlaybackIndex();/*
        for (ma_uint32 i = 0; i < audio->playback_device_count; i++) {
            if (audio->playback_device_info[i].isDefault) {
                return i;
            }
        }
        return 0;
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
        int default_capture = jniGetDefaultCaptureIndex();
        int default_playback = jniGetDefaultPlaybackIndex();
        for(int i = 0; i < num_capture_devices; i++){
            capture_devices.add(new CaptureDevice(capture_device_names[i], i, (i==default_capture)));
        }
        for(int i = 0; i < num_playback_devices; i++){
            playback_devices.add(new PlaybackDevice(playback_device_names[i], i, (i==default_playback)));
        }
    }

    private native void jniClearSounds();/*
        jum_clearSoundFiles(audio);
    */
    public void clearSounds(){
        sounds.clear();
        jniClearSounds();
    }

    // returns index into sounds array as sound handle
    private native int jniLoadSound(String filepath); /*
        return jum_loadSound(audio, filepath);
    */
    public Sound loadSound(String filepath) throws IllegalStateException, FileNotFoundException{
        Sound s = null;
        if(!audio_initialized){
            throw new IllegalStateException("attempting to load sound file before initializing audio context: " + filepath);
        }
        int result = jniLoadSound(filepath);
        if(result < 0){
            throw new FileNotFoundException("could load sound file: " + filepath);
        }
        s = new Sound(filepath, result); 
        sounds.add(s);
        return s;
    }

    private native int jniPlaySound(int sound_handle, float repeat_delay);/*
        return jum_playSound(audio, sound_handle, repeat_delay);
    */
    public void playSound(Sound sound) throws IllegalStateException{
        if(audio_initialized && sounds.contains(sound)){
            jniPlaySound(sound.handle, repeat_delay);
        } else {
            throw new IllegalStateException("could not play sound " + sound.filepath);
        }
    }


    private native int jniplaySong(String filepath);/*
        if(fft!=NULL)
            fft->max = 2.5; // reset normalization of fft
        return jum_playSong(audio, filepath);
    */
    public void playSong(String filepath) throws FileNotFoundException, IllegalStateException{
        if(!audio_initialized){
            throw new IllegalStateException("Attempted to play song without initialized audio context");
        }
        int result = jniplaySong(filepath);
        if(jniplaySong(filepath) != 0){
            throw new FileNotFoundException("Could not start playback for file " + filepath);
        }
    }

    private native int jniOpenPlaybackDevice(int device_index);/*
        if(fft!=NULL)
            fft->max = 2.5; // reset normalization of fft
        return jum_openPlaybackDevice(audio, device_index);
    */
    public void openPlaybackDevice(PlaybackDevice device) throws IllegalStateException{
        // make sure audio hasnt been disposed
        if(!audio_initialized){
            throw new IllegalStateException("Audio playback opened without initialized audio context");
        }

        int index = -1;
        if(playback_devices.contains(device)){
            index = device.index;
        }
        if(jniOpenPlaybackDevice(index) != 0){
            throw new IllegalStateException("Could not start audio playback");
        }
    }
    public void openPlaybackDevice() throws IllegalStateException{
        openPlaybackDevice(null);
    }


    private native int jniOpenCaptureDevice(int device_index);/*
        if(fft!=NULL)
            fft->max = 2.5; // reset normalization of fft
        return jum_openCaptureDevice(audio, device_index);
    */
    public void openCaptureDevice(CaptureDevice device) throws IllegalStateException{
        // make sure audio hasnt been disposed
        if(!audio_initialized){
            throw new IllegalStateException("Audio capture without initialized audio context");
        }

        int index = -1;
        if(capture_devices.contains(device)){
            index = device.index;
        }
        if(jniOpenCaptureDevice(index) != 0){
            throw new IllegalStateException("Could not start audio capture");
        }
    }
    public void openCaptureDevice() throws IllegalStateException{
        openCaptureDevice(null);
    }


    private native void jniSetFFTInputMode(int mode);/*
        jum_AudioMode jum_mode = AUDIO_MODE_NONE;
        if(mode==1)
            jum_mode = AUDIO_MODE_PLAYBACK;
        else if(mode ==2)
            jum_mode = AUDIO_MODE_CAPTURE;

        jum_setFFTMode(audio, jum_mode);
    */
    public void setFFTInputMode(FFT_INPUT_MODE mode){
        jniSetFFTInputMode(mode.ordinal());
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

    private native void jniSetMusicVolume(float volume);/*
        jum_setMusicVolume(audio, volume);
    */
    public void setMusicVolume(float volume){
        jniSetMusicVolume(volume);
    }

    private native void jniSetOtherVolume(float volume);/*
        jum_setOtherVolume(audio, volume);
    */
    public void setOtherVolume(float volume){
        jniSetOtherVolume(volume);
    }

    private native float jniGetSongCursor();/*
        return jum_getSongCursor(audio);
    */
    public float getSongCursor(){
        if(!audio_initialized){
            return 0;
        }
        return jniGetSongCursor();
    }

    private native float jniGetSongLength();/*
    return jum_getSongLength(audio);
    */
    public float getSongLength(){
        if(!audio_initialized){
            return 0;
        }
        return jniGetSongLength();
    }

    private native boolean jniSongFinished();/*
        return jum_isSongFinished(audio);
    */
    public boolean songFinished(){
        return jniSongFinished();
    }

    private native float jniGetLevel();/*
    if(fft != NULL){
        return fft->level;
    } else {
        return 0;
    }
    */
    public float getLevel(){
        return jniGetLevel();
    }

    private native void jniPauseSong();/*
        jum_pauseSong(audio);
    */
    public void pause(){
        jniPauseSong();
    }
    private native void jniResumeSong();/*
        jum_resumeSong(audio);
    */
    public void resume(){
        jniResumeSong();
    }

    public int getNumBins(){
        return num_bins;
    }

    private native void jniDisposeAudio();/*
        jum_deinitAudio(audio);
    */
    private native void jniDisposeFFT();/*
        jum_deinitFFT(fft);
    */
    @Override
    public void dispose() {
        clearSounds();
        if(audio_initialized){
            jniDisposeAudio();
        }
        if(fft_initialized){
            jniDisposeFFT();
        }
    }

    // public static void main (String[] args) throws Exception {
    //     JumAudio audio = new JumAudio();
    //     audio.initFFT();
    //     for(PlaybackDevice p : audio.playback_devices){
    //         System.out.println(p.name);
    //     }
    //     for(CaptureDevice c : audio.capture_devices){
    //         System.out.println(c.name);
    //     }

    //     audio.startPlayback("/home/hunter/Music/test.flac");
    //     audio.setAmplitude(0.3f);
    //     // audio.startCapture();
    //     for(int i = 0; i < 1000; i++){
    //         Thread.sleep(16, 0);
    //         audio.analyzeFFT(16);
    //     }
    //     audio.dispose();
    // }
}