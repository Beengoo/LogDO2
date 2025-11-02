package ua.beengoo.logdo2.api.provider;

public final class Properties {
    public final long bedrockCodeTimeAfterLeave;
    public final int bedrockLimitPerDiscord;
    public final int javaLimitPerDiscord;
    public final boolean limitIncludeReserved;
    public final boolean bansEnabled;
    public final long banTrackWindowSec;
    public final double banMultiplier;
    public final double banBaseSec;
    public final long banMaxSec;
    public final boolean disallowSimultaneousPlay;

    public Properties(long bedrockCodeTimeAfterLeave, int bedrockLimitPerDiscord, int javaLimitPerDiscord,
                      boolean limitIncludeReserved, boolean bansEnabled, long banTrackWindowSec, double banMultiplier,
                      double banBaseSec, long banMaxSec, boolean disallowSimultaneousPlay) {
        this.bedrockCodeTimeAfterLeave = bedrockCodeTimeAfterLeave;
        this.bedrockLimitPerDiscord = bedrockLimitPerDiscord;
        this.javaLimitPerDiscord = javaLimitPerDiscord;
        this.limitIncludeReserved = limitIncludeReserved;
        this.bansEnabled = bansEnabled;
        this.banTrackWindowSec = banTrackWindowSec;
        this.banMultiplier = banMultiplier;
        this.banBaseSec = banBaseSec;
        this.banMaxSec = banMaxSec;
        this.disallowSimultaneousPlay = disallowSimultaneousPlay;
    }
}
