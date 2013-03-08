package water.util;

import hex.GLMGridTest;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;

import water.*;
import water.sys.*;

public class TestRunner {
  public static void main(String[] args) throws Exception {
    Log.initHeaders();
    ArrayList<Node> nodes = new ArrayList<Node>();
    int count = 1 + 2;

    for( int i = 0; i < count - 1; i++ ) {
      // sites.add(new NodeCL(args));
      // sites.add(new NodeVM(args));

      Host host = new Host("192.168.1.15" + (i + 1));
      nodes.add(new NodeHost(host, args));
    }

    Host.rsync(nodes);

    for( Node node : nodes )
      node.start();

    H2O.main(args);
    TestUtil.stall_till_cloudsize(count);

    Desktop desktop = Desktop.getDesktop();
    desktop.browse(new URI("http://localhost:54321/StoreView.html"));

    // Desktop desktop = Desktop.getDesktop();
    // desktop.browse(new URI("http://localhost:54321/Cloud.html"));

    // new KMeansTest().testGaussian((int) 1e6);
    // org.junit.runner.JUnitCore.runClasses(KMeansTest.class);
    org.junit.runner.JUnitCore.runClasses(GLMGridTest.class);
    org.junit.runner.JUnitCore.runClasses(KVTest.class);

    // Utils.readConsole();

    // for( Node site : nodes )
    // site.kill();
    //
    // // TODO proper shutdown of remaining threads?
    // System.exit(0);
  }
}
