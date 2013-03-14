package water;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;

import water.sys.*;
import water.sys.VM.Watchdog;

/**
 * Runs TestRunner on a remote machine.
 */
public class TestRunnerRemote {
  public static void main(String[] _) throws Exception {
    Host host = new Host("192.168.1.150");
    Host.rsync(host);

    String[] args = new String[] { "-mainClass", TestRunner.class.getName(), "--log_headers" };
    Remote r = new Remote(host, args);
    r.start();
    r.waitFor();
  }

  static class Remote extends Watchdog {
    public Remote(Host host, String[] args) {
      super(host, args);
    }

    public static void main(String[] args) throws Exception {
      exitWithParent();

      Host host = getHost(args);
      String key = host.key() != null ? host.key() : "";
      String s = "ssh-agent sh -c \"ssh-add " + key + "; ssh -l " + host.user() + " -A" + Host.SSH_OPTS;
      s += " " + host.addr() + " '" + NodeHost.command(getArgs(args)) + "'\"";
      System.out.println(s);
      ArrayList<String> list = new ArrayList<String>();
      File onWindows = new File("C:/cygwin/bin/bash.exe");
      if( onWindows.exists() ) {
        File sh = File.createTempFile("h2o", null);
        FileWriter w = new FileWriter(sh);
        w.write(s);
        w.close();
        list.add(onWindows.getPath());
        list.add("--login");
        list.add(sh.getAbsolutePath());
      } else
        list.add(s);

      exec(list);
    }
  }
}
