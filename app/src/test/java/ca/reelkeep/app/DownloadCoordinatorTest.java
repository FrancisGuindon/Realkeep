package ca.reelkeep.app;
import static org.junit.Assert.*;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import org.junit.Test;
public class DownloadCoordinatorTest {
    static class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>();
        public void execute(Runnable action){tasks.add(action);} void run(){tasks.remove().run();}
    }
    static class Backend implements DownloadCoordinator.Backend {
        int calls;boolean fail;
        public String download(String url,DownloadCoordinator.Progress progress)throws Exception{
            calls++;if(fail)throw new IOException("no network");progress.report(100,"Saving");return "Saved";
        }
        public String update(DownloadCoordinator.Progress progress){return "Updated";}public void cancel(){}
    }
    @Test public void reattachesToOneJob(){
        Queue queue=new Queue();Backend backend=new Backend();DownloadCoordinator c=new DownloadCoordinator(backend,queue,Runnable::run);
        DownloadCoordinator.Listener first=state->{};c.attach(first);assertTrue(c.download("first"));c.detach(first);
        DownloadCoordinator.State[] observed={null};c.attach(state->observed[0]=state);
        assertTrue(observed[0].busy);assertFalse(c.download("second"));queue.run();
        assertEquals(1,backend.calls);assertFalse(observed[0].busy);assertEquals("Saved",observed[0].message);assertEquals(100,observed[0].percent);
    }
    @Test public void errorRestoresControlsAndAllowsRetry(){
        Queue q=new Queue();Backend b=new Backend();b.fail=true;DownloadCoordinator c=new DownloadCoordinator(b,q,Runnable::run);
        c.download("first");q.run();assertFalse(c.state().busy);assertTrue(c.state().message.contains("no network"));
        b.fail=false;assertTrue(c.download("retry"));q.run();assertEquals(2,b.calls);
    }
    @Test public void cancelledQueuedJobDoesNotStartNativeDownload(){
        Queue q=new Queue();Backend b=new Backend();DownloadCoordinator c=new DownloadCoordinator(b,q,Runnable::run);
        c.download("first");c.cancel();assertFalse(c.download("wait"));q.run();
        assertEquals(0,b.calls);assertFalse(c.state().busy);assertEquals("Download cancelled",c.state().message);assertFalse(Thread.currentThread().isInterrupted());
    }
}
