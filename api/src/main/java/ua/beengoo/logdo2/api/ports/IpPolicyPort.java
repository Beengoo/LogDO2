package ua.beengoo.logdo2.api.ports;

public interface IpPolicyPort {
    /** Чи дозволяти дію з поточною IP з урахуванням останньої підтвердженої IP. */
    boolean allow(String currentIp, String lastConfirmedIp);
}

