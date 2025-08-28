package ua.beengoo.logdo2.api.model;

public record OAuthOutcome(OAuthResultType type, String message) {
    public static OAuthOutcome ok() { return new OAuthOutcome(OAuthResultType.SUCCESS, "OK"); }
    public static OAuthOutcome error(OAuthResultType t, String msg) { return new OAuthOutcome(t, msg); }
}
