package org.fdroid.fdroid.net;

import android.annotation.TargetApi;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Build;
import android.support.v4.content.LocalBroadcastManager;

import org.fdroid.fdroid.Utils;
import org.fdroid.fdroid.data.Credentials;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class DownloaderFactory {
    private static final String TAG = "DownloaderFactory";

    private static LocalBroadcastManager localBroadcastManager;

    /**
     * Downloads to a temporary file, which *you must delete yourself when
     * you are done.  It is stored in {@link Context#getCacheDir()} and starts
     * with the prefix {@code dl-}.
     */
    public static Downloader create(Context context, String urlString)
            throws IOException {
        return create(context, new URL(urlString));
    }

    /**
     * Downloads to a temporary file, which *you must delete yourself when
     * you are done.  It is stored in {@link Context#getCacheDir()} and starts
     * with the prefix {@code dl-}.
     */
    public static Downloader create(Context context, URL url)
            throws IOException {
        File destFile = File.createTempFile("dl-", "", context.getCacheDir());
        destFile.deleteOnExit(); // this probably does nothing, but maybe...
        return create(context, url, destFile);
    }

    public static Downloader create(Context context, String urlString, File destFile)
            throws IOException {
        return create(context, new URL(urlString), destFile);
    }

    public static Downloader create(Context context, URL url, File destFile)
            throws IOException {
        return create(context, url, destFile, null);
    }

    public static Downloader create(Context context, URL url, File destFile, Credentials credentials)
            throws IOException {
        Downloader downloader = null;
        if (localBroadcastManager == null) {
            localBroadcastManager = LocalBroadcastManager.getInstance(context);
        }

        if (isBluetoothAddress(url)) {
            String macAddress = url.getHost().replace("-", ":");
            downloader = new BluetoothDownloader(macAddress, url, destFile);
        } else if (isLocalFile(url)) {
            downloader = new LocalFileDownloader(url, destFile);
        } else {
            downloader = new HttpDownloader(url, destFile, credentials);
        }

        downloader.setListener(new Downloader.DownloaderProgressListener() {
            @Override
            public void sendProgress(URL sourceUrl, int bytesRead, int totalBytes) {
                Intent intent = new Intent(Downloader.LOCAL_ACTION_PROGRESS);
                intent.putExtra(Downloader.EXTRA_ADDRESS, sourceUrl.toString());
                intent.putExtra(Downloader.EXTRA_BYTES_READ, bytesRead);
                intent.putExtra(Downloader.EXTRA_TOTAL_BYTES, totalBytes);
                localBroadcastManager.sendBroadcast(intent);
            }
        });
        return downloader;
    }

    private static boolean isBluetoothAddress(URL url) {
        return "bluetooth".equalsIgnoreCase(url.getProtocol());
    }

    private static boolean isLocalFile(URL url) {
        return "file".equalsIgnoreCase(url.getProtocol());
    }

    public static AsyncDownloader createAsync(Context context, String urlString, File destFile, String title, String id, Credentials credentials, AsyncDownloader.Listener listener) throws IOException {
        return createAsync(context, new URL(urlString), destFile, title, id, credentials, listener);
    }

    public static AsyncDownloader createAsync(Context context, URL url, File destFile, String title, String id, Credentials credentials, AsyncDownloader.Listener listener)
            throws IOException {
        // To re-enable, fix the following:
        // * https://gitlab.com/fdroid/fdroidclient/issues/445
        // * https://gitlab.com/fdroid/fdroidclient/issues/459
        if (false && canUseDownloadManager(context, url)) {
            Utils.debugLog(TAG, "Using AsyncDownloaderFromAndroid");
            return new AsyncDownloaderFromAndroid(context, listener, title, id, url.toString(), destFile);
        }
        Utils.debugLog(TAG, "Using AsyncDownloadWrapper");
        return new AsyncDownloadWrapper(create(context, url, destFile, credentials), listener);
    }

    private static boolean isOnionAddress(URL url) {
        return url.getHost().endsWith(".onion");
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private static boolean hasDownloadManager(Context context) {
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) {
            // Service was not found
            return false;
        }
        DownloadManager.Query query = new DownloadManager.Query();
        Cursor c = dm.query(query);
        if (c == null) {
            // Download Manager was disabled
            return false;
        }
        c.close();
        return true;
    }

    /**
     * Tests to see if we can use Android's DownloadManager to download the APK, instead of
     * a downloader returned from DownloadFactory.
     */
    private static boolean canUseDownloadManager(Context context, URL url) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            // No HTTPS support on 2.3, no DownloadManager on 2.2. Don't have
            // 3.0 devices to test on, so require 4.0.
            return false;
        }
        if (isOnionAddress(url)) {
            // We support onion addresses through our own downloader.
            return false;
        }
        if (isBluetoothAddress(url)) {
            // Completely differnet protocol not understood by the download manager.
            return false;
        }
        return hasDownloadManager(context);
    }

}