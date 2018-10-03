/**************************************************
 * Android Web Server
 * Based on JavaLittleWebServer (2008)
 * <p/>
 * Copyright (c) Piotr Polak 2008-2016
 **************************************************/

package ro.polak.http.servlet.impl;

import java.io.BufferedReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

import ro.polak.http.Headers;
import ro.polak.http.RequestStatus;
import ro.polak.http.Statistics;
import ro.polak.http.protocol.parser.MalformedInputException;
import ro.polak.http.protocol.parser.impl.LocaleParser;
import ro.polak.http.servlet.Cookie;
import ro.polak.http.servlet.HttpServletRequest;
import ro.polak.http.servlet.HttpSession;
import ro.polak.http.servlet.ServletContext;
import ro.polak.http.servlet.UploadedFile;
import ro.polak.http.utilities.StringUtilities;

import static java.util.TimeZone.getTimeZone;

/**
 * HTTP request wrapper.
 *
 * @author Piotr Polak piotr [at] polak [dot] ro
 * @since 200802
 */
public final class HttpRequestImpl implements HttpServletRequest {

    public static final String METHOD_CONNECT = "CONNECT";
    public static final String METHOD_DELETE = "DELETE";
    public static final String METHOD_GET = "GET";
    public static final String METHOD_HEAD = "HEAD";
    public static final String METHOD_OPTIONS = "OPTIONS";
    public static final String METHOD_PURGE = "PURGE";
    public static final String METHOD_PATCH = "PATCH";
    public static final String METHOD_POST = "POST";
    public static final String METHOD_PUT = "PUT";
    public static final String METHOD_TRACE = "TRACE";
    private static final String DATE_FORMAT = "EEE, d MMM yyyy HH:mm:ss z";
    private static final int HTTP_PORT = 80;
    private static final int HTTPS_PORT = 433;

    private Map<String, String> postParameters;
    private Map<String, String> getParameters;

    private RequestStatus status;
    private Headers headers;
    private boolean isMultipart = false;
    private String remoteAddr;

    private Map<String, Cookie> cookies;
    private Collection<UploadedFile> uploadedFiles;
    private HttpSessionImpl session;
    private boolean sessionWasRequested = false;
    private ServletContextImpl servletContext;
    private Map<String, Object> attributes;
    private String characterEncoding = StandardCharsets.UTF_8.name();

    private InputStream in;
    private String localAddr;
    private int localPort;
    private String remoteHost;
    private int remotePort;
    private int serverPort;
    private String localName;
    private String serverName;
    private String scheme;
    private boolean isSecure;
    private String pathTranslated;
    private String pathInfo;
    private String remoteUser;
    private Principal principal;

