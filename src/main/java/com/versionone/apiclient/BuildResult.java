package com.versionone.apiclient;

import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class BuildResult {
    public final List<BasicNameValuePair> querystringParts = new ArrayList<BasicNameValuePair>();
    public final List<String> pathParts = new ArrayList<String>();

    public String toUrl() {
    	ArrayList<String> encodedParts = new ArrayList<String>();
    	for(int i=0; i<pathParts.size() ; i++) {
    		try {
				encodedParts.add(URLEncoder.encode(pathParts.get(i), "UTF-8"));
			} catch (UnsupportedEncodingException e) {
				throw new IllegalArgumentException("Query path part is not encodable as UTF-8", e);
			}
    	}

        String path = TextBuilder.join(encodedParts, "/");
        String querystring = URLEncodedUtils.format(querystringParts, "UTF-8");

        if(querystring != null) {
        	return path.concat("?" + querystring);
        }
        return path;
    }
}
