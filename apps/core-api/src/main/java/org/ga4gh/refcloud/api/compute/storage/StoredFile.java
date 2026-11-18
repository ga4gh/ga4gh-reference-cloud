package org.ga4gh.refcloud.api.compute.storage;

// a file in a run's storage: its path relative to the directory it was listed from (e.g. outputs/), and its size
public record StoredFile(String relativePath, long size) {}
