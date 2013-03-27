package water.sys;

import java.io.File;
import java.io.FileWriter;
import java.util.*;

import water.Boot;
import water.Log;

public class Host {
  public static final String SSH_OPTS;

  static {
    SSH_OPTS = "" //
        + " -o UserKnownHostsFile=/dev/null" //
        + " -o StrictHostKeyChecking=no" //
        + " -o LogLevel=quiet";
  }

  public static final String FOLDER = "h2o_rsync";
  private final String       _address, _user, _key;

  public Host(String addr) {
    this(addr, null);
  }

  public Host(String addr, String user) {
    this(addr, user, null);
  }

  public Host(String addr, String user, String key) {
    _address = addr;
    _user = user != null ? user : System.getProperty("user.name");
    _key = key;
  }

  public String address() {
    return _address;
  }

  public String user() {
    return _user;
  }

  public String key() {
    return _key;
  }

  public static String[] defaultIncludes() {
    ArrayList<String> l = new ArrayList<String>();
    if( Boot._init.fromJar() ) {
      if( new File("target/h2o.jar").exists() )
        l.add("target/h2o.jar");
      else
        l.add("h2o.jar");
    } else {
      l.add("target");
      l.add("lib");
    }
    return l.toArray(new String[0]);
  }

  public static String[] defaultExcludes() {
    ArrayList<String> l = new ArrayList<String>();
    if( !Boot._init.fromJar() ) {
      l.add("target/*.jar");
      l.add("lib/javassist");
      l.add("**/*-sources.jar");
    }
    return l.toArray(new String[0]);
  }

  public void rsync(Collection<String> includes, Collection<String> excludes) {
    rsync(includes.toArray(new String[0]), excludes != null ? excludes.toArray(new String[0]) : null);
  }

  public void rsync(String[] includes, String[] excludes) {
    Process process = null;
    try {
      ArrayList<String> args = new ArrayList<String>();
      File onWindows = new File("C:/cygwin/bin/rsync.exe");
      args.add(onWindows.exists() ? onWindows.getAbsolutePath() : "rsync");
      args.add("-qvrzute");
      args.add(sshWithArgs());
      args.add("--chmod=u=rwx");

      for( int i = 0; i < includes.length; i++ ) {
        String path = new File(includes[i]).getAbsolutePath();
        // Adapts paths in case running on Windows
        includes[i] = path.replace('\\', '/').replace("C:/", "/cygdrive/c/");
      }
      args.addAll(Arrays.asList(includes));

      // --exclude doesn't seem work on Linux (?) so use --exclude-from
      File file = File.createTempFile("exclude", null);
      FileWriter w = new FileWriter(file);
      if( excludes != null )
        for( String s : excludes )
          w.write(s + '\n');
      w.close();
      args.add("--exclude-from");
      args.add(file.getAbsolutePath());

      args.add(_address + ":" + "/home/" + _user + "/" + FOLDER);
      // System.out.println(Arrays.toString(args.toArray()));
      ProcessBuilder builder = new ProcessBuilder(args);
      builder.environment().put("CYGWIN", "nodosfilewarning");
      process = builder.start();
      String log = "rsync " + VM.localIP() + " -> " + _address;
      System.out.println(log);
      NodeVM.inheritIO(process, Log.padRight(log + ": ", 24));
      process.waitFor();
    } catch( Exception ex ) {
      throw new RuntimeException(ex);
    } finally {
      if( process != null ) {
        try {
          process.destroy();
        } catch( Exception _ ) {
        }
      }
    }
  }

  static String ssh() {
    String ssh = "ssh";
    File onWindows = new File("C:/cygwin/bin/ssh.exe");
    if( onWindows.exists() ) {
      // Permissions are not always set correctly
      // TODO automate:
      // cd .ssh
      // chgrp Users id_rsa
      // chmod 600 id_rsa
      ssh = onWindows.getPath();
    }
    return ssh;
  }

  String sshWithArgs() {
    String k = "";
    if( _key != null ) {
      assert new File(_key).exists();
      // Git doesn't set permissions, so force them each time
      try {
        Process p = Runtime.getRuntime().exec("chmod 600 " + _key);
        p.waitFor();
      } catch( Exception e ) {
        throw new RuntimeException(e);
      }
      k = " -i " + _key;
    }
    return ssh() + " -l " + _user + " -A" + k + SSH_OPTS;
  }
}
