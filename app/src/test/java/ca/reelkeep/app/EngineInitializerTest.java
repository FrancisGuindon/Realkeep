package ca.reelkeep.app;
import static org.junit.Assert.*;
import java.io.IOException;
import org.junit.Test;
public class EngineInitializerTest {
    @Test public void initializesBothOnce() throws Exception {
        int[] calls={0,0}; EngineInitializer init=new EngineInitializer(()->calls[0]++,()->calls[1]++);
        init.initialize();init.initialize();assertArrayEquals(new int[]{1,1},calls);
    }
    @Test public void retriesFailedFfmpegExtraction() throws Exception {
        int[] calls={0,0};EngineInitializer init=new EngineInitializer(()->calls[0]++,()->{if(++calls[1]==1)throw new IOException("disk full");});
        assertThrows(IOException.class,init::initialize);init.initialize();init.initialize();assertArrayEquals(new int[]{2,2},calls);
    }
}
