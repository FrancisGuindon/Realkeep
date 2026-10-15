package ca.reelkeep.app;
import static org.junit.Assert.*;
import android.content.ClipData;
import android.content.Intent;
import android.os.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class MainActivityTest {
    private DownloadCoordinatorTest.Queue queue;private DownloadCoordinatorTest.Backend backend;
    @Before public void prepare(){
        queue=new DownloadCoordinatorTest.Queue();backend=new DownloadCoordinatorTest.Backend();
        ReelKeepApplication app=(ReelKeepApplication)RuntimeEnvironment.getApplication();
        app.setCoordinatorForTest(new DownloadCoordinator(backend,queue,action->new Handler(Looper.getMainLooper()).post(action)));
    }
    private Intent share(String url){return new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,url);}
    @Test public void recreationKeepsOneSharedDownloadAndBusyState(){
        ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class,share("https://youtu.be/kkIAeMqASaY?si=x")).setup();
        Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(1,queue.tasks.size());
        c.recreate();Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(1,queue.tasks.size());
        assertFalse(((Button)c.get().findViewById(R.id.download_video)).isEnabled());queue.run();Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1,backend.calls);assertTrue(((Button)c.get().findViewById(R.id.download_video)).isEnabled());c.pause().stop().destroy();
    }
    @Test public void clipDataAndSecondShareUseOneWorker(){
        Intent first=new Intent(Intent.ACTION_SEND).setType("text/plain");first.setClipData(ClipData.newPlainText("video","https://www.youtube.com/shorts/kkIAeMqASaY?si=x"));
        ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class,first).setup();
        c.newIntent(share("https://youtu.be/dQw4w9WgXcQ"));Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1,queue.tasks.size());assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ",((EditText)c.get().findViewById(R.id.video_url)).getText().toString());
        queue.run();assertEquals(1,backend.calls);c.pause().stop().destroy();
    }
}
