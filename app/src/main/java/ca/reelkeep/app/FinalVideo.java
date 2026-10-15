package ca.reelkeep.app;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

final class FinalVideo {
    final File file;
    final String mime;
    private FinalVideo(File file, String mime) { this.file = file; this.mime = mime; }
    static FinalVideo resolve(File directory, String path) throws IOException {
        File file = new File(path).getCanonicalFile();
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (!directory.getCanonicalFile().equals(file.getParentFile()) || !file.isFile() || file.length() == 0)
            throw new IOException("No complete video in the job directory");
        if (name.matches(".*\\.f[0-9]+\\..*") || name.contains(".temp."))
            throw new IOException("Downloader returned an unfinished stream");
        String mime;
        if (name.endsWith(".mp4")) mime = "video/mp4";
        else if (name.endsWith(".webm")) mime = "video/webm";
        else if (name.endsWith(".mkv")) mime = "video/x-matroska";
        else throw new IOException("Unsupported final video format");
        return new FinalVideo(file, mime);
    }
}
