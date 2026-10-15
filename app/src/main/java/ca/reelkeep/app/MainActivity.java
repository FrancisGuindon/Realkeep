package ca.reelkeep.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;

public class MainActivity extends Activity {
    private EditText input;
    private TextView status;
    private ProgressBar progress;
    private Button download, updateEngine, cancel;
    private DownloadCoordinator coordinator;
    private final DownloadCoordinator.Listener observer = this::render;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        coordinator = ((ReelKeepApplication) getApplication()).coordinator();
        LinearLayout root = new LinearLayout(this);
        root.setPadding(32, 50, 32, 24); root.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(getString(R.string.app_version, BuildConfig.VERSION_NAME)); title.setTextSize(28); root.addView(title);
        TextView guide = new TextView(this); guide.setText(R.string.guide); root.addView(guide);
        input = new EditText(this); input.setId(R.id.video_url); input.setHint(R.string.video_link);
        input.setMinLines(2); input.setMaxLines(4); input.setSelectAllOnFocus(true); root.addView(input);
        download = button(root, R.id.download_video, R.string.download_video);
        updateEngine = button(root, R.id.update_engine, R.string.update_engine);
        cancel = button(root, R.id.cancel_download, R.string.cancel_download);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100); root.addView(progress);
        status = new TextView(this); status.setId(R.id.download_status); status.setTextIsSelectable(true);
        ScrollView output = new ScrollView(this); output.addView(status);
        root.addView(output, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root);
        download.setOnClickListener(view -> start());
        updateEngine.setOnClickListener(view -> coordinator.update());
        cancel.setOnClickListener(view -> coordinator.cancel());
        render(coordinator.state());
        if (saved == null) accept(getIntent()); else input.setText(saved.getString("input", ""));
    }
    private Button button(LinearLayout root, int id, int label) {
        Button result = new Button(this); result.setId(id); result.setText(label); root.addView(result); return result;
    }
    @Override protected void onStart() { super.onStart(); coordinator.attach(observer); }
    @Override protected void onStop() { coordinator.detach(observer); super.onStop(); }
    @Override protected void onSaveInstanceState(Bundle saved) {
        saved.putString("input", input.getText().toString()); super.onSaveInstanceState(saved);
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); accept(intent); }
    private void accept(Intent intent) {
        if (intent == null) return;
        StringBuilder candidates = new StringBuilder();
        CharSequence body = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        CharSequence subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT);
        if (body != null) candidates.append(body).append('\n');
        if (subject != null) candidates.append(subject).append('\n');
        if (intent.getData() != null) candidates.append(intent.getData()).append('\n');
        ClipData clips = intent.getClipData();
        if (clips != null) for (int i = 0; i < Math.min(12, clips.getItemCount()); i++) {
            ClipData.Item item = clips.getItemAt(i);
            if (item.getText() != null) candidates.append(item.getText()).append('\n');
            if (item.getUri() != null) candidates.append(item.getUri()).append('\n');
        }
        setIntent(new Intent(this, MainActivity.class)); // Consume the share once.
        String raw = candidates.toString();
        if (raw.trim().isEmpty()) {
            if (Intent.ACTION_SEND.equals(intent.getAction()))
                coordinator.notice("This share contained no video URL. Try Share → Copy link, then paste it here.");
            return;
        }
        String cleaned = ReelUrlCleaner.clean(raw);
        if (cleaned == null) {
            input.setText(raw.substring(0, Math.min(1000, raw.length())));
            coordinator.notice("The shared link was not recognized. Try an Instagram Reel or YouTube video link."); return;
        }
        input.setText(cleaned); input.setSelection(cleaned.length());
        if (coordinator.state().busy)
            Toast.makeText(this, "A download is running. The next link is ready in the box.", Toast.LENGTH_LONG).show();
        else start();
    }
    private void start() {
        String cleaned = ReelUrlCleaner.clean(input.getText().toString());
        if (cleaned == null) { coordinator.notice("Provide an Instagram Reel, YouTube video, or YouTube Shorts link"); return; }
        input.setText(cleaned); coordinator.download(cleaned);
    }
    private void render(DownloadCoordinator.State state) {
        download.setEnabled(!state.busy); updateEngine.setEnabled(!state.busy);
        cancel.setEnabled(state.busy && state.cancellable);
        progress.setProgress(state.percent); status.setText(state.message);
    }
}
