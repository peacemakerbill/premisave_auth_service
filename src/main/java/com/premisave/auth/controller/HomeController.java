package com.premisave.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HomeController {

    /** A browser gets the landing page; every other client gets JSON. */
    @GetMapping("/")
    public ResponseEntity<?> home(HttpServletRequest request) {
        String acceptHeader = request.getHeader("Accept");

        if (acceptHeader != null && acceptHeader.contains(MediaType.TEXT_HTML_VALUE)) {
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(getHtmlResponse());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "auth-service");
        body.put("platform", "Premisave");
        body.put("status", "RUNNING");
        body.put("message", "Ready to handle authentication requests.");
        return ResponseEntity.ok(body);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "auth-service");
        body.put("health", "PASSING");
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(body);
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
}