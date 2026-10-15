package ca.reelkeep.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;

final class GalleryPublisher {
    interface Store<T> {
        T insert() throws Exception;
        OutputStream open(T item) throws Exception;
        int publish(T item) throws Exception;
        void delete(T item) throws Exception;
    }
    static <T> T save(File source, Store<T> store) throws Exception {
        T item = store.insert();
        if (item == null) throw new IOException("Cannot create a Gallery video");
        try {
            try (InputStream in = new FileInputStream(source); OutputStream out = store.open(item)) {
                if (out == null) throw new IOException("Cannot write the Gallery video");
                byte[] buffer = new byte[65536];
                int length;
                while ((length = in.read(buffer)) != -1) { checkInterrupted(); out.write(buffer, 0, length); }
            }
            checkInterrupted();
            if (store.publish(item) != 1) throw new IOException("Gallery did not publish the video");
            return item;
        } catch (Exception failure) {
            try { store.delete(item); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Download cancelled");
    }
}
