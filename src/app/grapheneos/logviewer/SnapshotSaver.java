package app.grapheneos.logviewer;

import android.annotation.Nullable;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static app.grapheneos.logviewer.Utils.showToast;

class SnapshotSaver {
    static final int ACTIVITY_REQUEST_CODE = 1000;

    static void start(BaseActivity ctx) {
        ViewModel.Snapshot s = ViewModel.Snapshot.create(ctx.viewModel);
        var i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType(ViewModel.Snapshot.MIME_TYPE);
        i.putExtra(Intent.EXTRA_TITLE, s.fileName);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        ctx.viewModel.pendingSnapshot = s;
        ctx.startActivityForResult(i, ACTIVITY_REQUEST_CODE);
    }

    static void onActivityResult(BaseActivity ctx, int resultCode, Intent resultIntent) {
        final ViewModel.Snapshot pendingSnapshot = ctx.viewModel.pendingSnapshot;
        ctx.viewModel.pendingSnapshot = null;

        if (resultCode != Activity.RESULT_OK || resultIntent == null) {
            return;
        }

        if (pendingSnapshot == null) {
            // will happen if our process was recreated while file picker was in the foreground
            ErrorDialog.show(ctx, ctx.getText(R.string.unable_to_save_file),
                    new IllegalStateException("no pending ViewModel snapshot"));
            return;
        }

        Uri uri = resultIntent.getData();
        bgExecutor.execute(() -> writeToUri(ctx, pendingSnapshot, uri));
    }

    private static Executor bgExecutor = Executors.newCachedThreadPool();

    static void writeToUri(Context ctx, ViewModel.Snapshot s, Uri uri) {
        ContentResolver cr = ctx.getContentResolver();
        ParcelFileDescriptor pfd;
        try {
            pfd = cr.openFileDescriptor(uri, "w");
        } catch (Exception e) {
            ctx.getMainExecutor().execute(() ->
                    ErrorDialog.show(ctx, ctx.getText(R.string.toast_unable_to_open_file), e));
            return;
        }

        if (pfd == null) {
            ctx.getMainExecutor().execute(() ->
                    showToast(ctx, ctx.getText(R.string.toast_unable_to_open_file)));
            return;
        }

        try (var os = new ParcelFileDescriptor.AutoCloseOutputStream(pfd)) {
            os.write(s.textBytes);
        } catch (Exception e) {
            ctx.getMainExecutor().execute(() ->
                    ErrorDialog.show(ctx, ctx.getText(R.string.unable_to_save_file), e));
        }

        ctx.getMainExecutor().execute(() ->
                showToast(ctx, ctx.getString(R.string.toast_saved, s.fileName)));
    }
}
