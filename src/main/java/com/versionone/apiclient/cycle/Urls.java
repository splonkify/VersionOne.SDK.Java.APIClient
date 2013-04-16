package com.versionone.apiclient.cycle;

import com.versionone.apiclient.IUrls;

public final class Urls implements IUrls {
    
    private String server;
    private String app;

    public Urls(String server, String app) {
        super();
        this.server = server;
        this.app = app;
    }

    public String getV1Url(){
        return "https://" + server + "/" + app + "/";
    }
    public String getMetaUrl(){
        return getV1Url().concat("meta.v1/");
    }
    public String getDataUrl(){
        return getV1Url().concat("rest-1.v1/");
    }
    public String getConfigUrl(){
        return getV1Url().concat("config.v1/");
    }
    public String getProxyUrl() {
        return "https://" + server + "/" + app + "/";
    }
}
