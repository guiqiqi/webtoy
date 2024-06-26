package webtoy;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
 * Parse raw data extarcted from TCP server into an HTTP request.
 * 
 * Format of request follow RFC 2616 standard.
 * See more: https://www.w3.org/Protocols/rfc2616/rfc2616-sec5.html
 */
public class Request {
    public HTTPMethod method;
    public String url;
    public String path;
    public Map<String, String> headers;
    public String body;
    public final Integer bodysize;
    public Map<String, String> forms;

    // Get data form url and headers
    public Map<String, String> args;
    public Map<String, String> cookies;

    public static final String Version = "HTTP/1.1";

    // All methods which has body
    private static final Set<HTTPMethod> HasBodyMethods = Set.of(
            HTTPMethod.POST, HTTPMethod.PUT,
            HTTPMethod.DELETE, HTTPMethod.PATCH);

    /**
     * Throw when server could not parse data to an request.
     */
    public class InvalidRequest extends Exception {
        public InvalidRequest(String message) {
            super(message);
        }
    }

    /**
     * Parse raw HEADER data into an HTTP request.
     * 
     * Request       = Request-Line              ;
     *                 *(( general-header        ;
     *                  | request-header         ;
     *                  | entity-header ) CRLF)  ;
     *                 CRLF
     *                 [ message-body ]          ;
     * 
     * @param data is raw string from TCP server
     */
    public Request(String data) throws InvalidRequest {
        Integer lineno = 0;
        this.args = new HashMap<>();
        this.forms = new HashMap<>();
        this.headers = new HashMap<>();
        this.cookies = new HashMap<>();
        for (String line : data.strip().split("\r\n")) {
            if (lineno++ == 0) {
                this.parseRequestLine(line);
                continue;
            }
            this.parseHeaderLine(line);
        }
        if (HasBodyMethods.contains(this.method))
            if (!this.headers.containsKey("Content-Length"))
                throw new InvalidRequest("content length not indicated in header");
        this.bodysize = Integer.decode(this.headers.getOrDefault("Content-Length", "0"));
    }

    /**
     * Parse body from request.
     * 
     * Only serveral HTTPMethods are allowed to send data, they are in HasBodyMethods.
     * If methods are not allowed in HasBodyMethods, skip.
     * 
     * If Content-Type header set to "application/x-www-form-urlencoded", then the forms
     * could be set to urlDecode result of body.
     * Otherwise body is equal to body data.
     * 
     * @param content of http request body
     * @throws InvalidRequest if cannot parse body from data
     */
    public void parseBody(String body) throws InvalidRequest {
        if (!HasBodyMethods.contains(this.method))
            return;
        if (this.bodysize > body.length())
            throw new InvalidRequest(
                    String.format("content length not matched: %d > %d", this.bodysize, body.length()));
        body = body.substring(0, this.bodysize);
        String contentType = this.headers.getOrDefault("Content-Type", "text/plain");

        // Check if Content-Type is "application/x-www-form-urlencoded"
        this.body = new String();
        if (contentType.equals("application/x-www-form-urlencoded"))
            this.forms = urlDecode(body, "&");
        else
            this.body = body;
    }

    /**
     * Parse header from line.
     * 
     * Header       = header-key : SP header-value
     * 
     * @param line of http request headers part
     * @throws InvalidRequest if cannot parse header from line
     */
    private void parseHeaderLine(String line) throws InvalidRequest {
        String[] parts = line.split(": ");
        if (!(parts.length == 2))
            throw new InvalidRequest(String.format("invalid header line %s", line));
        this.headers.put(parts[0], parts[1]);

        // Parse Cookie
        if (parts[0].equals("Cookie")) {
            this.cookies = urlDecode(parts[1], "; ");
            this.headers.remove(parts[0]);
        }
    }

    /**
     * URL decode x-www-form-urlencoded format and generate dictonary.
     * 
     * Decode string encoded with url pattern like: "Great=Hello%20world&Language=Java"
     * The result should be {"Great": "Hello world", "Language": "Java"}
     * 
     * @param params string for spliting
     * @param delimeter for spliting params
     * @return splited dictionary
     * @throws UnsupportedEncodingException 
     */
    public Map<String, String> urlDecode(String params, String delimeter) throws InvalidRequest {
        Map<String, String> result = new HashMap<>();
        for (String param : params.split(delimeter)) {
            String[] parts = param.split("=", 2);
            try {
                result.put(parts[0], URLDecoder.decode(parts[1], "utf-8"));
            } catch (UnsupportedEncodingException error) {
                throw new InvalidRequest("ooh, why you canot recognize utf-8?");
            // Ignore invalid paramaters
            } catch (IllegalArgumentException error) {
                continue;
            } catch (ArrayIndexOutOfBoundsException error) {
                continue;
            }
        }
        return result;
    }

    /**
     * Parse request line and set request url, path, and method.
     * 
     * Request-Line   = Method SP Request-URI SP HTTP-Version CRLF
     * Method         = "OPTIONS" | "GET" | "HEAD" | "POST" | "PUT" | "DELETE" | "TRACE" | "CONNECT"
     * Request-URI    = "*" | absoluteURI | abs_path | authority
     * HTTP-Version   = "HTTP/1.1"
     * 
     * @param line first in request lines
     */
    private void parseRequestLine(String line) throws InvalidRequest {
        String[] parts = line.split(" ");
        if (!(parts.length == 3))
            throw new InvalidRequest(String.format("invalid request line %s", line));
        try {
            this.method = HTTPMethod.valueOf(parts[0]);
        } catch (IllegalArgumentException error) {
            throw new InvalidRequest(String.format("invalid request method %s", parts[0]));
        }
        this.url = parts[1];
        if (!parts[2].equals(Version))
            throw new InvalidRequest(String.format("invalid request version %s", parts[2]));

        // Parse path and args from url
        parts = this.url.split("\\?", 2);
        this.path = parts[0];
        if (parts.length > 1)
            this.args = urlDecode(parts[1], "&");
    }
}
