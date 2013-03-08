package water.sys;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Map.Entry;

import water.Log;

public class Jython {
  public static void main(String[] args) throws Exception {
    ArrayList<String> list = new ArrayList<String>();
    list.add("-Dpython.options.includeJavaStackInExceptions=true");
    list.add("-Dpython.options.showJavaExceptions=true");
    list.add("-Dpython.options.showPythonProxyExceptions=true");
    list.add("-S");

    // list.add("py/test.py");
    list.add("py/cypof.py");
    // list.add("-v");

    // Reflection: Jython is not a dependency during build
    Class c = Class.forName("org.python.util.jython");
    c.getMethod("run", String[].class).invoke(null, (Object) list.toArray(new String[0]));
  }

  public static class browser {
    public static void open(String url) throws Exception {
      java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
      desktop.browse(new URI(url));
    }
  }

  public static class http {
    public static String get(String url, float timeoutSecs, Map<String, Object> params) throws Exception {
      try {
        HttpURLConnection c = getConnection(url, timeoutSecs, params);
        BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream()));
        String result = "", line;
        while( (line = in.readLine()) != null )
          result += line;
        in.close();
        return result;
      } catch( Exception ex ) {
        if( !(ex instanceof IOException) )
          Log.write(ex);
        throw ex;
      }
    }

    public static String post(String url, float timeoutSecs, Map<String, Object> params, String file) {
      try {
        File file_ = new File(file);
        HttpURLConnection c = getConnection(url, timeoutSecs, params);
        String boundary = "683c3db71d444a2fbab155ba3e128d04";
        c.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
        c.connect();
        OutputStream out = c.getOutputStream();
        out.write(("--" + boundary + "\r\n").getBytes());
        out.write(("Content-Disposition: form-data; filename=\"" + file_.getName() + "\"\r\n").getBytes());
        out.write("Content-Type: application/octet-stream\r\n".getBytes());
        out.write("\r\n".getBytes());

        byte[] buffer = new byte[0xffff];
        FileInputStream in = new FileInputStream(file_);
        int n = 0;
        while( (n = in.read(buffer)) != -1 )
          out.write(buffer, 0, n);
        in.close();

        out.write(("\r\n--" + boundary + "--\r\n").getBytes());
        out.close();
        BufferedReader rd = new BufferedReader(new InputStreamReader(c.getInputStream()));
        String result = "", line;
        while( (line = rd.readLine()) != null )
          result += line;
        rd.close();
        return result;
      } catch( IOException ex ) {
        throw new RuntimeException(ex);
      } catch( Exception ex ) {
        Log.write(ex);
        throw new RuntimeException(ex);
      }
    }

    private static HttpURLConnection getConnection(String url, float timeoutSecs, Map<String, Object> params) throws Exception {
      String s = url;
      if( params != null ) {
        for( Entry<String, Object> entry : params.entrySet() ) {
          s += s.length() == url.length() ? "?" : "&";
          if( entry.getValue() != null ) {
            s += URLEncoder.encode(entry.getKey().toString(), "UTF-8");
            s += "=";
            s += URLEncoder.encode(entry.getValue().toString(), "UTF-8");
          }
        }
      }
      HttpURLConnection c = (HttpURLConnection) new URL(s).openConnection();
      c.setDoOutput(true);
      c.setRequestMethod("POST");
      c.setConnectTimeout((int) (timeoutSecs * 1000));
      c.setReadTimeout((int) (timeoutSecs * 1000));
      return c;
    }
  }
}
