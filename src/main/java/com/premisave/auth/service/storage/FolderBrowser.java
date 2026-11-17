package com.premisave.auth.service.storage;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Lists and creates folders for the administrators' folder picker. It only ever shows folders inside the allowed roots: a
 * path outside them is refused, hidden folders are left out, and a link that leads out of the roots is not shown. No Spring
 * here, so the rules are easy to test. Problems are thrown as {@link IllegalArgumentException} with a plain message.
 */
public final class FolderBrowser {

    private static final int MAX_FOLDERS = 300;

    /** The name of a folder made from the picker: one folder, starting with a letter or digit. */
    private static final Pattern NEW_NAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._ -]{0,63}$");

    private FolderBrowser() { }

    public record Entry(String name, String path) { }

    /**
     * @param path      the folder shown, or "" for the list of allowed folders themselves
     * @param parent    the folder above, "" when the parent is the list of allowed folders, null at that list
     * @param top       true for the list of allowed folders
     * @param exists    false when the folder is allowed but has not been created yet
     * @param canCreate whether "New folder" makes sense here
     * @param writable  whether the service can write into this folder
     * @param freeMb    free space of the disk this folder is on, when known
     * @param truncated true when there are more folders than are listed
     */
    public record Listing(String path, String parent, boolean top, boolean exists, boolean canCreate, boolean writable,
                          Long freeMb, boolean truncated, List<Entry> folders) { }

    /** The folders inside {@code requested}, or the allowed folders themselves when it is blank. */
    public static Listing list(String requested, List<Path> roots) {
        List<Path> allowed = clean(roots);
        if (requested == null || requested.isBlank()) {
            List<Entry> entries = new ArrayList<>();
            for (Path root : allowed) entries.add(new Entry(root.toString(), root.toString()));
            return new Listing("", null, true, true, false, false, null, false, entries);
        }
        Path path = absolute(requested);
        requireInside(path, allowed);

        boolean exists = Files.isDirectory(path);
        String parent = allowed.contains(path) ? "" : (path.getParent() == null ? "" : path.getParent().toString());
        if (!exists) {
            return new Listing(path.toString(), parent, false, false, true, false, null, false, List.of());
        }

        List<Entry> entries = new ArrayList<>();
        boolean truncated = false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path child : stream) {
                String name = child.getFileName().toString();
                if (name.startsWith(".") || !Files.isDirectory(child)) continue;
                if (!insideQuietly(child, allowed)) continue;
                if (entries.size() >= MAX_FOLDERS) {
                    truncated = true;
                    break;
                }
                entries.add(new Entry(name, child.toAbsolutePath().normalize().toString()));
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("The folder cannot be read: " + e.getMessage());
        }
        entries.sort(Comparator.comparing(e -> e.name().toLowerCase()));

        Long free = null;
        try {
            free = Files.getFileStore(path).getUsableSpace() / (1024 * 1024);
        } catch (IOException | RuntimeException ignored) {
            // the free space is only a hint
        }
        return new Listing(path.toString(), parent, false, true, true, Files.isWritable(path), free, truncated, entries);
    }

    /** Makes {@code name} inside {@code parent} and returns the new folder's listing, ready to be opened. */
    public static Listing create(String parent, String name, List<Path> roots) {
        List<Path> allowed = clean(roots);
        Path inside = absolute(parent);
        requireInside(inside, allowed);

        String clean = name == null ? "" : name.trim();
        if (!NEW_NAME.matcher(clean).matches() || clean.equals(".") || clean.equals("..")) {
            throw new IllegalArgumentException("A folder name can use letters, digits, dot, dash, underscore and space, "
                    + "and must start with a letter or digit");
        }
        Path target = inside.resolve(clean).normalize();
        requireInside(target, allowed);
        if (Files.exists(target)) {
            throw new IllegalArgumentException("There is already something called " + clean + " here");
        }
        try {
            Files.createDirectories(target);
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("The service cannot create the folder: " + e.getMessage());
        }
        return list(target.toString(), allowed);
    }

    /** A full path, cleaned up. Throws a plain message when it is empty or not a full path. */
    private static Path absolute(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The folder is empty");
        }
        String text = value.trim();
        if (text.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("The folder has characters that are not allowed");
        }
        Path p;
        try {
            p = Paths.get(text);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("The folder is not a valid path");
        }
        if (!p.isAbsolute()) {
            throw new IllegalArgumentException("The folder must be a full path, starting with /");
        }
        return p.normalize();
    }

    private static List<Path> clean(List<Path> roots) {
        List<Path> out = new ArrayList<>();
        for (Path root : roots) {
            Path p = root.toAbsolutePath().normalize();
            if (!out.contains(p)) out.add(p);
        }
        return out;
    }

    private static void requireInside(Path path, List<Path> roots) {
        boolean inside;
        try {
            inside = StorageLayout.isInside(path, roots) && StorageLayout.isReallyInside(path, roots);
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("The folder cannot be checked: " + e.getMessage());
        }
        if (!inside) {
            throw new IllegalArgumentException("That folder is not inside an allowed folder");
        }
    }

    private static boolean insideQuietly(Path path, List<Path> roots) {
        try {
            return StorageLayout.isReallyInside(path, roots);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
