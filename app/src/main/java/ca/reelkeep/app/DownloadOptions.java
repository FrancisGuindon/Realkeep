package ca.reelkeep.app;

final class DownloadOptions {
    // Without this, missing FFmpeg can be a warning with a zero exit status.
    static final String STRICT_ERRORS = "--abort-on-error";
    static final String YOUTUBE_FORMAT = "bv[ext=mp4][vcodec^=avc1]+ba[ext=m4a]/b[ext=mp4][vcodec^=avc1]/bv*+ba/b";
    static final String RESULT_TEMPLATE = "after_move:{\"filepath\":%(filepath)j,\"acodec\":%(acodec)j}";
    private DownloadOptions() {}
}
