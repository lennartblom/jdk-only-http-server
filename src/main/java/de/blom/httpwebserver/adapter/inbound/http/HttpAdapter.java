package de.blom.httpwebserver.adapter.inbound.http;

import de.blom.httpwebserver.adapter.inbound.http.commons.HttpRequest;
import de.blom.httpwebserver.adapter.inbound.http.commons.ResponseWriter;
import de.blom.httpwebserver.domain.fileserver.DirectoryRequestDto;
import de.blom.httpwebserver.domain.fileserver.DirectoryService;
import de.blom.httpwebserver.domain.fileserver.FileRequestDto;
import de.blom.httpwebserver.enums.HttpMethod;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpAdapter implements Runnable {
    private static final Logger log = Logger.getLogger(HttpAdapter.class.getName());

    private static final int PORT = 8080;
    private static final boolean VERBOSE = true;

    private ResponseWriter responseWriter;
    private Socket connect;
    private DirectoryService directoryService;


    private HttpAdapter(Socket c, String directoryParam) {
        this.responseWriter = new ResponseWriter();
        this.connect = c;
        this.directoryService = new DirectoryService(directoryParam);
    }

    private HttpAdapter(Socket c) {
        this.responseWriter = new ResponseWriter();
        this.connect = c;
        this.directoryService = new DirectoryService();
    }

    HttpAdapter(Socket c, ResponseWriter responseWriter) {
        this(c);
        this.responseWriter = responseWriter;
    }

    HttpAdapter(Socket c, ResponseWriter responseWriter, DirectoryService directoryService) {
        this(c, responseWriter);
        this.directoryService = directoryService;
    }

    public static void main(String[] args) {
        try {
            ServerSocket serverConnect = new ServerSocket(PORT);
            log.info("Server started.\nListening for connections on port : " + PORT + " ...\n");

            String directoryParam = null;
            if(args.length == 1){
                directoryParam = args[0];
            }

            while (true) {
                HttpAdapter httpAdapter = new HttpAdapter(serverConnect.accept(), directoryParam);

                if (VERBOSE) {
                    log.info("Connection opened. (" + new Date() + ")");
                }

                // create dedicated thread to manage the client connection
                Thread thread = new Thread(httpAdapter);
                thread.start();
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Server Connection error : " + e.getMessage(), e);
        }
    }

    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(this.connect.getInputStream()));
             PrintWriter httpResponseHead = new PrintWriter(this.connect.getOutputStream());
             BufferedOutputStream httpResponseBody = new BufferedOutputStream(this.connect.getOutputStream())
        ) {
            HttpRequest httpRequest = HttpRequest.parseFrom(in);

            if(httpRequest == null){
                return;
            }

            this.handleHttpMethod(httpResponseHead, httpResponseBody, httpRequest);

        } catch (IOException ioe) {
            log.log(Level.SEVERE, "Server error : " + ioe.getMessage(), ioe);
        } finally {
            if (VERBOSE) {
                log.info("Connection closed.\n");
            }
        }


    }

    void handleHttpMethod(PrintWriter httpResponseHead, BufferedOutputStream httpResponseBody, HttpRequest httpRequest) throws IOException {
        switch (httpRequest.getMethod()) {
            case POST:
                this.handlePostRequest(httpRequest.getUri());
                break;

            case HEAD:
            case GET:
                this.handleDirectoryServerRequest(httpRequest, httpResponseHead, httpResponseBody);
                break;

            default:
                this.responseWriter.respondeWith501(httpResponseHead, httpResponseBody);
                break;
        }
    }

    void handleDirectoryServerRequest(HttpRequest httpRequest, PrintWriter httpResponseHead, BufferedOutputStream httpResponseBody) throws IOException {
        String uri = httpRequest.getUri();

        if (uri.endsWith("/")) {
            this.handleDirectoryRequest(httpRequest, httpResponseHead, httpResponseBody);
        } else {
            this.handleFileRequest(httpRequest, httpResponseHead, httpResponseBody);
        }
    }

    void handlePostRequest(String uri) {
        log.info("HTTP Request uri='" + uri + "'");
        switch (uri) {
            case "/comments":
            case "/comments/":
                log.info("Comment creation");
                break;

            case "/comments/query":
            case "/comments/query/":
                log.info("Comment retrievement");
                break;

            default:
                break;
        }
    }

    void handleFileRequest(HttpRequest httpRequest, PrintWriter httpResponseHead, BufferedOutputStream httpResponseBody) throws IOException {
        FileRequestDto fileRequestDto = this.directoryService.handleFileRequest(httpRequest.getUri());

        if (!fileRequestDto.getFound()) {
            this.responseWriter.respondeWith404(httpResponseHead, null);
        } else {
            this.responseWriter.writeHttpResponse(fileRequestDto, httpResponseHead, null);
        }
    }

    void handleDirectoryRequest(HttpRequest httpRequest, PrintWriter httpResponseHead, BufferedOutputStream httpResponseBody) throws IOException {
        DirectoryRequestDto directoryRequestDto = this.directoryService.handleDirectoryRequest(httpRequest.getUri());
        if (!directoryRequestDto.getFound()) {
            if(httpRequest.getMethod() == HttpMethod.HEAD){
                this.responseWriter.respondeWith404(httpResponseHead, null);
            }else {
                this.responseWriter.respondeWith404(httpResponseHead, httpResponseBody);
            }

        } else {
            if(httpRequest.getMethod() == HttpMethod.HEAD){
                this.responseWriter.writeHttpResponse(directoryRequestDto, httpResponseHead, null);
            }else {
                this.responseWriter.writeHttpResponse(directoryRequestDto, httpResponseHead, httpResponseBody);
            }

        }
    }


}