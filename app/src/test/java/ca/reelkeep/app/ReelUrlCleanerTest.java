package ca.reelkeep.app;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReelUrlCleanerTest {
  @Test public void stripsInstagramShareTracking() {
    assertEquals("https://www.instagram.com/reel/Dd37EkgTYsc/",
      ReelUrlCleaner.clean("https://www.instagram.com/reel/Dd37EkgTYsc/?psln=MXE1ZHF2b3JnNmF3bA=="));
    assertEquals("https://www.instagram.com/reel/DeArvkVP_Y2/",
      ReelUrlCleaner.clean("Watch this! https://www.instagram.com/reel/DeArvkVP_Y2/?cplk=123"));
  }
  @Test public void youtubeLinks() {
    assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ",
      ReelUrlCleaner.clean("https://youtu.be/dQw4w9WgXcQ?si=tracking"));
    assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ",
      ReelUrlCleaner.clean("https://www.youtube.com/watch?feature=share&v=dQw4w9WgXcQ&t=30"));
    assertEquals("https://www.youtube.com/shorts/dQw4w9WgXcQ",
      ReelUrlCleaner.clean("https://youtube.com/shorts/dQw4w9WgXcQ?si=tracking"));
    assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ",
      ReelUrlCleaner.clean("https://m.youtube.com/live/dQw4w9WgXcQ?feature=share"));
  }
  @Test public void maliciousHostsRejected() {
    assertNull(ReelUrlCleaner.clean("https://youtube.com.evil.example/watch?v=dQw4w9WgXcQ"));
    assertNull(ReelUrlCleaner.clean("https://instagram.com.evil.example/reel/Dd37EkgTYsc/"));
    assertNull(ReelUrlCleaner.clean("https://www.youtube.com/playlist?list=ABC"));
  }
}
