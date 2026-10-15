package ca.reelkeep.app;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.Files;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
public class GalleryPublisherTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private File source;private Store store;
    static class Store implements GalleryPublisher.Store<String>{
        boolean closed,failPublish,failCopy,nullOutput;int deletes,rows=1;final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        public String insert(){return "pending";}
        public OutputStream open(String item){
            if(nullOutput)return null;
            return new OutputStream(){
                public void write(int value)throws IOException{if(failCopy)throw new IOException("disk full");bytes.write(value);}
                public void close(){closed=true;}
            };
        }
        public int publish(String item)throws Exception{assertTrue(closed);if(failPublish)throw new IOException("provider failed");return rows;}
        public void delete(String item){deletes++;}
    }
    @Before public void prepare()throws Exception{source=temp.newFile("reel.mp4");Files.write(source.toPath(),new byte[]{1,2,3});store=new Store();}
    @Test public void closesAndPublishesCompleteCopy()throws Exception{
        assertEquals("pending",GalleryPublisher.save(source,store));assertArrayEquals(new byte[]{1,2,3},store.bytes.toByteArray());assertEquals(0,store.deletes);
    }
    @Test public void zeroRowsCleansPending(){store.rows=0;assertThrows(IOException.class,()->GalleryPublisher.save(source,store));assertEquals(1,store.deletes);}
    @Test public void publishExceptionCleansPending(){store.failPublish=true;assertThrows(IOException.class,()->GalleryPublisher.save(source,store));assertEquals(1,store.deletes);}
    @Test public void copyFailureCleansPending(){store.failCopy=true;assertThrows(IOException.class,()->GalleryPublisher.save(source,store));assertTrue(store.closed);assertEquals(1,store.deletes);}
    @Test public void nullOutputCleansPending(){store.nullOutput=true;assertThrows(IOException.class,()->GalleryPublisher.save(source,store));assertEquals(1,store.deletes);}
}
