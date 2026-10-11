package ca.reelkeep.app;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Constructs canonical video URLs, discarding share and tracking parameters. */
public final class ReelUrlCleaner {
    private static final Pattern LINKS =
            Pattern.compile("https?://[^\\s<>\"']+", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSTAGRAM_ID =
            Pattern.compile("[A-Za-z0-9_-]{5,50}");
    private static final Pattern YOUTUBE_ID =
            Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern YOUTUBE_QUERY_ID =
            Pattern.compile("(?:^|&)v=([A-Za-z0-9_-]{11})(?:&|$)");

    private ReelUrlCleaner() {}

    public static String clean(String sharedText) {
        if (sharedText == null || sharedText.isEmpty()) return null;
        Matcher urls = LINKS.matcher(sharedText);
        while (urls.find()) {
            String canonical = cleanUrl(urls.group().replaceAll("[),.;!?]+$", ""));
            if (canonical != null) return canonical;
        }
        return null;
    }

    private static String cleanUrl(String url) {
        try {
            URI uri = new URI(url);
            if (uri.getUserInfo() != null || uri.getHost() == null) return null;
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath();
            if (path == null) return null;
            String[] parts = path.split("/");

            if (host.equals("instagram.com") || host.equals("www.instagram.com")
                    || host.equals("m.instagram.com")) {
                if (parts.length == 3 && (parts[1].equalsIgnoreCase("reel")
                        || parts[1].equalsIgnoreCase("reels"))
                        && INSTAGRAM_ID.matcher(parts[2]).matches()) {
                    return "https://www.instagram.com/reel/" + parts[2] + "/";
                }
                return null;
            }

            if (host.equals("youtu.be") || host.equals("www.youtu.be")) {
                if (parts.length == 2 && YOUTUBE_ID.matcher(parts[1]).matches()) {
                    return "https://www.youtube.com/watch?v=" + parts[1];
                }
                return null;
            }

            if (host.equals("youtube.com") || host.equals("www.youtube.com")
                    || host.equals("m.youtube.com") || host.equals("music.youtube.com")) {
                if (parts.length == 2 && parts[1].equalsIgnoreCase("watch")) {
                    String q = uri.getRawQuery();
                    if (q == null) return null;
                    Matcher id = YOUTUBE_QUERY_ID.matcher(q);
                    if (id.find()) return "https://www.youtube.com/watch?v=" + id.group(1);
                }
                if (parts.length == 3 && YOUTUBE_ID.matcher(parts[2]).matches()) {
                    if (parts[1].equalsIgnoreCase("shorts")) {
                        return "https://www.youtube.com/shorts/" + parts[2];
                    }
                    if (parts[1].equalsIgnoreCase("live")) {
                        return "https://www.youtube.com/watch?v=" + parts[2];
                    }
                }
            }
        } catch (Exception ignored) { }
        return null;
    }
}
