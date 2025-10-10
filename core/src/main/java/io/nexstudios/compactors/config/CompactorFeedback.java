package io.nexstudios.compactors.config;

import lombok.Value;

@Value
public class CompactorFeedback {
    String chatMessage;
    boolean soundEnabled;
    String soundType;
    float soundVolume;
    float soundPitch;
    int rateLimitTicks;
}