    /**
     * Default constructor.
     */
    public HttpRequestImpl() {
        Statistics.incrementRequestHandled();
        postParameters = new HashMap<>();
        getParameters = new HashMap<>();
        uploadedFiles = new HashSet<>();
        attributes = new HashMap<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getRequestURI() {
        return status.getUri();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StringBuilder getRequestURL() {
        StringBuilder url = new StringBuilder();
        url.append(getScheme()).append("://").append(getHost());

        int port = getServerPort();
        if (port != HTTP_PORT && port != HTTPS_PORT) {
            url.append(':').append(port);
        }
        url.append(status.getUri());

        return url;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getHeader(final String name) {
        return headers.getHeader(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getIntHeader(final String name) {
        if (!headers.containsHeader(name)) {
            return -1;
        }

        try {
            return Integer.parseInt(getHeader(name));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getRemoteAddr() {
        return remoteAddr;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isMultipart() {
        return isMultipart;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Headers getHeaders() {
        return headers;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getDateHeader(final String name) {
        if (!headers.containsHeader(name)) {
            return -1;
        }

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(DATE_FORMAT, Locale.US);
        simpleDateFormat.setTimeZone(getTimeZone("GMT"));
        try {
            Date date = simpleDateFormat.parse(headers.getHeader(name));
            return date.getTime();
        } catch (ParseException e) {
            return -1;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Enumeration getHeaderNames() {
        return Collections.enumeration(headers.keySet());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Cookie[] getCookies() {
        Cookie[] cookiesArray = new Cookie[cookies.size()];
        cookies.values().toArray(cookiesArray);

        return cookiesArray;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getQueryString() {
        return status.getQueryString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getRequestedSessionId() {
        Cookie sessionCookie = getCookie(HttpSessionImpl.COOKIE_NAME);
        if (sessionCookie == null) {
            return null;

        }
        return sessionCookie.getValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getAttribute(final String name) {
        return attributes.get(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Enumeration getAttributeNames() {
        return Collections.enumeration(attributes.keySet());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCharacterEncoding() {
        return characterEncoding;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getContentLength() {
        if (!headers.containsHeader(Headers.HEADER_CONTENT_LENGTH)) {
            return -1;
        }
        return getIntHeader(Headers.HEADER_CONTENT_LENGTH);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getContentType() {
        return headers.getHeader(Headers.HEADER_CONTENT_TYPE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStream getInputStream() {
        return in;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLocalAddr() {
        return localAddr;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Locale getLocale() {
        Enumeration<Locale> locales = getLocales();
        if (locales == null) {
            return null;
        }

        return locales.nextElement();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Enumeration getLocales() {
        if (!StringUtilities.isEmpty(headers.getHeader(Headers.HEADER_ACCEPT_LANGUAGE))) {
            try {
                return Collections.enumeration(
                        new LocaleParser().parse(headers.getHeader(Headers.HEADER_ACCEPT_LANGUAGE)));
            } catch (MalformedInputException e) {
                // return null
            }
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLocalName() {
        return localName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLocalPort() {
        return localPort;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map getParameterMap() {
        String method = getMethod().toUpperCase();
        if (method.equals(METHOD_POST) || method.equals(METHOD_PUT)) {
            return postParameters;
        }

        return getParameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Enumeration getParameterNames() {
        return Collections.enumeration(getParameterMap().keySet());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String[] getParameterValues(final String name) {

        // TODO Implement parsing array query strings, like ?a[]=1&a[]=2
        throw new IllegalStateException("Not implemented");

//        String[] values = new String[getParameterMap().size()];
//        getParameterMap().values().toArray(values);
//        return values;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProtocol() {
        return status.getProtocol();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BufferedReader getReader() {
        throw new IllegalStateException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getRemoteHost() {
        return remoteHost;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getRemotePort() {
        return remotePort;
    }

    //  RequestDispatcher getRequestDispatcher(String path)

    /**
     * {@inheritDoc}
     */
    @Override
    public String getScheme() {
        return scheme;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getServerName() {
        return serverName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getServerPort() {
        return serverPort;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSecure() {
        return isSecure;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeAttribute(final String name) {
        attributes.remove(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAttribute(final String name, final Object o) {
        attributes.put(name, o);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCharacterEncoding(final String characterEncoding) {
        this.characterEncoding = characterEncoding;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMethod() {
        return status.getMethod();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<UploadedFile> getUploadedFiles() {
        return uploadedFiles;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Cookie getCookie(final String cookieName) {
        if (cookies.containsKey(cookieName)) {
            return cookies.get(cookieName);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getParameter(final String paramName) {
        return getParameters.get(paramName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPostParameter(final String paramName) {
        return postParameters.get(paramName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HttpSession getSession(final boolean create) {
        getSessionInstance();

        if (session == null && create) {
            session = servletContext.createNewSession();
        }

        if (session != null) {
            session.setLastAccessedTime(System.currentTimeMillis());
        }

        return session;
    }

    private HttpSession getSessionInstance() {
        if (!sessionWasRequested) {
            sessionWasRequested = true;
            String sessionId = getRequestedSessionId();
            if (sessionId != null) {
                session = servletContext.getSession(sessionId);
            }
        }

        return session;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HttpSession getSession() {
        return getSession(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAuthType() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getContextPath() {
        return servletContext.getContextPath();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPathTranslated() {
        return pathTranslated;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPathInfo() {
        return pathInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getRemoteUser() {
        return remoteUser;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Principal getUserPrincipal() {
        return principal;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return true; // Hardcoded, only cookie implementation exists
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRequestedSessionIdFromURL() {
        return false; // Hardcoded, only cookie implementation exists
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRequestedSessionIdValid() {
        return getSessionInstance() != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isUserInRole(final String role) {
        return false; // Not really implemented
    }

    /**
     * Sets the servlet context.
     *
     * @param servletContext
     */
    public void setServletContext(final ServletContextImpl servletContext) {
        this.servletContext = servletContext;
    }

    public void setRemoteAddr(final String remoteAddr) {
        this.remoteAddr = remoteAddr;
    }

    public void setHeaders(final Headers headers) {
        this.headers = headers;
    }

    public void setUploadedFiles(final Collection<UploadedFile> uploadedFiles) {
        this.uploadedFiles = uploadedFiles;
    }

    public void setGetParameters(final Map<String, String> getParameters) {
        this.getParameters = getParameters;
    }

    public void setPostParameters(final Map<String, String> postParameters) {
        this.postParameters = postParameters;
    }

    public void setStatus(final RequestStatus status) {
        this.status = status;
    }

    public void setCookies(final Map<String, Cookie> cookies) {
        this.cookies = cookies;
    }

    public void setInputStream(final InputStream in) {
        this.in = in;
    }

    public void setLocalPort(final int localPort) {
        this.localPort = localPort;
    }

    public void setRemoteHost(final String remoteHost) {
        this.remoteHost = remoteHost;
    }

    public void setRemotePort(final int remotePort) {
        this.remotePort = remotePort;
    }

    public void setLocalAddr(final String localAddr) {
        this.localAddr = localAddr;
    }

    public void setServerPort(final int serverPort) {
        this.serverPort = serverPort;
    }

    public void setLocalName(final String localName) {
        this.localName = localName;
    }

    public void setServerName(final String serverName) {
        this.serverName = serverName;
    }

    public void setScheme(final String scheme) {
        this.scheme = scheme;
    }

    public void setSecure(final boolean secure) {
        isSecure = secure;
    }

    public void setMultipart(final boolean multipart) {
        isMultipart = multipart;
    }

    public void setPathTranslated(final String pathTranslated) {
        this.pathTranslated = pathTranslated;
    }

    public void setPathInfo(final String pathInfo) {
        this.pathInfo = pathInfo;
    }

    public void setRemoteUser(final String remoteUser) {
        this.remoteUser = remoteUser;
    }

    public void setPrincipal(final Principal principal) {
        this.principal = principal;
    }

    /**
     * Returns requested host name.
     *
     * @return
     */
    private String getHost() {
        String host;
        if (headers.containsHeader(Headers.HEADER_HOST)) {
            // Strip port number
            host = headers.getHeader(Headers.HEADER_HOST).split(":")[0];
        } else {
            host = getLocalAddr();
        }
        return host;
    }
}
