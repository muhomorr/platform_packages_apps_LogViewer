package app.grapheneos.logviewer;

import java.io.File;

public record TimestampedFile(File file, long lastModified) {}
