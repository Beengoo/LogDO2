package ua.beengoo.logdo2.api;

import java.time.Instant;
import java.util.Optional;

public record SessionView(
        Optional<PendingLoginView> pendingLogin,
        Optional<PendingIpConfirmView> pendingIpConfirm,
        boolean limitBypassGranted
) {
    public record PendingLoginView(String ip, boolean bedrock, Instant since) {}
    public record PendingIpConfirmView(String newIp, long discordId, Instant since) {}
}
