/**
 * 
 */
package com.versionone.apiclient;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

/**
 * @author JKoberg
 *
 */



public class ApacheHttpAPIConnector implements IAPIConnector {
	private String url;
	private Credentials creds;
	protected DefaultHttpClient httpclient;
	private Map<String,ImmutablePair<String, ByteArrayOutputStream>> startedRequests;
	private ProxyProvider proxy;
	private Logger log;

	/** Create a connector with credentials to be used for fetching (unauthenticated) metadata
	 * 
	 * @param url The VersionOne instance url.
	 */
	public ApacheHttpAPIConnector( String url) {
		this(url, "", "");
	}
		
	public ApacheHttpAPIConnector( String url, String username, String password) {
		this(url, username, password, null);
	}
	
	public ApacheHttpAPIConnector( String url, String username, String password, ProxyProvider proxy) {
		this.log = Logger.getGlobal();
		this.url = url;
		this.creds = new UsernamePasswordCredentials(username, password);
		this.proxy = proxy;
		if(this.proxy != null) {
			throw new NotImplementedException();
		}
		this.httpclient = new DefaultHttpClient();
		this.httpclient.getCredentialsProvider().setCredentials(AuthScope.ANY, creds);
		this.startedRequests = new HashMap<String, ImmutablePair<String, ByteArrayOutputStream>>();
	}

	/* (non-Javadoc)
	 * @see com.versionone.apiclient.IAPIConnector#getData()
	 */
	
	public Reader getData() throws ConnectionException {
		// Some uses of this expect to create new V1APIConnector(pathpart) and then just call .getData()
		// to get everything from that path part. (IN other words, using additionally-constructed V1APIConnectors to represent different path prefixes)
		return getData("");
	}
	
	protected Header[] getCustomHeaders() {
		// return a dummy for now.  Can be overridden by classes that handle custom headers.
		return new Header[0];
	}
	
	private String execute(HttpUriRequest request) throws ConnectionException {
		//try {
			HttpResponse response;
			try {
				response = this.httpclient.execute(request);
				HttpEntity entity = response.getEntity();
				String body = IOUtils.toString(entity.getContent(), "UTF-8");
				log.fine("RESPONSE -----------------------------------------------------");
				log.fine(response.getStatusLine().toString());
				log.fine(body);
				log.fine("--------------------------------------------------------------");
				int code = response.getStatusLine().getStatusCode();
				if(code >= 400) {
					String message = "Received error " + code + " from URL " + request.getURI().toString();
					throw new ConnectionException(message, code);
				}
				
				return body;
			} catch (ClientProtocolException e) {
				throw new ConnectionException("Error processing request", e);
			} catch (IOException e) {
				throw new ConnectionException("Error processing request", e);
			}
		//} catch (IOException e) {
		//	throw new ConnectionException("Error executing http request", httpclient.);
	    //	}
	}
	
	/* (non-Javadoc)
	 * @see com.versionone.apiclient.IAPIConnector#getData(java.lang.String)
	 */
	
	public Reader getData(String path) throws ConnectionException {
		String myurl = this.url + path;
		HttpGet request = new HttpGet(myurl);
		request.setHeaders(getCustomHeaders());
		log.fine("REQUEST: Getting url: " + myurl);
		return new StringReader(this.execute(request));
	}

	
	/* (non-Javadoc)
	 * @see com.versionone.apiclient.IAPIConnector#sendData(java.lang.String, java.lang.String)
	 */	

	public Reader sendData(String path, String data) throws ConnectionException {
		String myurl = this.url + path;
		log.fine("REQUEST: Posting url: " + myurl);
		log.fine(data);
		HttpPost request = new HttpPost(myurl);
		StringEntity postbody;
		postbody = new StringEntity(data, ContentType.create("text/xml", "UTF-8"));
		request.setEntity(postbody);
		return new StringReader(this.execute(request));
	}
	
	/* (non-Javadoc)
	 * @see com.versionone.apiclient.IAPIConnector#beginRequest(java.lang.String, java.lang.String)
	 */
	
	public OutputStream beginRequest(String path, String contentType) throws ConnectionException {		
		ByteArrayOutputStream outstream = new ByteArrayOutputStream();
		ImmutablePair<String, ByteArrayOutputStream>
		  startedRequest = new ImmutablePair<String, ByteArrayOutputStream>(contentType, outstream);
		startedRequests.put(path, startedRequest);
		return outstream;		
	}

	/* (non-Javadoc)
	 * @see com.versionone.apiclient.IAPIConnector#endRequest(java.lang.String)
	 */
	
	public InputStream endRequest(String path) throws ConnectionException {
		ImmutablePair<String, ByteArrayOutputStream> startedRequest = startedRequests.get(path);
		if(startedRequest == null) {
			throw new ConnectionException("Must begin request before ending it");
		}
		startedRequests.remove(path);
		String contentType = startedRequest.left;
		ByteArrayOutputStream outstream = startedRequest.right;
		
		String url = this.url + path;
		
		HttpUriRequest request;
		
		if(outstream.size() > 0) {
			// TODO: How the hell do we determine the difference between wanting to truncate the file (writing a zero-length file) and wanting to GET the content?????
			try {
				String body = outstream.toString("UTF-8");
				log.fine("REQUEST\n");
				log.fine(body);
				StringEntity postbody = new StringEntity(body);
				postbody.setContentType(contentType);
				request = new HttpPost(url);
				((HttpPost) request).setEntity(postbody);
			} catch (UnsupportedEncodingException e) {
				throw new ConnectionException("Server response could not be decoded as UTF8", e);
			}
		} else {
			request = new HttpGet(url);
		}
		return IOUtils.toInputStream(this.execute(request));
	}
}
