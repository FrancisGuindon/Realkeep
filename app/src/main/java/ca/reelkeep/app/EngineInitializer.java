package ca.reelkeep.app;

/** Ready only after both initializers succeed; a failed extraction can be retried. */
final class EngineInitializer {
    interface Step { void run() throws Exception; }
    private final Step downloader, ffmpeg;
    private boolean ready;
    EngineInitializer(Step downloader, Step ffmpeg) { this.downloader = downloader; this.ffmpeg = ffmpeg; }
    synchronized void initialize() throws Exception {
        if (ready) return;
        downloader.run();
        ffmpeg.run();
        ready = true;
    }
}
