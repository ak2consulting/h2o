package water;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;

import water.parser.ParseDataset;
import water.sys.*;
import water.util.Utils;

public class Sandbox {
  public static void main(String[] _) throws Exception {
    Cloud ec2 = EC2.resize(3, "m1.small");

    String[] args = new String[] { "-mainClass", //
        "water.sys.Jython", //
        "py/cypof.py", //
        // "py/testdir_hosts/test_w_hosts.py", //
        // "-cj", //
        // "py/testdir_hosts/pytest_config-cypof.json", //
        "-v" };

    RemoteRunner.exec(ec2, args);
    Utils.readConsole();

    // H2O.main(new String[] {});
    // TestUtil.stall_till_cloudsize(1);

    // ArrayList<Node> nodes = new ArrayList<Node>();
    // int count = 1 + 2;
    //
    // for( int i = 0; i < count - 1; i++ ) {
    // // sites.add(new NodeCL(args));
    // // sites.add(new NodeVM(args));
    //
    // Host host = new Host("192.168.1.15" + (i + 1));
    // nodes.add(new NodeHost(host, null, args));
    // }
    //
    // for( Node node : nodes )
    // node.start();

    File f = new File("smalldata/covtype/covtype.20k.data");
    // File f = new File("../../aaaa/datasets/millionx7_logreg.data.gz");
    // File f = new File("smalldata/test/rmodels/iris_x-iris-1-4_y-species_ntree-500.rdata");
    // File f = new File("py/testdir_single_jvm/syn_datasets/hastie_4x.data");
    Key key = TestUtil.load_test_file(f, "test");
    Key dest = Key.make("test.hex");
    ParseDataset.parse(dest, DKV.get(key));
    Desktop desktop = Desktop.getDesktop();
    // desktop.browse(new URI("http://localhost:54321/Jobs.html"));
    // desktop.browse(new URI("http://localhost:54321/Inspect.html?key=test.hex"));
    desktop.browse(new URI("http://localhost:54321/Timeline.html"));

    Utils.readConsole();
  }
}
