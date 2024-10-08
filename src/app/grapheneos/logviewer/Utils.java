package app.grapheneos.logviewer;

import android.annotation.Nullable;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.service.oemlock.OemLockManager;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Utils {

    public static void maybeAddFlags(Context ctx, ArrayList<String> dst) {
        var l = new ArrayList<String>();

        var olm = ctx.getSystemService(OemLockManager.class);
        if (olm != null && olm.isDeviceOemUnlocked()) {
            l.add("bootloader unlocked");
        }

        ContentResolver cr = ctx.getContentResolver();
        if (Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0) {
            l.add("dev options enabled");
        }

        if (!l.isEmpty()) {
            dst.add("flags: " + String.join(", ", l));
        }
    }

    public static String printStackTraceToString(Throwable t) {
        var baos = new ByteArrayOutputStream(1000);
        t.printStackTrace(new PrintStream(baos));
        return baos.toString();
    }

    @Nullable
    public static String readFileAsString(String path) {
        byte[] bytes;
        Path p = Paths.get(path);
        try {
            bytes = Files.readAllBytes(p);
        } catch (IOException e) {
            Log.e("readFileAsString", "", e);
            return null;
        }
        return new String(bytes, UTF_8);
    }

    public static CharSequence loadAppLabel(Context ctx, String pkgName) {
        PackageManager pm = ctx.getPackageManager();

        ApplicationInfo ai;
        try {
            ai = pm.getApplicationInfo(pkgName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return pkgName;
        }

        return ai.loadLabel(pm);
    }

    @Nullable
    public static String getInstallingPackage(Context ctx, String pkgName) {
        PackageManager pm = ctx.getPackageManager();
        InstallSourceInfo isi;
        try {
            isi = pm.getInstallSourceInfo(pkgName);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
        return isi.getInstallingPackageName();
    }

    public static List<String> splitLines(String s) {
        return Arrays.asList(s.split("\n"));
    }

    public static void showToast(Context ctx, CharSequence text) {
        Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show();
    }
}
