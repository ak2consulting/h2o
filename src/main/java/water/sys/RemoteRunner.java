package water.sys;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;

import water.H2O;
import water.sys.VM.Watchdog;

public class RemoteRunner {
  public static VM launch(Host host, String[] args) {
    SSHWatchdog r = new SSHWatchdog(host, args);
    r.inheritIO();
    r.start();
    return r;
  }

  static void main(String[] ipsAndargs) throws Exception {
    VM.exitWithParent();
    String[] args = Arrays.copyOfRange(ipsAndargs, 1, ipsAndargs.length);

    ArrayList<Node> nodes = new ArrayList<Node>();
    String[] ips = ipsAndargs[0].split(",");
    for( int i = 1; i < ips.length; i++ ) {
      Host host = new Host(ips[i]);
      nodes.add(new NodeHost(host, null, args));
    }
    Cloud.rsync(nodes);

    for( Node node : nodes ) {
      node.inheritIO();
      node.start();
    }
    H2O.main(args);
  }

  static class SSHWatchdog extends Watchdog {
    public SSHWatchdog(Host host, String[] args) {
      super(host, args);
    }

    public static void main(String[] args) throws Exception {
      exitWithParent();

      Host host = getHost(args);
      String key = host.key() != null ? host.key() : "";
      String s = "ssh-agent sh -c \"ssh-add " + key + "; ssh -l " + host.user() + " -A" + Host.SSH_OPTS;
      s += " -L 54321:127.0.0.1:54321"; // Port forwarding
      s += " " + host.address() + " '" + NodeHost.command(null, getArgs(args)) + "'\"";
      s = s.replace("\\", "\\\\").replace("$", "\\$");

      ArrayList<String> list = new ArrayList<String>();
      // Have to copy to file for cygwin, but works also on -nix
      File sh = File.createTempFile("h2o", null);
      FileWriter w = new FileWriter(sh);
      w.write(s);
      w.close();
      File onWindows = new File("C:/cygwin/bin/bash.exe");
      if( onWindows.exists() ) {
        list.add(onWindows.getPath());
        list.add("--login");
      } else
        list.add("bash");
      list.add(sh.getAbsolutePath());
      exec(list);
    }
  }
}
