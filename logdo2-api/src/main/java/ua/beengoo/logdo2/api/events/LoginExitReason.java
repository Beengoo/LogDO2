package ua.beengoo.logdo2.api.events;

public enum LoginExitReason {
    LOGIN_TIMEOUT, IP_CONFIRM_TIMEOUT, IP_CONFIRM_REJECT,
    LOGIN_SUCCESS, IP_CONFIRM_CONFIRMED,
}
