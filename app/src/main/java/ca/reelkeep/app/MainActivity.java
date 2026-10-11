package ca.reelkeep.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.*;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.ffmpeg.FFmpeg;
import java.io.*;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
 private EditText input;
 private TextView status;
 private ProgressBar progress;
 private Button download;
 private Button updateEngine;
 private volatile boolean busy=false;
 private boolean initialized=false;
 private void msg(String message) { runOnUiThread(()->status.setText(message)); }
 @Override public void onCreate(Bundle b) {
   super.onCreate(b);
   LinearLayout root=new LinearLayout(this);
   root.setPadding(32,50,32,24);root.setOrientation(LinearLayout.VERTICAL);
   TextView title=new TextView(this);title.setText("ReelKeep v0.3.0");title.setTextSize(28);root.addView(title);
   TextView guide=new TextView(this);guide.setText("Share an Instagram Reel, YouTube video, or YouTube Short here. Tracking parameters are removed. No account login.");root.addView(guide);
   input=new EditText(this);input.setHint("Shared video link (Instagram / YouTube)");input.setSingleLine(false);input.setMinLines(2);input.setMaxLines(4);input.setSelectAllOnFocus(true);root.addView(input);
   download=new Button(this);download.setText("Download Video");root.addView(download);
   updateEngine=new Button(this);updateEngine.setText("Update downloader (yt-dlp)");root.addView(updateEngine);
   progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);root.addView(progress);
   status=new TextView(this);status.setText("Ready");status.setTextIsSelectable(true);
   ScrollView outputScroll=new ScrollView(this);
   outputScroll.setFillViewport(false);
   outputScroll.addView(status);
   root.addView(outputScroll,new LinearLayout.LayoutParams(-1,0,1));
   setContentView(root);
   download.setOnClickListener(v->start());
   updateEngine.setOnClickListener(v->updateDownloader());
   accept(getIntent());
 }
 @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);accept(i);}
 private void accept(Intent intent){
   if(intent==null)return;
   // Shared apps vary: EXTRA_TEXT, EXTRA_SUBJECT, ClipData, or intent.data.
   StringBuilder candidates=new StringBuilder();
   CharSequence body=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
   if(body!=null)candidates.append(body).append('\n');
   CharSequence subject=intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT);
   if(subject!=null)candidates.append(subject).append('\n');
   if(intent.getData()!=null)candidates.append(intent.getData()).append('\n');
   ClipData clips=intent.getClipData();
   if(clips!=null){
     for(int i=0;i<Math.min(12,clips.getItemCount());i++){
       ClipData.Item item=clips.getItemAt(i);
       if(item.getText()!=null)candidates.append(item.getText()).append('\n');
       if(item.getUri()!=null)candidates.append(item.getUri()).append('\n');
     }
   }
   String raw=candidates.toString();
   if(raw.trim().isEmpty()){
     if(Intent.ACTION_SEND.equals(intent.getAction())||Intent.ACTION_VIEW.equals(intent.getAction())){
       msg("This share didn't contain a video URL. Use Share → Copy link, then paste the link here.");
     }
     return;
   }
   String cleaned=ReelUrlCleaner.clean(raw);
   if(cleaned==null){
     input.setText(raw.length()>1000?raw.substring(0,1000):raw);
     msg("A link was received but wasn't recognized as an Instagram Reel, YouTube video, or Short. Try Copy link.");
     return;
   }
   input.setText(cleaned);
   input.setSelection(cleaned.length());
   msg("Received cleaned link. Starting download…");
   start();
 }
 private void start(){
   if(busy)return;
   String cleaned=ReelUrlCleaner.clean(input.getText().toString());
   if(cleaned==null){msg("Provide an Instagram Reel, YouTube video, or YouTube Shorts link");return;}
   input.setText(cleaned); busy=true;download.setEnabled(false);updateEngine.setEnabled(false);progress.setProgress(0);
   Executors.newSingleThreadExecutor().execute(()->downloadReel(cleaned));
 }
 private void updateDownloader(){
   if(busy)return;
   busy=true;
   download.setEnabled(false);
   updateEngine.setEnabled(false);
   Executors.newSingleThreadExecutor().execute(()->{
     try{
       msg("Initializing downloader...");
       if(!initialized){YoutubeDL.getInstance().init(getApplicationContext());FFmpeg.getInstance().init(getApplicationContext());initialized=true;}
       msg("Checking for a newer yt-dlp version on GitHub...");
       YoutubeDL.UpdateStatus result=YoutubeDL.getInstance().updateYoutubeDL(getApplicationContext(),YoutubeDL.UpdateChannel._STABLE);
       msg("Downloader: "+String.valueOf(result)+". Try downloading again.");
     }catch(Exception e){
       android.util.Log.e("ReelKeep","Update failed",e);
       msg("Downloader update failed:\n"+readableError(e));
     }finally{
       busy=false;
       runOnUiThread(()->{download.setEnabled(true);updateEngine.setEnabled(true);});
     }
   });
 }
 private static String readableError(Exception e){
   String message=e.getMessage();
   if(message==null||message.trim().isEmpty())message=e.toString();
   Throwable cause=e.getCause();
   if(cause!=null && (e.getMessage()==null || !e.getMessage().contains(cause.getMessage()==null?"":cause.getMessage())))
      message+="\nCause: "+cause;
   message=message.trim();
   // Prevent very large output from burying the actionable end of the error.
   if(message.length()>2400)message="...\n"+message.substring(message.length()-2400);
   return message;
 }
 private void downloadReel(String url){
   File dir=new File(getCacheDir(),"reel"+System.nanoTime());
   try{
     if(!dir.mkdirs())throw new IOException("Cannot create temporary directory");
     if(!initialized){YoutubeDL.getInstance().init(getApplicationContext());initialized=true;}
     YoutubeDLRequest request=new YoutubeDLRequest(url);
     request.addOption("--ignore-config");
     request.addOption("--no-playlist");
     request.addOption("--no-mtime");
     if(url.contains("youtube.com/") || url.contains("youtu.be/")){
       // Prefer compatible MP4 video and M4A audio, merging adaptive streams with FFmpeg.
       // Fall back to other available adaptive or combined formats.
       request.addOption("-f","bv*[ext=mp4]+ba[ext=m4a]/bv*+ba/b");
       request.addOption("--merge-output-format","mp4");
     }else{
       request.addOption("-f","best[ext=mp4]/best");
     }
     request.addOption("-o",new File(dir,"reel.%(ext)s").getAbsolutePath());
     msg("Downloading…");
     YoutubeDL.getInstance().execute(request, "ReelKeep-" + System.nanoTime(), (percent, eta, line) -> {
       runOnUiThread(() -> progress.setProgress(Math.max(0, Math.min(100, percent.intValue()))));
       return kotlin.Unit.INSTANCE;
     });
     File[] found=dir.listFiles((d,n)->n.endsWith(".mp4")||n.endsWith(".webm")||n.endsWith(".mkv"));
     if(found==null||found.length==0)throw new IOException("No video produced");
     File video=found[0]; String mime=video.getName().endsWith(".webm")?"video/webm":video.getName().endsWith(".mkv")?"video/x-matroska":"video/mp4";
     ContentValues cv=new ContentValues();
     cv.put(MediaStore.Video.Media.DISPLAY_NAME,"video_"+System.currentTimeMillis()+(mime.equals("video/mp4")?".mp4":mime.equals("video/webm")?".webm":".mkv"));
     cv.put(MediaStore.Video.Media.MIME_TYPE,mime);
     cv.put(MediaStore.Video.Media.RELATIVE_PATH,Environment.DIRECTORY_MOVIES+"/ReelKeep");
     cv.put(MediaStore.Video.Media.IS_PENDING,1);
     Uri saved=getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,cv);
     if(saved==null)throw new IOException("Cannot save video");
     try(InputStream in=new FileInputStream(video);OutputStream out=getContentResolver().openOutputStream(saved)) {
       if(out==null)throw new IOException("Cannot write saved video");
       byte[] data=new byte[8192]; int n;
       while((n=in.read(data))!=-1)out.write(data,0,n);
     }catch(Exception ex){getContentResolver().delete(saved,null,null);throw ex;}
     cv.clear();cv.put(MediaStore.Video.Media.IS_PENDING,0);getContentResolver().update(saved,cv,null,null);
     msg("Saved to Gallery → Albums → ReelKeep");runOnUiThread(()->progress.setProgress(100));
   }catch(Exception error){
     android.util.Log.e("ReelKeep","Download failed",error);
     msg("Download failed:\n"+readableError(error)+"\n\nIf the error mentions an outdated extractor, tap Update downloader and retry. YouTube may require sign-in for restricted videos; this app does not use your account.");
   }finally{
     if(dir.exists()){File[] fs=dir.listFiles();if(fs!=null)for(File f:fs)f.delete();dir.delete();}
     busy=false;runOnUiThread(()->{download.setEnabled(true);updateEngine.setEnabled(true);});
   }
 }
}
