package com.premisave.auth.service.application;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.auth.oauth2.UserCredentials;
import com.premisave.auth.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Stores applicant documents in Google Drive.
 *
 * Two ways to authenticate, chosen by which settings are present:
 *
 *  1. OAuth refresh token of a Google account (works with a normal Gmail account).
 *     Set google-drive.client-id, client-secret and refresh-token. The token only
 *     needs the "drive.file" scope, so the service can touch nothing but the folders
 *     and files it created itself. A root folder is created on first use.
 *
 *  2. A service account key file (for Google Workspace and Shared Drives).
 *     Set google-drive.service-account-file and google-drive.folder-id, where the
 *     folder is one the service account has been given access to.
 *
 * Files are never shared publicly. Applicants and staff download them through this
 * service, which checks who is asking.
 *
 * With nothing configured the service still starts; document uploads then fail
 * with a clear message.
 */
@Slf4j
@Service
public class DriveStorageService {

    private static final String FOLDER_MIME = "application/vnd.google-apps.folder";
    private static final String APPLICATION_NAME = "Premisave Auth Service";

    public record StoredFolder(String id, String webViewLink) {
    }

    public record StoredFile(String id, String name, String mimeType, long sizeBytes, String webViewLink) {
    }

    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private final String serviceAccountFile;
    private final String configuredFolderId;
    private final String rootFolderName;

    private volatile Drive drive;
    private volatile String rootFolderId;

    public DriveStorageService(
            @Value("${google-drive.client-id:}") String clientId,
            @Value("${google-drive.client-secret:}") String clientSecret,
            @Value("${google-drive.refresh-token:}") String refreshToken,
            @Value("${google-drive.service-account-file:}") String serviceAccountFile,
            @Value("${google-drive.folder-id:}") String configuredFolderId,
            @Value("${google-drive.root-folder-name:Premisave Home Owner Applications}") String rootFolderName) {
        this.clientId = clientId.trim();
        this.clientSecret = clientSecret.trim();
        this.refreshToken = refreshToken.trim();
        this.serviceAccountFile = serviceAccountFile.trim();
        this.configuredFolderId = configuredFolderId.trim();
        this.rootFolderName = rootFolderName.trim().isEmpty() ? "Premisave Home Owner Applications" : rootFolderName.trim();
    }

    public boolean isConfigured() {
        boolean oauth = !clientId.isEmpty() && !clientSecret.isEmpty() && !refreshToken.isEmpty();
        boolean serviceAccount = !serviceAccountFile.isEmpty();
        return oauth || serviceAccount;
    }

    // ------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------

    /** Creates a folder for one application inside the root folder. */
    public StoredFolder createApplicationFolder(String folderName) {
        return call("create the application folder", () -> {
            File metadata = new File()
                    .setName(sanitizeName(folderName))
                    .setMimeType(FOLDER_MIME)
                    .setParents(List.of(rootFolderId()));
            File created = client().files().create(metadata)
                    .setFields("id,webViewLink")
                    .setSupportsAllDrives(true)
                    .execute();
            return new StoredFolder(created.getId(), created.getWebViewLink());
        });
    }

    public StoredFile upload(String folderId, String fileName, String mimeType, byte[] content) {
        return call("upload the document", () -> {
            File metadata = new File()
                    .setName(sanitizeName(fileName))
                    .setParents(List.of(folderId));
            File created = client().files()
                    .create(metadata, new ByteArrayContent(mimeType, content))
                    .setFields("id,name,mimeType,size,webViewLink")
                    .setSupportsAllDrives(true)
                    .execute();
            long size = created.getSize() == null ? content.length : created.getSize();
            return new StoredFile(created.getId(), created.getName(), created.getMimeType(), size,
                    created.getWebViewLink());
        });
    }

    public byte[] download(String fileId) {
        return call("download the document", () -> {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            client().files().get(fileId).setSupportsAllDrives(true).executeMediaAndDownloadTo(out);
            return out.toByteArray();
        });
    }

    /** Deletes a file or folder. A file that is already gone is not an error. */
    public void delete(String fileId) {
        if (fileId == null || fileId.isBlank() || !isConfigured()) {
            return;
        }
        try {
            client().files().delete(fileId).setSupportsAllDrives(true).execute();
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() != 404) {
                log.warn("Could not delete Drive item {}: {}", fileId, e.getMessage());
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Could not delete Drive item {}: {}", fileId, e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private interface DriveCall<T> {
        T run() throws IOException;
    }

    private <T> T call(String action, DriveCall<T> operation) {
        requireConfigured();
        try {
            return operation.run();
        } catch (GoogleJsonResponseException e) {
            log.error("Google Drive could not {}: HTTP {} {}", action, e.getStatusCode(),
                    e.getDetails() == null ? e.getMessage() : e.getDetails().getMessage());
            if (e.getStatusCode() == 401 || e.getStatusCode() == 403) {
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "Document storage rejected our credentials. Please contact support.");
            }
            if (e.getStatusCode() == 404) {
                throw ApiException.notFound("The file could not be found in document storage.");
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Document storage could not " + action + ". Please try again shortly.");
        } catch (IOException e) {
            log.error("Google Drive could not {}: {}", action, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Document storage is unreachable right now. Please try again shortly.");
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Document storage (Google Drive) is not configured on this server.");
        }
    }

    private Drive client() throws IOException {
        Drive current = drive;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (drive == null) {
                drive = buildClient();
            }
            return drive;
        }
    }

    private Drive buildClient() throws IOException {
        GoogleCredentials credentials;
        if (!clientId.isEmpty() && !clientSecret.isEmpty() && !refreshToken.isEmpty()) {
            credentials = UserCredentials.newBuilder()
                    .setClientId(clientId)
                    .setClientSecret(clientSecret)
                    .setRefreshToken(refreshToken)
                    .build();
        } else {
            try (InputStream in = Files.newInputStream(Path.of(serviceAccountFile))) {
                credentials = ServiceAccountCredentials.fromStream(in)
                        .createScoped(List.of(DriveScopes.DRIVE));
            }
        }

        HttpCredentialsAdapter adapter = new HttpCredentialsAdapter(credentials);
        HttpRequestInitializer initializer = request -> {
            adapter.initialize(request);
            request.setConnectTimeout(10_000);
            request.setReadTimeout(60_000);
        };

        return new Drive.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), initializer)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    /** The folder every application folder lives in. Created once, then remembered. */
    private String rootFolderId() throws IOException {
        if (!configuredFolderId.isEmpty()) {
            return configuredFolderId;
        }
        String cached = rootFolderId;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (rootFolderId == null) {
                rootFolderId = findOrCreateRootFolder();
            }
            return rootFolderId;
        }
    }

    private String findOrCreateRootFolder() throws IOException {
        String query = "mimeType='" + FOLDER_MIME + "' and name='" + escapeQuery(rootFolderName)
                + "' and trashed=false";
        FileList found = client().files().list()
                .setQ(query)
                .setFields("files(id,name)")
                .setPageSize(1)
                .execute();
        if (found.getFiles() != null && !found.getFiles().isEmpty()) {
            return found.getFiles().get(0).getId();
        }
        File created = client().files()
                .create(new File().setName(rootFolderName).setMimeType(FOLDER_MIME))
                .setFields("id")
                .execute();
        log.info("Created Google Drive root folder '{}'", rootFolderName);
        return created.getId();
    }

    private static String escapeQuery(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static String sanitizeName(String name) {
        String cleaned = name == null ? "file" : name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        return cleaned.isEmpty() ? "file" : cleaned;
    }
}