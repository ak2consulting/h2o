package water.sys;

import java.util.HashSet;

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
          hosts[i_].rsync(Host.defaultIncludes(), Host.defaultExcludes());
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
}