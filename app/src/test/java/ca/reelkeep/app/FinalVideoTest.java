package ca.reelkeep.app;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.Files;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
public class FinalVideoTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Test public void finalFileAcceptedIntermediateRejected()throws Exception{
        File dir=temp.newFolder(),file=new File(dir,"reel.mp4");Files.write(file.toPath(),new byte[]{1});
        assertEquals("video/mp4",FinalVideo.resolve(dir,file.getPath()).mime);
        File intermediate=new File(dir,"reel.f137.mp4");Files.write(intermediate.toPath(),new byte[]{1});
        assertThrows(IOException.class,()->FinalVideo.resolve(dir,intermediate.getPath()));
    }
    @Test public void outsideAndEmptyFilesRejected()throws Exception{
        File dir=temp.newFolder(),outside=temp.newFile("other.mp4");Files.write(outside.toPath(),new byte[]{1});
        assertThrows(IOException.class,()->FinalVideo.resolve(dir,outside.getPath()));
        File empty=new File(dir,"reel.mp4");assertTrue(empty.createNewFile());
        assertThrows(IOException.class,()->FinalVideo.resolve(dir,empty.getPath()));
    }
}
