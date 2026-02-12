package ua.beengoo.logdo2.api.entity;

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
}
