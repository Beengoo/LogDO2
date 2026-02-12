package ua.beengoo.logdo2.api.entity;

import org.jetbrains.annotations.NotNull;

public record WebServerInfo(
        String host, int port,
        String displayableUrl,
        String loginEndpoint,
        String callbackEndpoint
){
    public String getPublicLoginURL(){
        return displayableUrl+loginEndpoint;
    }
    public String getPublicCallbackURL(){
        return displayableUrl+callbackEndpoint;
    }

    @Override
    public @NotNull String toString() {
        return "WebServerInfo{" +
                "host=" + host + ", " +
                "port=" + port + ", " +
                "displayableUrl=" + displayableUrl + ", " +
                "publicLoginURL=" + getPublicLoginURL() + ", " +
                "publicCallbackURL=" + getPublicCallbackURL() + ", " +
                "}";
    }
}
