package app.grapheneos.logviewer;

import android.annotation.Nullable;
import android.app.ApplicationErrorReport;
import android.content.Intent;
import android.ext.LogViewerApp;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.StringBuilderPrinter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ErrorReportActivity extends BaseActivity {
    private static final String TAG = ErrorReportActivity.class.getSimpleName();

    @Override
    ViewModel createViewModel() {
        Intent intent = getIntent();
        String action = intent.getAction();
        if (action == null) {
            return null;
        }

        return switch (action) {
            case LogViewerApp.ACTION_ERROR_REPORT -> createErrorReportViewModel(intent);
            case Intent.ACTION_APP_ERROR -> createAppErrorViewModel(intent);
            default -> null;
        };
    }

    @Nullable
    private ViewModel createErrorReportViewModel(Intent i) {
        Bundle extras = i.getExtras();
        if (extras == null) {
            return null;
        }

        byte[] msgBytes;
        if (extras.getBoolean(LogViewerApp.EXTRA_PREFER_TEXT_TOMBSTONE)) {
            msgBytes = getTextTombstoneBytes();
            if (msgBytes == null) {
                Utils.showToast(this, getText(R.string.toast_unable_to_show_more_info));
                return null;
            }
        } else {
            byte[] msgGz = extras.getByteArray(LogViewerApp.EXTRA_GZIPPED_MESSAGE);
            if (msgGz == null) {
                return null;
            }
            try (var s = new GZIPInputStream(new ByteArrayInputStream(msgGz))) {
                msgBytes = s.readAllBytes();
            } catch (IOException e) {
                Log.d(TAG, "", e);
                return null;
            }
        }

        String msg = new String(msgBytes, UTF_8);
        String body;
        {
            var sb = new StringBuilder(msg.length() + 200);
            String type = extras.getString(LogViewerApp.EXTRA_ERROR_TYPE, "crash");
            sb.append("type: ").append(type).append('\n');
            if (!msg.contains(Build.FINGERPRINT)) {
                sb.append("osVersion: ").append(Build.FINGERPRINT).append('\n');
            }
            if (msg.charAt(0) != '\n' && !msg.startsWith("osVersion: ")) {
                sb.append('\n');
            }
            sb.append(msg);
            body = sb.toString();
        }
        String sourcePkg = extras.getString(LogViewerApp.EXTRA_SOURCE_PACKAGE);

        String title = extras.getString(Intent.EXTRA_TITLE);
        if (title == null) {
            title = sourcePkg != null ?
                    getString(R.string.error_report_title, Utils.loadAppLabel(this, sourcePkg)) : "";
        }

        return new ViewModel(sourcePkg, title, "", body);
    }

    @Nullable
    private ViewModel createAppErrorViewModel(Intent i) {
        var aer = i.getParcelableExtra(Intent.EXTRA_BUG_REPORT, ApplicationErrorReport.class);
        if (aer == null) {
            return null;
        }
        String body;
        boolean useTextTombstone = i.getBooleanExtra(LogViewerApp.EXTRA_PREFER_TEXT_TOMBSTONE, false);
        if (useTextTombstone) {
            byte[] bytes = getTextTombstoneBytes();
            if (bytes == null) {
                Utils.showToast(this, getText(R.string.toast_unable_to_show_more_info));
                return null;
            }
            body = new String(bytes, UTF_8);
        } else {
            body = createAerBody(aer);
        }
        if (body == null) {
            Log.e(TAG, "invalid ApplicationErrorReport");
            return null;
        }
        String sourcePkg = aer.packageName;
        String title = createTitle(sourcePkg);
        String header = createAerHeader(aer,
                // text tombstone includes OS version string already
                !useTextTombstone);
        String headerExt = i.getStringExtra(Intent.EXTRA_TEXT);
        if (headerExt != null) {
            header += '\n' + headerExt;
        }
        return new ViewModel(sourcePkg, title, header, body);
    }

    private String createTitle(String sourcePkg) {
        return sourcePkg != null ? getString(R.string.error_report_title, Utils.loadAppLabel(this, sourcePkg)) : "";
    }

    private String createAerHeader(ApplicationErrorReport r, boolean includeOsVersion) {
        ArrayList<String> l = new ArrayList<>();
        l.add("type: " + aerTypeToString(r.type));
        if (includeOsVersion) {
            l.add("osVersion: " + Build.FINGERPRINT);
        }
        Utils.maybeAddFlags(this, l);
        l.add("package: " + r.packageName + ':' + r.packageVersion);
        l.add("process: " + r.processName);
        if (r.type == ApplicationErrorReport.TYPE_CRASH && r.crashInfo.processUptimeMs > 0) {
            l.add("processUptime: " + r.crashInfo.processUptimeMs
                            + " + " + r.crashInfo.processStartupLatencyMs + " ms");
        }

        String sourcePkg = r.packageName;
        if (sourcePkg != null) {
            String installer = Utils.getInstallingPackage(this, sourcePkg);
            if (installer != null) {
                l.add("installer: " + installer);
            }
        }

        return String.join("\n", l);
    }

    @Nullable
    private static String createAerBody(ApplicationErrorReport r) {
        if (r.type == ApplicationErrorReport.TYPE_CRASH) {
            String stackTrace = r.crashInfo.stackTrace;
            int nativeCrashMarkerIdx = stackTrace.indexOf("\nProcess uptime: ");
            if (nativeCrashMarkerIdx > 0) {
                // This is a native crash, filter out most of the header lines to make the report easier to read
                var sb = new StringBuilder();
                String[] prefixes = { "signal ", "Abort message: " };
                boolean backtraceStarted = false;
                for (String line : stackTrace.substring(nativeCrashMarkerIdx).split("\n")) {
                    if (backtraceStarted) {
                        sb.append(line);
                        sb.append('\n');
                    }
                    for (String prefix : prefixes) {
                        if (line.startsWith(prefix)) {
                            sb.append(line);
                            sb.append('\n');
                        }
                    }
                    if (line.startsWith("backtrace:")) {
                        sb.append('\n');
                        sb.append(line);
                        sb.append('\n');
                        backtraceStarted = true;
                    }
                }
                return sb.toString();
            } else {
                return stackTrace;
            }
        }

        var sb = new StringBuilder();
        var printer = new StringBuilderPrinter(sb);

        switch (r.type) {
            case ApplicationErrorReport.TYPE_ANR -> {
                ApplicationErrorReport.AnrInfo i = r.anrInfo;
                if (i == null) {
                    return null;
                }
                String tracesFile = i.tracesFilePath;
                if (tracesFile != null) {
                    String s = Utils.readFileAsString(tracesFile);
                    if (s != null) {
                        printer.println(s);
                    }
                }
                printer.println("\nAnrInfo dump:");
                i.dump(printer, "");
            }
            case ApplicationErrorReport.TYPE_BATTERY -> {
                ApplicationErrorReport.BatteryInfo i = r.batteryInfo;
                if (i == null) {
                    return null;
                }
                i.dump(printer, "");
            }
            case ApplicationErrorReport.TYPE_RUNNING_SERVICE -> {
                ApplicationErrorReport.RunningServiceInfo i = r.runningServiceInfo;
                if (i == null) {
                    return null;
                }
                i.dump(printer, "");
            }
            default -> {
                return null;
            }
        }
        return sb.toString();
    }

    private static String aerTypeToString(int type) {
        return switch (type) {
            case ApplicationErrorReport.TYPE_CRASH -> "crash";
            case ApplicationErrorReport.TYPE_ANR -> "ANR";
            case ApplicationErrorReport.TYPE_BATTERY -> "battery";
            case ApplicationErrorReport.TYPE_RUNNING_SERVICE -> "running_service";
            default -> "unknown (" + type + ")";
        };
    }

    @Override
    boolean shouldShowReportButton() {
        return getIntent().getBooleanExtra(LogViewerApp.EXTRA_SHOW_REPORT_BUTTON, false);
    }

    @Nullable
    private TimestampedFile getTextTombstoneFile() {
        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            return null;
        }

        var aer = extras.getParcelable(Intent.EXTRA_BUG_REPORT, ApplicationErrorReport.class);
        if (aer != null) {
            TimestampedFile res = getTextTombstoneFileFromAer(aer);
            if (res != null) {
                return res;
            }
        }

        String path = extras.getString(LogViewerApp.EXTRA_TEXT_TOMBSTONE_FILE_PATH);
        if (path == null) {
            return null;
        }
        Long expectedLastModifiedB = extras.getNumber(LogViewerApp.EXTRA_TEXT_TOMBSTONE_LAST_MODIFIED_TIME);
        if (expectedLastModifiedB == null) {
            return null;
        }
        long expectedLastModified = expectedLastModifiedB.longValue();

        File file = new File(path);
        long lastModified = file.lastModified();
        if (expectedLastModified != lastModified) {
            Log.e(TAG, "lastModified mismatch: expected " + expectedLastModified + ", got " + lastModified);
            return null;
        }
        return new TimestampedFile(file, lastModified);
    }

    @Nullable
    public static TimestampedFile getTextTombstoneFileFromAer(ApplicationErrorReport aer) {
        if (aer.type != ApplicationErrorReport.TYPE_CRASH) {
            return null;
        }

        // crashInfo.stackTrace is filled out by NativeCrashListener in system_server before
        // tombstoned writes tombstone to storage. Header of stackTrace string and header of text
        // tombstone file are the same, so extract it to search for the matching text tombstone file.

        ApplicationErrorReport.CrashInfo crashInfo = aer.crashInfo;
        if (crashInfo == null) {
            return null;
        }
        String stackTrace = crashInfo.stackTrace;
        if (stackTrace == null) {
            return null;
        }
        if (!stackTrace.startsWith("*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***")) {
            return null;
        }
        String timestampPrefix = "\nTimestamp: ";
        int timestampIdx = stackTrace.indexOf(timestampPrefix);
        if (timestampIdx < 0) {
            return null;
        }
        int timestampEnd = stackTrace.indexOf('\n', timestampIdx + timestampPrefix.length());
        if (timestampEnd < 0) {
            return null;
        }

        byte[] header = stackTrace.substring(0, timestampEnd).getBytes(UTF_8);

        return TombstoneUtils.findTombstoneByHeader(header);
    }

    @Nullable
    private byte[] getTextTombstoneBytes() {
        TimestampedFile tfile = getTextTombstoneFile();
        if (tfile == null) {
            return null;
        }

        File file = tfile.file();

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            Log.e(TAG, "", e);
            return null;
        }
        if (file.lastModified() != tfile.lastModified()) {
            // a race condition: file was modified since last check
            return null;
        }
        return bytes;
    }

    @Override
    List<BottomButton> createExtraBottomButtons() {
        var list = new ArrayList<BottomButton>(7);
        if (!getIntent().getBooleanExtra(LogViewerApp.EXTRA_PREFER_TEXT_TOMBSTONE, false)
                && getTextTombstoneFile() != null) {
            var bb = new BottomButton(getText(R.string.action_more_info), v -> {
                var i = new Intent(getIntent());
                i.putExtra(LogViewerApp.EXTRA_PREFER_TEXT_TOMBSTONE, true);
                startActivity(i);
            });
            list.add(bb);
        }
        String sourcePkg = viewModel.sourcePackage;
        if (sourcePkg != null) {
            var bb = new BottomButton(getText(R.string.action_show_log), v -> {
                var i = new Intent(this, LogcatActivity.class);
                i.putExtra(Intent.EXTRA_PACKAGE_NAME, sourcePkg);
                startActivity(i);
            });
            list.add(bb);
        }
        return list;
    }
}
