package ca.reelkeep.app;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public final class ReelUrlCleaner {
  private static final Pattern LINKS=Pattern.compile("https?://[^\\s<>\\\"']+",Pattern.CASE_INSENSITIVE);
  private static final Pattern CODE=Pattern.compile("[A-Za-z0-9_-]{5,50}");
  private ReelUrlCleaner(){}
  public static String clean(String text){
    if(text==null)return null;
    Matcher m=LINKS.matcher(text);
    while(m.find()){
      try{
        URI u=new URI(m.group().replaceAll("[),.;!]+$",""));
        String host=u.getHost();
        if(host==null)continue;
        host=host.toLowerCase(Locale.ROOT);
        if(!host.equals("instagram.com")&&!host.equals("www.instagram.com")&&!host.equals("m.instagram.com"))continue;
        String[] segments=u.getPath().split("/");
        if(segments.length!=3||!segments[1].equalsIgnoreCase("reel")||!CODE.matcher(segments[2]).matches())continue;
        return "https://www.instagram.com/reel/"+segments[2]+"/";
      }catch(Exception ignored){}
    }
    return null;
  }
}
