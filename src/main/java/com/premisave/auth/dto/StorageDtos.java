package com.premisave.auth.dto;

import java.util.List;

/** The administrators' storage endpoints (/admin/storage-settings). Paths appear here only because administrators need them. */
public final class StorageDtos {

    private StorageDtos() {
    }

    /**
     * What an administrator sends to try or save the document storage. A blank {@code minFreeMb} keeps the .env value.
     * LOCAL uses {@code localPath}; NETWORK uses the network fields.
     */
    public record Request(String type, String localPath, String networkProtocol, String networkHost, String networkShare,
                          String networkMountPath, String networkDirectory, Boolean networkRequireMount, Integer minFreeMb) {
    }

    /** The result of trying storage values without saving them. */
    public record Check(boolean ok, String problem, String location, Long freeMb) {
    }

    /** The .env values, which "Reset to .env" goes back to. */
    public record Env(String type, String localPath, String networkProtocol, String networkHost, String networkShare,
                      String networkMountPath, String networkDirectory, boolean networkRequireMount, int minFreeMb) {
    }

    /** The storage in use now. {@code source} is DASHBOARD when an administrator saved it, ENV otherwise. */
    public record Documents(String source, String type, String localPath, String networkProtocol, String networkHost,
                            String networkShare, String networkMountPath, String networkDirectory,
                            boolean networkRequireMount, int minFreeMb, String location, boolean ready, String problem,
                            Long freeMb, Env env) {
    }

    public record Response(List<String> allowedRoots, Documents documents) {
    }
}
