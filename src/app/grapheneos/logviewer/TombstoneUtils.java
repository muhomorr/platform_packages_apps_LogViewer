package app.grapheneos.logviewer;

import android.annotation.Nullable;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import static java.nio.charset.StandardCharsets.UTF_8;

public class TombstoneUtils {
    private static final String TAG = TombstoneUtils.class.getSimpleName();

    @Nullable
    public static TimestampedFile findTombstoneByHeader(byte[] header) {

        File[] tombstones = new File("/data/tombstones").listFiles();
        if (tombstones == null) {
            return null;
        }

        ArrayList<TimestampedFile> timestampedTombstones = new ArrayList<>();
        for (File tombstone : tombstones) {
            String name = tombstone.getName();
            if (name.endsWith(".pb") || !name.startsWith("tombstone_")) {
                continue;
            }
            long lastModified = tombstone.lastModified();
            if (lastModified <= 0) {
                continue;
            }
            timestampedTombstones.add(new TimestampedFile(tombstone, lastModified));
        }

        timestampedTombstones.sort(Comparator.comparing(TimestampedFile::lastModified).reversed());

        int headerLen = header.length;
        byte[] buf = new byte[headerLen];

        for (TimestampedFile tombstone : timestampedTombstones) {
            try (var s = new FileInputStream(tombstone.file())) {
                if (s.readNBytes(buf, 0, headerLen) != headerLen) {
                    continue;
                }
                if (Arrays.equals(header, buf)) {
                    if (tombstone.file().lastModified() != tombstone.lastModified()) {
                        return null;
                    }
                    return tombstone;
                }
            } catch (IOException e) {
                Log.d(TAG, "", e);
            }
        }
        return null;
    }
}
