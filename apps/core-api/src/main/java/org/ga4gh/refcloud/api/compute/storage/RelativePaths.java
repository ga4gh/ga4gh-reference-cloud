package org.ga4gh.refcloud.api.compute.storage;

// paths under a run's location that came from outside (a download url's path, a staged input's name): they must stay
// where they're put, on a filesystem and in S3 alike
public final class RelativePaths {

    private RelativePaths() {}

    // a relative path made only of ordinary segments: not absolute, no "." or ".." or empty segments, no backslashes
    // or NUL characters
    public static boolean isSafe(String path) {
        if (path == null || path.isEmpty() || path.startsWith("/") || path.indexOf('\0') >= 0 || path.contains("\\")) {
            return false;
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    // a download path as it arrives in a url, e.g. "/report/summary.txt", without its leading slash
    public static String stripLeadingSlash(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }
}
