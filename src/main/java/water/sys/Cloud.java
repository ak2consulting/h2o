package water.sys;

import java.util.ArrayList;

public class Cloud {
  private final String[] _publicIPs, _privateIPs;

  public Cloud(String[] publicIPs, String[] privateIPs) {
    _publicIPs = publicIPs;
    _privateIPs = privateIPs;
  }

  public String[] publicIPs() {
    return _publicIPs;
  }

  public String[] privateIPs() {
    return _privateIPs;
  }

  public static void rsync(final Host... hosts) {
    ArrayList<Thread> threads = new ArrayList<Thread>();

    for( int i = 0; i < hosts.length; i++ ) {
      final int i_ = i;
      Thread t = new Thread() {
        @Override
        public void run() {
          hosts[i_].rsync(Host.defaultIncludes(), Host.defaultExcludes());
        }
      };
      t.setDaemon(true);
      t.start();
      threads.add(t);
    }

    for( Thread t : threads ) {
      try {
        t.join();
      } catch( InterruptedException e ) {
        throw new RuntimeException(e);
      }
    }
  }
}