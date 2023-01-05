package com.HunterWhyte.jumaudio;

import com.badlogic.gdx.utils.SharedLibraryLoader;

public class JumAudio {
    static { // static initialization block
        new SharedLibraryLoader().load("gdx-jumaudio");
    }

    // @off
    /*JNI
    #define JUMAUDIO_IMPLEMENTATION
    #include "jumaudio.h"

    #define FFT_BUF_SIZE 4096
    // number of callback buffers to predecode ahead of writing to stream
    #define PREDECODE_BUFS 5
    #define NUM_BINS 1024
    // weighting applied to frequency bins, [0] = freq, [1] = weight in db
    // TODO make this runtime configurable
    #define NUM_WEIGHTS 15
    const float weights[NUM_WEIGHTS][2] = {{63, -5},    {200, -5},   {250, -5},   {315, -5},
                                        {400, -4.8}, {500, -3.2}, {630, -1.9}, {800, -0.8},
                                        {1000, 0.0}, {1250, 0.6}, {1600, 1.0}, {2000, 1.2},
                                        {2500, 3.3}, {3150, 4.2}, {4000, 5.0}};

    #define NUM_FREQS 10
    const float freqs[NUM_FREQS][2] = {{0, 35},      {0.2, 450},  {0.3, 700},  {0.4, 1200},
                                       {0.5, 1700},  {0.6, 2600}, {0.7, 4100}, {0.8, 6500},
                                       {0.9, 10000}, {1.0, 20000}};

    jum_AudioSetup* audio;
    jum_FFTSetup* fft;
    */
    
    public native int jniInitAudio(int buffer_size, int predecode_bufs, int period);/*
        audio = jum_initAudio(buffer_size, predecode_bufs, period);
        return (audio == NULL);
    */

    public native int jniStartPlayback(String filepath);/*
        return jum_startPlayback(audio, filepath, -1);
    */

    public native int jniInitFFT(); /*
        fft = jum_initFFT(freqs, NUM_FREQS, weights, NUM_WEIGHTS, FFT_BUF_SIZE, NUM_BINS);
        return (fft == NULL);
    */

    static public native int add (int a, int b); /*
        return a + b;
    */

    public static void main (String[] args) throws Exception {
        int FFT_BUF_SIZE = 4096;
        int PREDECODE_BUFS = 5;
        System.out.println(add(1, 2));
        JumAudio audio = new JumAudio();
        audio.jniInitAudio(FFT_BUF_SIZE * (PREDECODE_BUFS + 5), PREDECODE_BUFS, FFT_BUF_SIZE);
        System.out.println(audio.jniStartPlayback("/home/hunter/Music/test.flac"));
        Thread.sleep(100000);
    }
}