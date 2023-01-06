package com.HunterWhyte.jumaudio;

import com.badlogic.gdx.utils.SharedLibraryLoader;

public class JumAudio {
    static { // static initialization block
        new SharedLibraryLoader().load("gdx-jumaudio");
    }

    static final int FFT_BUF_SIZE = 4096;
    // number of callback buffers to predecode ahead of writing to stream
    static final int PREDECODE_BUFS = 5;
    static final int NUM_BINS = 1024;
    // weighting applied to frequency bins, [0] = freq, [1] = weight in db
    static final float[][] weights = {{63, -5},    {200, -5},   {250, -5},   {315, -5},
                                        {400, -4.8f}, {500, -3.2f}, {630, -1.9f}, {800, -0.8f},
                                        {1000, 0.0f}, {1250, 0.6f}, {1600, 1.0f}, {2000, 1.2f},
                                        {2500, 3.3f}, {3150, 4.2f}, {4000, 5.0f}};
    static final float[][] freqs = {{0, 35},      {0.2f, 450},  {0.3f, 700},  {0.4f, 1200},
                                        {0.5f, 1700},  {0.6f, 2600}, {0.7f, 4100}, {0.8f, 6500},
                                        {0.9f, 10000}, {1.0f, 20000}};
    // @off
    /*JNI
    #define JUMAUDIO_IMPLEMENTATION
    #include "jumaudio.h"

    jum_AudioSetup* audio;
    jum_FFTSetup* fft;
    */
    
    public native int jniInitAudio(int buffer_size, int predecode_bufs, int period);/*
        audio = jum_initAudio(buffer_size, predecode_bufs, period);
        return (audio == NULL);
    */

    public native int jniStartPlayback(String filepath, int device_index);/*
        return jum_startPlayback(audio, filepath, device_index);
    */

    public native int jniInitFFT(float[][] freqs, int num_freqs, float[][] weights, int num_weights, int fft_buf_size, int num_bins); /*
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
        return (fft == NULL);
    */

    public native float[] jniAnalyzeFFT(int msec); /*
        jfloatArray jarray = env->NewFloatArray(fft->num_bins);
        if(!audio || !fft){
            return jarray;
        }
        
        jum_analyze(fft, audio, msec);
        env->SetFloatArrayRegion(jarray, 0, fft->num_bins, fft->result);
        return jarray;
    */

    public static void main (String[] args) throws Exception {
        JumAudio audio = new JumAudio();
        audio.jniInitAudio(FFT_BUF_SIZE * (PREDECODE_BUFS + 5), PREDECODE_BUFS, FFT_BUF_SIZE);
        audio.jniInitFFT(freqs, freqs.length, weights, weights.length, FFT_BUF_SIZE, NUM_BINS);
        System.out.println(audio.jniStartPlayback("/home/hunter/Music/test.flac", -1));
        while(true){
            Thread.sleep(16, 0);
            float[] fft = audio.jniAnalyzeFFT(16);
        }
    }
}