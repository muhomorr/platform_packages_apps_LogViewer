package app.grapheneos.logviewer;

import android.annotation.Nullable;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.UserManager;
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
import java.util.Locale;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Utils {

    public static void maybeAddHeaderLines(Context ctx, ArrayList<String> dst) {
        int userId = ctx.getUserId();
        if (userId != UserHandle.USER_SYSTEM) {
            var userManager = ctx.getSystemService(UserManager.class);
            String userType = userManager.getUserInfo(userId).userType;
            String prefix = "android.os.usertype.";
            if (userType.startsWith(prefix)) {
                userType = userType.substring(prefix.length());
            }
            dst.add("userType: " + userType.toLowerCase(Locale.US));
        }

        var flags = new ArrayList<String>();

        var olm = ctx.getSystemService(OemLockManager.class);
        if (olm != null && olm.isDeviceOemUnlocked()) {
            flags.add("bootloader unlocked");
        }

        ContentResolver cr = ctx.getContentResolver();
        if (Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0) {
            flags.add("dev options enabled");
        }

        if (!flags.isEmpty()) {
            dst.add("flags: " + String.join(", ", flags));
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
