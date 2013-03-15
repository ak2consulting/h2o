package water.sys;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import water.Log;
import water.sys.VM.Watchdog;

/**
 * Creates a node on a host.
 */
public class NodeHost implements Node {
  private final Host     _host;
  private final String[] _args;
  private final Thread   _thread;

  public NodeHost(Host host, String[] args) {
    _host = host;
    _args = args;

    _thread = new Thread() {
      @Override
      public void run() {
        try {
          SSH ssh = new SSH(_host, _args);
          ssh.start();
          ssh.waitFor();
        } catch( Exception ex ) {
          Log.write(ex);
        }
      }
    };

    _thread.setDaemon(true);
  }

  public Host host() {
    return _host;
  }

  @Override
  public void inheritIO() {

  }

  @Override
  public void persistIO(String outFile, String errFile) throws IOException {

  }

  @Override
  public void start() {
    _thread.start();
  }

  @Override
  public boolean isAlive() {
    return _thread.isAlive();
  }

  @Override
  public int waitFor() {
    try {
      _thread.join();
    } catch( InterruptedException e ) {
    }
    return 0;
  }

  @Override
  public void kill() {
    // TODO;
  }

  public static String command(String[] args) throws Exception {
    ArrayList<String> list = new ArrayList<String>();
    // TODO When port forwarding done
    // list.add("-agentlib:jdwp=transport=dt_socket,address=127.0.0.1:8000,server=y,suspend=n");
    VM.defaultParams(list);

    String cp = "";
    int shared = new File(".").getCanonicalPath().length() + 1;
    for( String s : System.getProperty("java.class.path").split(File.pathSeparator) ) {
      cp += cp.length() != 0 ? ":" : "";
      cp += new File(s).getCanonicalPath().substring(shared).replace('\\', '/');
    }
    list.add("-cp");
    list.add(cp);

    String command = "cd " + Host.FOLDER + ";java";
    for( String s : list )
      command += " " + s;
    command += " " + NodeVM.class.getName();
    for( String s : args )
      command += " " + s;
    return command;
  }

  static class SSH extends Watchdog {
    public SSH(Host host, String[] args) {
      super(host, args);
    }

    public static void main(String[] args) throws Exception {
      exitWithParent();

      Host host = getHost(args);
      ArrayList<String> list = new ArrayList<String>();
      list.addAll(Arrays.asList(host.ssh().split(" ")));
      list.add(host.addr());
      // TODO Port forwarding for security
      // list.add("-L");
      // list.add("8000:127.0.0.1:" + local);
      list.add(command(getArgs(args)));
      exec(list);
    }
  }
}
