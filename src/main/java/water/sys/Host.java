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

  public static void rsync(Iterable<Node> nodes) throws Exception {
    HashSet<Host> hosts = new HashSet<Host>();
    for( Node node : nodes )
      if( node instanceof NodeHost )
        hosts.add(((NodeHost) node).host());
    rsync(hosts.toArray(new Host[0]));
  }

  public static void rsync(NodeHost... nodes) throws Exception {
    HashSet<Host> hosts = new HashSet<Host>();
    for( NodeHost node : nodes )
      hosts.add(node.host());
    rsync(hosts.toArray(new Host[0]));
  }

  public static void rsync(final Host... hosts) {
    Thread[] threads = new Thread[hosts.length];

    for( int i = 0; i < threads.length; i++ ) {
      final int i_ = i;
      threads[i] = new Thread() {
        @Override
        public void run() {
          ArrayList<String> includes = new ArrayList<String>();
          ArrayList<String> excludes = new ArrayList<String>();
          hosts[i_].rsync(includes, excludes);
        }
      };
      threads[i].setDaemon(true);
      threads[i].start();
    }

    for( int i = 0; i < threads.length; i++ ) {
      try {
        threads[i].join();
      } catch( InterruptedException e ) {
        throw new RuntimeException(e);
      }
    }
  }

  public void rsync(List<String> includes, List<String> excludes) {
    Process process = null;
    try {
      ArrayList<String> args = new ArrayList<String>();
      File onWindows = new File("C:/cygwin/bin/rsync.exe");
      args.add(onWindows.exists() ? onWindows.getAbsolutePath() : "rsync");
      args.add("-vrzute");
      args.add(sshWithArgs());
      args.add("--chmod=u=rwx");

      if( Boot._init.fromJar() ) {
        String[] cp = System.getProperty("java.class.path").split(File.pathSeparator);
        includes.addAll(Arrays.asList(cp));
      } else {
        includes.add("target");
        includes.add("lib");

        excludes.add("target/*.jar");
        excludes.add("lib/javassist");
        excludes.add("**/*-sources.jar");
      }

      for( int i = 0; i < includes.size(); i++ ) {
        String path = new File(includes.get(i)).getAbsolutePath();
        // Adapts paths in case running on Windows
        includes.set(i, path.replace('\\', '/').replace("C:/", "/cygdrive/c/"));
      }
      args.addAll(includes);

      // --exclude doesn't seem work on Linux (?) so use --exclude-from
      File file = File.createTempFile("exclude", null);
      FileWriter w = new FileWriter(file);
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
      NodeVM.inheritIO(process, Log.padRight("rsync " + VM.localIP() + " -> " + _address + ": ", 24));
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
