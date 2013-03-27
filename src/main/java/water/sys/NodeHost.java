package water.sys;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import water.Boot;
import water.Log;
import water.sys.VM.Watchdog;

/**
 * Creates a node on a host.
 */
public class NodeHost implements Node {
  private volatile SSH _ssh;

  public NodeHost(Host host, String[] javaArgs, String[] nodeArgs) {
    _ssh = new SSH(host, new String[] { command(javaArgs, nodeArgs) });
  }

  public Host host() {
    return _ssh.host();
  }

  @Override
  public String address() {
    return host().address();
  }

  @Override
  public void inheritIO() {
    _ssh.inheritIO();
  }

  @Override
  public void persistIO(String outFile, String errFile) throws IOException {
    _ssh.persistIO(outFile, errFile);
  }

  @Override
  public void start() {
    _ssh.startThread();
  }

  @Override
  public boolean isAlive() {
    return _ssh._thread == null || _ssh._thread.isAlive();
  }

  @Override
  public int waitFor() {
    try {
      _ssh._thread.join();
    } catch( InterruptedException e ) {
    }
    return 0;
  }

  @Override
  public void kill() {
    // TODO;
  }

  public static String command(String[] javaArgs, String[] nodeArgs) {
    ArrayList<String> list = new ArrayList<String>();
    // TODO When port forwarding done
    // list.add("-agentlib:jdwp=transport=dt_socket,address=127.0.0.1:8000,server=y,suspend=n");
    VM.defaultParams(list);
    if( javaArgs != null )
      list.addAll(Arrays.asList(javaArgs));

    String cp = "";
    try {
      int shared = new File(".").getCanonicalPath().length() + 1;
      for( String s : System.getProperty("java.class.path").split(File.pathSeparator) ) {
        cp += cp.length() != 0 ? ":" : "";
        if( Boot._init.fromJar() )
          cp += new File(s).getName();
        else
          cp += new File(s).getCanonicalPath().substring(shared).replace('\\', '/');
      }
      list.add("-cp");
      list.add(cp);
    } catch( IOException e ) {
      throw new RuntimeException(e);
    }

    String command = "cd " + Host.FOLDER + ";java";
    for( String s : list )
      command += " " + s;
    command += " " + NodeVM.class.getName();
    for( String s : nodeArgs )
      command += " " + s;
    return command.replace("$", "\\$");
  }

  static class SSH extends Watchdog {
    Thread _thread;

    public SSH(Host host, String[] args) {
      super(host, args);
    }

    final void startThread() {
      _thread = new Thread() {
        @Override
        public void run() {
          try {
            SSH.this.start();
            SSH.this.waitFor();
          } catch( Exception ex ) {
            Log.write(ex);
          }
        }
      };
      _thread.setDaemon(true);
      _thread.start();
    }

    public static void main(String[] args) throws Exception {
      exitWithParent();

      Host host = getHost(args);
      ArrayList<String> list = new ArrayList<String>();
      list.addAll(Arrays.asList(host.sshWithArgs().split(" ")));
      list.add(host.address());
      // TODO Port forwarding for security
      // list.add("-L");
      // list.add("8000:127.0.0.1:" + local);
      list.addAll(Arrays.asList(getArgs(args)));
      exec(list);
    }
  }
}
