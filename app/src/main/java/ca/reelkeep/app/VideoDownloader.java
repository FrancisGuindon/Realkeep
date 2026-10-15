package ca.reelkeep.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.AtomicFile;
import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Application context only; no Activity is retained by the worker. */
final class VideoDownloader implements DownloadCoordinator.Backend {
    private final Context context;
    private final EngineInitializer initializer;
    private volatile String processId;
    VideoDownloader(Context context) {
        this.context = context.getApplicationContext();
        initializer = new EngineInitializer(() -> {
            YoutubeDL.getInstance().init(this.context); installBaseline();
        }, () -> { FFmpeg.getInstance().init(this.context); probeFfmpeg(); });
    }
    @Override public String download(String url, DownloadCoordinator.Progress progress) throws Exception {
        File directory = new File(context.getCacheDir(), "reel-" + UUID.randomUUID());
        try {
            initializer.initialize(); checkInterrupted();
            if (!directory.mkdirs()) throw new IOException("Cannot create a temporary download directory");
            File manifest = new File(directory, "finished.jsonl");
            YoutubeDLRequest request = new YoutubeDLRequest(url);
            request.addOption("--ignore-config"); request.addOption(DownloadOptions.STRICT_ERRORS);
            request.addOption("--no-playlist"); request.addOption("--no-mtime");
            request.addOption("--socket-timeout", 20); request.addOption("--retries", 3); request.addOption("--fragment-retries", 3);
            request.addOption("--match-filter", "!is_live & !is_upcoming");
            if (url.contains("youtube.com/") || url.contains("youtu.be/")) {
                request.addOption("-f", DownloadOptions.YOUTUBE_FORMAT); request.addOption("--merge-output-format", "mp4");
            } else request.addOption("-f", "best[ext=mp4]/best");
            request.addOption("-o", new File(directory, "reel.%(ext)s").getAbsolutePath());
            request.addCommands(Arrays.asList("--print-to-file", DownloadOptions.RESULT_TEMPLATE, manifest.getAbsolutePath()));
            processId = "ReelKeep-" + UUID.randomUUID(); checkInterrupted(); progress.report(0, "Downloading…");
            YoutubeDL.getInstance().execute(request, processId, (percent, eta, line) -> {
                progress.report(percent.intValue(), "Downloading… " + percent.intValue() + "%"); return kotlin.Unit.INSTANCE;
            });
            checkInterrupted();
            if (!manifest.isFile()) throw new IOException("No completed video was reported. Live and upcoming streams are not supported.");
            List<String> records = Files.readAllLines(manifest.toPath(), StandardCharsets.UTF_8);
            if (records.size() != 1) throw new IOException("Expected exactly one completed video");
            JSONObject result = new JSONObject(records.get(0));
            FinalVideo video = FinalVideo.resolve(directory, result.getString("filepath"));
            validateTracks(video.file, !"none".equals(result.optString("acodec", "unknown")));
            checkInterrupted(); progress.report(99, "Saving to Gallery…");
            GalleryPublisher.save(video.file, new MediaStoreWriter(context.getContentResolver(), video));
            return "Saved to Gallery → Albums → ReelKeep";
        } finally { processId = null; deleteTree(directory); }
    }
    @Override public String update(DownloadCoordinator.Progress progress) throws Exception {
        initializer.initialize(); progress.report(0, "Checking for a newer yt-dlp version…");
        YoutubeDL.UpdateStatus result = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE);
        return "Downloader " + engineVersion() + ": " + result + ". Ready to download.";
    }
    @Override public void cancel() {
        String id = processId;
        if (id != null) YoutubeDL.getInstance().destroyProcessById(id);
    }
    private String engineVersion() throws Exception {
        YoutubeDLRequest request = new YoutubeDLRequest(Collections.emptyList()); request.addOption("--version");
        return YoutubeDL.getInstance().execute(request, null, null).getOut().trim();
    }
    private void installBaseline() throws Exception {
        SharedPreferences prefs = context.getSharedPreferences("engine-baseline", Context.MODE_PRIVATE);
        if (BuildConfig.DOWNLOADER_VERSION.equals(prefs.getString("installed", ""))) return;
        String installed;
        try { installed = engineVersion(); }
        catch (InterruptedException cancelled) { throw cancelled; }
        catch (Exception brokenEngine) { installed = ""; }
        if (!installed.matches("[0-9]{4}\\.[0-9]{2}\\.[0-9]{2}.*") || installed.compareTo(BuildConfig.DOWNLOADER_VERSION) < 0) {
            AtomicFile atomic = new AtomicFile(new File(context.getNoBackupFilesDir(), "youtubedl-android/yt-dlp/yt-dlp"));
            FileOutputStream output = null;
            try (InputStream input = context.getResources().openRawResource(R.raw.ytdlp)) {
                output = atomic.startWrite(); byte[] buffer = new byte[65536]; int length;
                while ((length = input.read(buffer)) != -1) { checkInterrupted(); output.write(buffer, 0, length); }
                atomic.finishWrite(output);
            } catch (Exception error) { if (output != null) atomic.failWrite(output); throw error; }
        }
        prefs.edit().putString("installed", BuildConfig.DOWNLOADER_VERSION).apply();
    }
    private void probeFfmpeg() throws Exception {
        File nativeDir = new File(context.getApplicationInfo().nativeLibraryDir);
        File packages = new File(context.getNoBackupFilesDir(), "youtubedl-android/packages");
        ProcessBuilder builder = new ProcessBuilder(new File(nativeDir, "libffmpeg.so").getAbsolutePath(), "-version");
        builder.redirectErrorStream(true);
        builder.environment().put("LD_LIBRARY_PATH", new File(packages, "ffmpeg/usr/lib").getAbsolutePath() + ":" + new File(packages, "python/usr/lib").getAbsolutePath());
        Process process = builder.start();
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) throw new IOException("FFmpeg did not start within 10 seconds");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[1024]; int length;
                while ((length = input.read(buffer)) != -1 && output.size() < 8192) output.write(buffer, 0, length);
            }
            if (process.exitValue() != 0) throw new IOException("FFmpeg could not start:\n" + output.toString("UTF-8"));
        } finally { process.destroy(); }
    }
    private static void validateTracks(File file, boolean expectsAudio) throws IOException {
        MediaExtractor extractor = new MediaExtractor(); boolean video = false, audio = false;
        try {
            extractor.setDataSource(file.getAbsolutePath());
            for (int index = 0; index < extractor.getTrackCount(); index++) {
                String mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME);
                if (mime != null) { video |= mime.startsWith("video/"); audio |= mime.startsWith("audio/"); }
            }
        } finally { extractor.release(); }
        if (!video || (expectsAudio && !audio)) throw new IOException("The finished file is missing its expected video or audio track");
    }
    private static void checkInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Download cancelled");
    }
    private static void deleteTree(File file) {
        File[] children = file.listFiles(); if (children != null) for (File child : children) deleteTree(child);
        if (file.exists() && !file.delete()) android.util.Log.w("ReelKeep", "Cannot delete temporary file " + file.getName());
    }
}
