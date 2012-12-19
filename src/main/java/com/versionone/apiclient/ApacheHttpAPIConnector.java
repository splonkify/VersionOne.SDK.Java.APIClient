/**
 * 
 */
package com.versionone.apiclient;

import java.io.ByteArrayInputStream;
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
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.ByteArrayBuffer;

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
	
	private byte[] execute(HttpUriRequest request) throws ConnectionException {
		HttpResponse response;
		try {
			response = this.httpclient.execute(request);
		} catch (IOException e1) {
			throw new ConnectionException("Error executing HTTP request " + request.getURI().toString(), -1, e1);
		}
		StatusLine status = response.getStatusLine();
		HttpEntity entity = response.getEntity();
		log.fine("RESPONSE -----------------------------------------------------");
		log.fine(response.getStatusLine().toString());
		
		
		byte[] body;
		try {
			body = IOUtils.toByteArray(entity.getContent());
		} catch (IllegalStateException e1) {
			throw new ConnectionException("Error reading HTTP response " + request.getURI().toString(), status.getStatusCode(), e1);
		} catch (IOException e1) {
			throw new ConnectionException("Error reading HTTP response " + request.getURI().toString(), status.getStatusCode(), e1);
		}
		//String body = IOUtils.toString(entity.getContent(), "UTF-8");
		if(status.getStatusCode() > 200 ) {
			try {
				log.fine(new String(body, "UTF-8"));
			} catch (UnsupportedEncodingException e) {
				log.fine("Non-utf8 body");
			}
		}
		log.fine("--------------------------------------------------------------");
		if(status.getStatusCode() >= 400) {
			String message = "Received error " + status.getStatusCode() + " from URL " + request.getURI().toString();
			throw new ConnectionException(message, status.getStatusCode());
		}
		
		return body;
	}
	
	/* (non-Javadoc)
	 * @see com.versionone.apiclient.IAPIConnector#getData(java.lang.String)
	 */
	
	public Reader getData(String path) throws ConnectionException {
		String myurl = this.url + path;
		HttpGet request = new HttpGet(myurl);
		request.setHeaders(getCustomHeaders());
		log.fine("REQUEST: Getting url: " + myurl);
		byte[] body = this.execute(request);
		try {
			String bodystr = new String(body, "UTF-8");
			return new StringReader(bodystr);
		} catch (UnsupportedEncodingException e) {
			throw new ConnectionException("Server returned non-UTF8 data", e);
		}
		
		//return new StringReader(this.execute(request));
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
		byte[] body = this.execute(request);
		try {
			String bodystr = new String(body, "UTF-8");
			return new StringReader(bodystr);
		} catch (UnsupportedEncodingException e) {
			throw new ConnectionException("Server returned non-UTF8 data", e);
		}
	}
	
	/* (non-Javadoc)
	 * @see com.versionone.apiclient.IAPIConnector#beginRequest(java.lang.String, java.lang.String)
	 * 
	 * we're going to keep a string around to hold the request body instead of using all streams.
	 * That way it's debuggable!
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
			byte[] body = outstream.toByteArray();
			
			log.fine("REQUEST size: " + outstream.size());
			try {
				log.fine(new String(body, "UTF-8"));
			} catch (UnsupportedEncodingException e) {
				log.fine("Non-UTF8 body of size " + body.length);
			}
			ByteArrayEntity postbody = new ByteArrayEntity(body);
			postbody.setContentType(contentType);
			log.fine("ENDREQUEST: Posting url: " + url);
			request = new HttpPost(url);
			((HttpPost) request).setEntity(postbody);
		} else {
			log.fine("ENDREQUEST: Getting url: " + url);
			request = new HttpGet(url);
		}

		log.fine("REQUEST: Getting url: " + url);
		byte[] body = this.execute(request);
		return new ByteArrayInputStream(body);
	}
	
	

	/*
	private Map<String, ImmutablePair<OutputStream, Thread>> streamedRequests;
	private Map<String, ImmutablePair<HttpResponse, Exception>> completedResponses;
	
	public OutputStream streamed_beginRequest(final String path, final String contentType) throws ConnectionException {
		if(this.streamedRequests == null) {
			this.streamedRequests = new HashMap<String, ImmutablePair<OutputStream, Thread>>();
			this.completedResponses = new HashMap<String, ImmutablePair<HttpResponse, Exception>>();
		}
		final PipedInputStream streamForClientToRead = new PipedInputStream();
		final PipedOutputStream streamForCallerToWrite;
		try {
			streamForCallerToWrite = new PipedOutputStream(streamForClientToRead);
		} catch (IOException e) {
			throw new ConnectionException("Error starting request", e);
		}
		// start the request in another thread to consume the piped input that our caller will writing
		// to the pipedoutputstream we return to them.
		Runnable threadAction = new Runnable(){
			public void run(){
				try {
					InputStreamEntity rawbody = new InputStreamEntity(streamForClientToRead, -1, ContentType.create(contentType));
					/// the following guy just converts the body to a bytearray if it needs to be repeated!
					BufferedHttpEntity postbody = new BufferedHttpEntity(rawbody);
					HttpPost request = new HttpPost(url + path);
					request.setEntity(postbody);
					HttpResponse response = httpclient.execute(request);
					completedResponses.put(path, new ImmutablePair<HttpResponse, Exception>(response, null));
				} catch (ClientProtocolException e) {
					completedResponses.put(path, new ImmutablePair<HttpResponse, Exception>(null, e));
				} catch (IOException e) {
					completedResponses.put(path, new ImmutablePair<HttpResponse, Exception>(null, e));
				}
	      }
	    };
	    
		Thread requestRunner = new Thread(threadAction);
		requestRunner.start();
		this.streamedRequests.put(path, new ImmutablePair<OutputStream, Thread>(streamForCallerToWrite, requestRunner));
		return streamForCallerToWrite;
		}


	
	public InputStream streamed_endRequest(String path) throws ConnectionException {
		if(!streamedRequests.containsKey(path)) {
			throw new ConnectionException("Must begin request before ending it");
		}
		ImmutablePair<OutputStream, Thread> storedReq = this.streamedRequests.get(path);
		try {
			storedReq.left.close();
		} catch (IOException e1) {
			throw new ConnectionException("Request failed.", e1);
		}
		Thread requestRunner = storedReq.right;
		streamedRequests.remove(path);
		try {
			requestRunner.join();
		} catch (InterruptedException e) {
			throw new ConnectionException("Request interrupted.", e);
		}
		//request.releaseConnection();
		ImmutablePair<HttpResponse, Exception> finishedResponse = completedResponses.get(path);
		HttpResponse response = finishedResponse.left;
		Exception execException = finishedResponse.right;
		if(execException != null) {
			throw new ConnectionException("Error executing HTTP request", execException);
		}
		try {
			return response.getEntity().getContent();
		} catch (IllegalStateException e) {
			throw new ConnectionException("Error reading response", e);
		} catch (IOException e) {
			throw new ConnectionException("Error reading response", e);
		}
		
	}
	*/
}
