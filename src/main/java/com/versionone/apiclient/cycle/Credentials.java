package com.versionone.apiclient.cycle;

import com.versionone.apiclient.ICredentials;

public final class Credentials implements ICredentials {
    private String user;
    private String password;

    public Credentials(String user, String password) {
        super();
        this.user = user;
        this.password = password;
    }

    public String getV1UserName(){
        return user;
    }

    public String getV1Password(){
        return password;
    }

    public String getProxyUserName(){
        return user;
    }

    public String getProxyPassword(){
        return password;
    }

}