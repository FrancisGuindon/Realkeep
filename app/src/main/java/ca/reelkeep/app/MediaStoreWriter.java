package ca.reelkeep.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.OutputStream;

final class MediaStoreWriter implements GalleryPublisher.Store<Uri> {
    private final ContentResolver resolver;
    private final FinalVideo video;
    MediaStoreWriter(ContentResolver resolver, FinalVideo video) { this.resolver = resolver; this.video = video; }
    @Override public Uri insert() {
        ContentValues values = new ContentValues();
        String name = video.file.getName();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, "video_" + System.currentTimeMillis() + name.substring(name.lastIndexOf('.')));
        values.put(MediaStore.Video.Media.MIME_TYPE, video.mime);
        values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ReelKeep");
        values.put(MediaStore.Video.Media.IS_PENDING, 1);
        return resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
    }
    @Override public OutputStream open(Uri item) throws Exception { return resolver.openOutputStream(item); }
    @Override public int publish(Uri item) {
        ContentValues values = new ContentValues(); values.put(MediaStore.Video.Media.IS_PENDING, 0);
        return resolver.update(item, values, null, null);
    }
    @Override public void delete(Uri item) { resolver.delete(item, null, null); }
}
