package water.sys;

import java.io.File;
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
    this(addr, System.getProperty("user.name"));
  }

  public Host(String addr, String user) {
    this(addr, user, null);
  }

  public Host(String addr, String user, String key) {
    _address = addr;
    _user = user;
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

  public static void rsync(final Host... hosts) throws Exception {
    Thread[] threads = new Thread[hosts.length];

    for( int i = 0; i < threads.length; i++ ) {
      final int i_ = i;
      threads[i] = new Thread() {
        @Override
        public void run() {
          hosts[i_].rsync();
        }
      };
      threads[i].setDaemon(true);
      threads[i].start();
    }

    for( int i = 0; i < threads.length; i++ )
      threads[i].join();
  }

  void rsync() {
    Process process = null;
    try {
      ArrayList<String> args = new ArrayList<String>();
      File onWindows = new File("C:/cygwin/bin/rsync.exe");
      args.add(onWindows.exists() ? onWindows.getAbsolutePath() : "rsync");
      args.add("-vrzute");
      args.add(ssh());
      args.add("--delete");
      args.add("--chmod=u=rwx");

      ArrayList<String> sources = new ArrayList<String>();
      if( Boot._init.fromJar() ) {
        String[] cp = System.getProperty("java.class.path").split(File.pathSeparator);
        sources.addAll(Arrays.asList(cp));
      } else {
        args.add("--exclude");
        args.add("'target/*.jar'");
        args.add("--exclude");
        args.add("'lib/javassist'");

        sources.add("target");
        sources.add("lib");
      }
      for( int i = 0; i < sources.size(); i++ ) {
        String path = new File(sources.get(i)).getAbsolutePath();
        // Adapts paths in case running on Windows
        sources.set(i, path.replace('\\', '/').replace("C:/", "/cygdrive/c/"));
      }
      args.addAll(sources);

      args.add(_address + ":" + "/home/" + _user + "/" + FOLDER);
      //System.out.println(Arrays.toString(args.toArray()));
      ProcessBuilder builder = new ProcessBuilder(args);
      builder.environment().put("CYGWIN", "nodosfilewarning");
      process = builder.start();
      NodeVM.inheritIO(process, Log.padRight("rsync to " + _address + ": ", 24));
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

  String ssh() {
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
    String k = _key != null ? " -i " + _key : "";
    return ssh + " -l " + _user + " -A" + k + SSH_OPTS;
  }
}
