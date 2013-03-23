package water;

import java.io.File;
import java.util.ArrayList;

import water.parser.ParseDataset;
import water.sys.*;
import water.util.Utils;

public class Sandbox {
  public static void main(String[] _) throws Exception {
    Cloud ec2 = EC2.resize(1, "m1.xlarge");

    Host master = new Host(ec2.publicIPs()[0]);
    ArrayList<String> includes = new ArrayList<String>(), excludes = new ArrayList<String>();
    includes.add("py");
    includes.add("smalldata");
    //
    excludes.add("py/**.class");
    excludes.add("**/cachedir");
    excludes.add("**/sandbox");
    master.rsync(includes, excludes);

    String hosts = "";
    for( String ip : ec2.privateIPs() )
      hosts += ip + ",";

    String[] args = new String[] { "-mainClass", //
        // "water.sys.Jython", //
        // "py/cypof.py", //

        Master.class.getName(), //
        "-hosts", //
        hosts, //

    // "py/testdir_hosts/test_w_hosts.py", //
    // "-cj", //
    // "py/testdir_hosts/pytest_config-cypof.json", //
    // "-v"
    };

    RemoteRunner.launch(master, args);

    Thread.sleep(1000);

    // Desktop desktop = Desktop.getDesktop();
    // desktop.browse(new URI("http://localhost:54321/Jobs.html"));
    // desktop.browse(new URI("http://localhost:54321/Inspect.html?key=test.hex"));
    // desktop.browse(new URI("http://localhost:54321/Timeline.html"));

    Utils.readConsole();
  }

  public static class Master {
    public static void main(String[] args) throws Exception {
      VM.exitWithParent();

      ArrayList<Node> nodes = new ArrayList<Node>();
      String[] ips = args[1].split(",");
      for( int i = 1; i < ips.length; i++ ) {
        Host host = new Host(ips[i]);
        nodes.add(new NodeHost(host, null, args));
      }
      Host.rsync(nodes);
      for( Node node : nodes ) {
        node.inheritIO();
        node.start();
      }
      H2O.main(args);
      TestUtil.stall_till_cloudsize(nodes.size() + 1);

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
    }
  }
}
