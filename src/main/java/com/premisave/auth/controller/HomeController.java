package com.premisave.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public String home(HttpServletRequest request) {
        String acceptHeader = request.getHeader("Accept");

        if (acceptHeader != null && acceptHeader.contains(MediaType.TEXT_HTML_VALUE)) {
            return getHtmlResponse();
        }
        return getPlainTextResponse();
    }

    @GetMapping("/health")
    public String health() {
        return """
                
                **************************************************************
                *                                                            *
                *                      P R E M I S A V E                    *
                *                                                            *
                **************************************************************
                *                                                            *
                *        +-----------+       +-----------+                   *
                *        |  [=====]  |       |  [=====]  |                   *
                *        |  | ### |  |  / \\  |  | ### |  |                   *
                *        |  |_____|  | /___\\ |  |_____|  |                   *
                *        |  |     |  ||     ||  |     |  |                   *
                *        +--+-----+--++-----++--+-----+--+                   *
                *                                                            *
                *              PROPERTY MANAGEMENT PLATFORM                  *
                *                                                            *
                **************************************************************
                *                                                            *
                *    >>  STATUS .............................  [ UP ]  <<     *
                *    >>  SERVICE ................  AUTH SERVICE         <<     *
                *    >>  HEALTH ........................  PASSING       <<     *
                *                                                            *
                **************************************************************
                """;
    }

    private String getHtmlResponse() {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>Premisave Auth Service</title>
                    <style>
                        body {
                            font-family: monospace, Arial, sans-serif;
                            text-align: center;
                            margin-top: 60px;
                            background: #f8fafc;
                            color: #1e2937;
                        }
                        h1 {
                            color: #1e40af;
                            font-size: 2.8rem;
                            margin-bottom: 10px;
                        }
                        .subtitle {
                            color: #334155;
                            font-size: 1.4rem;
                            margin-bottom: 40px;
                        }
                        .status {
                            color: #166534;
                            font-size: 1.35rem;
                            font-weight: bold;
                            letter-spacing: 2px;
                        }
                        pre {
                            display: inline-block;
                            text-align: left;
                            margin: 30px 0;
                            font-size: 0.95rem;
                            line-height: 1.1;
                        }
                    </style>
                </head>
                <body>
                    <h1>Premisave</h1>
                    <p class="subtitle">Property Management Platform</p>
                    <pre>
   _____                  _               
  |  __ \\                (_)              
  | |__) | ___ _ __ ___   ___ ___  __ _ 
  |  ___/ / _ \\ '_ ` _ \\ / _ \\ __|/ _` |
  | |    |  __/ | | | | |  __/ (__| (_| |
  |_|     \\___|_| |_| |_|\\___|\\___|\\__,_|
                    </pre>
                    <p class="status">AUTH SERVICE - RUNNING SUCCESSFULLY</p>
                    <div style="margin-top: 30px; color: #64748b;">
                        Secure Authentication &amp; User Management System
                    </div>
                </body>
                </html>
                """;
    }

    private String getPlainTextResponse() {
        return """
                
                ============================================================
                                   PREMISAVE
                ============================================================
                
                   _____                  _               
                  |  __ \\                (_)              
                  | |__) | ___ _ __ ___   ___ ___  __ _ 
                  |  ___/ / _ \\ '_ ` _ \\ / _ \\ __|/ _` |
                  | |    |  __/ | | | | |  __/ (__| (_| |
                  |_|     \\___|_| |_| |_|\\___|\\___|\\__,_|
                
                ============================================================
                Property Management Platform
                Secure Authentication & User Management System
                
                Status: RUNNING SUCCESSFULLY
                -----------------------------------------------------------
                Ready to handle authentication requests.
                ============================================================
                """;
    }
}