package ca.reelkeep.app;

import java.util.concurrent.Executor;

/** One process-owned job. Detaching an observer releases its Activity. */
final class DownloadCoordinator {
    interface Progress { void report(int percent, String message); }
    interface Backend {
        String download(String url, Progress progress) throws Exception;
        String update(Progress progress) throws Exception;
        void cancel();
    }
    interface Listener { void changed(State state); }
    interface Dispatcher { void post(Runnable action); }
    static final class State {
        final boolean busy, cancellable;
        final int percent;
        final String message;
        State(boolean busy, boolean cancellable, int percent, String message) {
            this.busy = busy; this.cancellable = cancellable; this.percent = percent; this.message = message;
        }
    }
    private static final class Job {
        volatile boolean cancelled;
        volatile Thread worker;
        final boolean update;
        Job(boolean update) { this.update = update; }
    }
    private final Backend backend;
    private final Executor executor;
    private final Dispatcher dispatcher;
    private State state = new State(false, false, 0, "Ready");
    private Listener listener;
    private Job current;
    DownloadCoordinator(Backend backend, Executor executor, Dispatcher dispatcher) {
        this.backend = backend; this.executor = executor; this.dispatcher = dispatcher;
    }
    synchronized State state() { return state; }
    synchronized void attach(Listener observer) { listener = observer; emit(); }
    synchronized void detach(Listener observer) { if (listener == observer) listener = null; }
    synchronized void notice(String message) {
        if (current == null) { state = new State(false, false, 0, message); emit(); }
    }
    boolean download(String url) { return start(url, false); }
    boolean update() { return start(null, true); }
    private synchronized boolean start(String url, boolean update) {
        if (current != null) return false;
        Job job = new Job(update);
        current = job;
        state = new State(true, !update, 0, "Initializing downloader…");
        emit();
        try { executor.execute(() -> run(job, url)); }
        catch (RuntimeException error) {
            current = null;
            state = new State(false, false, 0, "Cannot start: " + readableError(error));
            emit();
        }
        return true;
    }
    private void run(Job job, String url) {
        job.worker = Thread.currentThread();
        String result;
        boolean success = false;
        try {
            if (job.cancelled) throw new InterruptedException();
            Progress progress = (percent, message) -> {
                synchronized (this) {
                    if (current != job || job.cancelled) return;
                    state = new State(true, !job.update, Math.max(0, Math.min(99, percent)), message);
                    emit();
                }
            };
            result = job.update ? backend.update(progress) : backend.download(url, progress);
            success = true;
        } catch (Exception error) {
            result = job.cancelled ? "Download cancelled" :
                (job.update ? "Downloader update failed:\n" : "Download failed:\n") + readableError(error);
        } finally {
            job.worker = null;
            Thread.interrupted();
        }
        synchronized (this) {
            if (current != job) return;
            current = null;
            state = new State(false, false, success ? 100 : 0, result);
            emit();
        }
    }
    synchronized void cancel() {
        if (current == null || current.update || current.cancelled) return;
        current.cancelled = true;
        state = new State(true, false, state.percent, "Cancelling…");
        if (current.worker != null) current.worker.interrupt();
        backend.cancel();
        emit();
    }
    private void emit() {
        Listener target = listener;
        if (target == null) return;
        dispatcher.post(() -> {
            synchronized (DownloadCoordinator.this) {
                if (listener == target) target.changed(state);
            }
        });
    }
    static String readableError(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) message = error.toString();
        return message.length() > 2400 ? "…\n" + message.substring(message.length() - 2400) : message.trim();
    }
}
