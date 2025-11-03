package me.sweettie.oma.network.whip;

import android.media.AudioTrack;

import org.webrtc.Logging;

class LowLatencyAudioBufferManager {
    private static final String TAG = "LowLatencyAudioBufferManager";

    private int prevUnderrunCount;
    private int ticksUntilNextDecrease;
    private boolean keepLoweringBufferSize;
    private int bufferIncreaseCounter;

    public LowLatencyAudioBufferManager() {
        this.prevUnderrunCount = 0;
        this.ticksUntilNextDecrease = 10;
        this.keepLoweringBufferSize = true;
        this.bufferIncreaseCounter = 0;
    }

    public void maybeAdjustBufferSize(AudioTrack audioTrack) {
        final int underrunCount = audioTrack.getUnderrunCount();
        if (underrunCount > prevUnderrunCount) {
            if (bufferIncreaseCounter < 5) {
                final int currentBufferSize = audioTrack.getBufferSizeInFrames();
                final int newBufferSize = currentBufferSize + audioTrack.getPlaybackRate() / 100;
                Logging.d(TAG,
                        "Underrun detected! Increasing AudioTrack buffer size from " + currentBufferSize
                                + " to " + newBufferSize);
                audioTrack.setBufferSizeInFrames(newBufferSize);
                bufferIncreaseCounter++;
            }
            keepLoweringBufferSize = false;
            prevUnderrunCount = underrunCount;
            ticksUntilNextDecrease = 10;
        } else if (keepLoweringBufferSize) {
            ticksUntilNextDecrease--;
            if (ticksUntilNextDecrease <= 0) {
                final int bufferSize10ms = audioTrack.getPlaybackRate() / 100;
                final int currentBufferSize = audioTrack.getBufferSizeInFrames();
                final int newBufferSize = Math.max(bufferSize10ms, currentBufferSize - bufferSize10ms);
                if (newBufferSize != currentBufferSize) {
                    Logging.d(TAG,
                            "Lowering AudioTrack buffer size from " + currentBufferSize + " to "
                                    + newBufferSize);
                    audioTrack.setBufferSizeInFrames(newBufferSize);
                }
                ticksUntilNextDecrease = 10;
            }
        }
    }
}