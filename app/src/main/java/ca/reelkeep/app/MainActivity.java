package ca.reelkeep.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.*;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
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
   TextView title=new TextView(this);title.setText("ReelKeep");title.setTextSize(28);root.addView(title);
   TextView guide=new TextView(this);guide.setText("Share a public Instagram Reel here to clean tracking parameters and download it. No Meta login.");root.addView(guide);
   input=new EditText(this);input.setHint("Paste Instagram Reel link");input.setSingleLine(true);root.addView(input);
   download=new Button(this);download.setText("Download Reel");root.addView(download);
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
   if(intent==null||!Intent.ACTION_SEND.equals(intent.getAction()))return;
   CharSequence t=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
   String cleaned=ReelUrlCleaner.clean(t==null?null:t.toString());
   if(cleaned==null){msg("Not a supported Instagram Reel link");return;}
   input.setText(cleaned); start();
 }
 private void start(){
   if(busy)return;
   String cleaned=ReelUrlCleaner.clean(input.getText().toString());
   if(cleaned==null){msg("Please provide a valid instagram.com/reel/ URL");return;}
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
       if(!initialized){YoutubeDL.getInstance().init(getApplicationContext());initialized=true;}
       msg("Checking for a newer yt-dlp version on GitHub...");
       YoutubeDL.UpdateStatus result=YoutubeDL.getInstance().updateYoutubeDL(getApplicationContext(),YoutubeDL.UpdateChannel._STABLE);
       msg("Downloader: "+String.valueOf(result)+". Try the Reel again.");
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
     request.addOption("-f","best[ext=mp4]/best");
     request.addOption("-o",new File(dir,"reel.%(ext)s").getAbsolutePath());
     msg("Downloading…");
     YoutubeDL.getInstance().execute(request, "ReelKeep-" + System.nanoTime(), (percent, eta, line) -> {
       runOnUiThread(() -> progress.setProgress(Math.max(0, Math.min(100, percent.intValue()))));
       return kotlin.Unit.INSTANCE;
     });
     File[] found=dir.listFiles((d,n)->n.endsWith(".mp4")||n.endsWith(".webm"));
     if(found==null||found.length==0)throw new IOException("No video produced");
     File video=found[0]; String mime=video.getName().endsWith(".webm")?"video/webm":"video/mp4";
     ContentValues cv=new ContentValues();
     cv.put(MediaStore.Video.Media.DISPLAY_NAME,"reel_"+System.currentTimeMillis()+(mime.equals("video/mp4")?".mp4":".webm"));
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
     msg("Download failed:\n"+readableError(error)+"\n\nIf this mentions an outdated extractor, tap Update downloader and retry.");
   }finally{
     if(dir.exists()){File[] fs=dir.listFiles();if(fs!=null)for(File f:fs)f.delete();dir.delete();}
     busy=false;runOnUiThread(()->{download.setEnabled(true);updateEngine.setEnabled(true);});
   }
 }
}
