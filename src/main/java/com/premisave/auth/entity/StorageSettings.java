package com.premisave.auth.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Where uploaded documents are kept, as an administrator set it from the dashboard. One record, with the id "storage".
 *
 * While {@code documents} is empty the .env values apply. Once saved, the dashboard value wins, and "Reset to .env"
 * empties it again.
 */
@Document(collection = "storage_settings")
public class StorageSettings {

    public static final String ID = "storage";

    @Id
    private String id = ID;

    /** Home Owner application documents. Null means the .env values apply. */
    private Documents documents;

    /**
     * Folders documents were kept in before an administrator changed the location. Files are looked up there when they are not
     * in the current folder, so changing the folder never loses a file. Most recent first.
     */
    private List<String> previousBases = new ArrayList<>();

    private String updatedBy;
    private Instant updatedAt;

    public static class Documents {
        /** LOCAL or NETWORK. */
        private String type;
        private String localPath;
        /** SMB or NFS. */
        private String networkProtocol;
        private String networkHost;
        private String networkShare;
        private String networkMountPath;
        private String networkDirectory;
        private boolean networkRequireMount = true;
        /** Uploads are refused below this many free megabytes. Null means the .env value. */
        private Integer minFreeMb;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getLocalPath() { return localPath; }
        public void setLocalPath(String localPath) { this.localPath = localPath; }
        public String getNetworkProtocol() { return networkProtocol; }
        public void setNetworkProtocol(String networkProtocol) { this.networkProtocol = networkProtocol; }
        public String getNetworkHost() { return networkHost; }
        public void setNetworkHost(String networkHost) { this.networkHost = networkHost; }
        public String getNetworkShare() { return networkShare; }
        public void setNetworkShare(String networkShare) { this.networkShare = networkShare; }
        public String getNetworkMountPath() { return networkMountPath; }
        public void setNetworkMountPath(String networkMountPath) { this.networkMountPath = networkMountPath; }
        public String getNetworkDirectory() { return networkDirectory; }
        public void setNetworkDirectory(String networkDirectory) { this.networkDirectory = networkDirectory; }
        public boolean isNetworkRequireMount() { return networkRequireMount; }
        public void setNetworkRequireMount(boolean networkRequireMount) { this.networkRequireMount = networkRequireMount; }
        public Integer getMinFreeMb() { return minFreeMb; }
        public void setMinFreeMb(Integer minFreeMb) { this.minFreeMb = minFreeMb; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Documents getDocuments() { return documents; }
    public void setDocuments(Documents documents) { this.documents = documents; }
    public List<String> getPreviousBases() { return previousBases; }
    public void setPreviousBases(List<String> previousBases) {
        this.previousBases = previousBases == null ? new ArrayList<>() : previousBases;
    }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
