package ca.reelkeep.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class ReelUrlCleanerTest {
  @Test public void removesParameters(){
    assertEquals("https://www.instagram.com/reel/Dd37EkgTYsc/",ReelUrlCleaner.clean("https://www.instagram.com/reel/Dd37EkgTYsc/?psln=MXE1ZHF2b3JnNmF3bA=="));
    assertEquals("https://www.instagram.com/reel/Dd37EkgTYsc/",ReelUrlCleaner.clean("https://www.instagram.com/reel/Dd37EkgTYsc/?cplk=secret"));
  }
  @Test public void rejectsOtherDomains(){assertNull(ReelUrlCleaner.clean("https://instagram.com.evil.test/reel/Dd37EkgTYsc/"));}
}
