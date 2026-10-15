package ca.reelkeep.app;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ReelKeepApplication extends Application {
    private DownloadCoordinator coordinator;
    private ExecutorService worker;
    synchronized DownloadCoordinator coordinator() {
        if (coordinator == null) {
            worker = Executors.newSingleThreadExecutor(action -> {
                Thread thread = new Thread(action, "ReelKeep-download");
                thread.setDaemon(true);
                return thread;
            });
            Handler main = new Handler(Looper.getMainLooper());
            coordinator = new DownloadCoordinator(new VideoDownloader(this), worker, main::post);
        }
        return coordinator;
    }
    synchronized void setCoordinatorForTest(DownloadCoordinator replacement) { coordinator = replacement; }
    @Override public void onTerminate() {
        if (worker != null) worker.shutdownNow();
        super.onTerminate();
    }
}
