package com.premisave.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything under app.storage.* in application.yml: where uploaded documents are kept.
 *
 * Two kinds of location, both plain directories as far as the application is concerned:
 *   LOCAL    a directory on this server's disk (app.storage.local.path)
 *   NETWORK  a network share, for example a Synology NAS, that the operating system has mounted
 *            at app.storage.network.mount-path (SMB/CIFS or NFS)
 *
 * The service never receives a NAS username or password. Signing in to the NAS is the job of the
 * mount, whose credentials live in a root-only file on the server (see the storage guide), so
 * no secret of the NAS appears in this application's environment, logs or configuration.
 *
 * Every property has a safe default; a missing variable can never stop the service from starting
 * unless app.storage.fail-on-unavailable is switched on.
 */
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    public enum Type { LOCAL, NETWORK }

    public enum Protocol { SMB, NFS }

    /** Where files live: LOCAL or NETWORK. */
    private Type type = Type.LOCAL;

    /** Create the storage directory (and the application folders) when missing. */
    private boolean createDirectories = true;

    /** When true, an unusable storage location stops the service at startup. When false it only logs a warning. */
    private boolean failOnUnavailable = false;

    /** Uploads are refused when less than this many megabytes are free. 0 turns the check off. */
    private int minFreeMb = 100;

    private Local local = new Local();
    private Network network = new Network();

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type == null ? Type.LOCAL : type; }
    public boolean isCreateDirectories() { return createDirectories; }
    public void setCreateDirectories(boolean createDirectories) { this.createDirectories = createDirectories; }
    public boolean isFailOnUnavailable() { return failOnUnavailable; }
    public void setFailOnUnavailable(boolean failOnUnavailable) { this.failOnUnavailable = failOnUnavailable; }
    public int getMinFreeMb() { return minFreeMb; }
    public void setMinFreeMb(int minFreeMb) { this.minFreeMb = Math.max(0, minFreeMb); }
    public Local getLocal() { return local; }
    public void setLocal(Local local) { this.local = local; }
    public Network getNetwork() { return network; }
    public void setNetwork(Network network) { this.network = network; }

    /** Used when no path is configured. Inside the home directory of the user running the service. */
    static final String DEFAULT_PATH = "~/premisave-file-storage/homeowner-onboarding";

    public static class Local {

        /**
         * Directory for LOCAL storage. A leading ~ means the home directory of the user running the
         * service. The default in application.yml lives under that home directory; set
         * STORAGE_LOCAL_PATH in .env for a specific location.
         */
        private String path = DEFAULT_PATH;

        public String getPath() { return path; }

        /** A blank value (for example STORAGE_LOCAL_PATH= left empty in .env) keeps the default instead of failing. */
        public void setPath(String path) { this.path = path == null || path.isBlank() ? DEFAULT_PATH : path; }
    }

    static final String DEFAULT_MOUNT_PATH = "/mnt/premisave-nas";

    public static class Network {

        /** How the NAS is mounted. Used to recognise the right mount. */
        private Protocol protocol = Protocol.SMB;

        /** IP address or host name of the NAS, for example a Synology on the LAN. Used to verify the mount, never to connect. */
        private String host = "";

        /** SMB: the share name (premisave). NFS: the exported path (/volume1/premisave). */
        private String share = "";

        /** Where the operating system mounted the share. Must be an absolute path. */
        private String mountPath = DEFAULT_MOUNT_PATH;

        /** Folder inside the share that holds the documents. */
        private String directory = "homeowner-onboarding";

        /**
         * Refuse to use the location unless the share is really mounted there. Without this check, an
         * unmounted share leaves an empty folder on the local disk and uploads silently land there.
         */
        private boolean requireMount = true;

        public Protocol getProtocol() { return protocol; }
        public void setProtocol(Protocol protocol) { this.protocol = protocol == null ? Protocol.SMB : protocol; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public String getShare() { return share; }
        public void setShare(String share) { this.share = share; }
        public String getMountPath() { return mountPath; }
        public void setMountPath(String mountPath) {
            this.mountPath = mountPath == null || mountPath.isBlank() ? DEFAULT_MOUNT_PATH : mountPath;
        }
        public String getDirectory() { return directory; }
        public void setDirectory(String directory) { this.directory = directory; }
        public boolean isRequireMount() { return requireMount; }
        public void setRequireMount(boolean requireMount) { this.requireMount = requireMount; }
    }
}