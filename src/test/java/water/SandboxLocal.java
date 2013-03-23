package water;

import java.io.File;
import java.util.ArrayList;

import water.parser.ParseDataset;
import water.sys.Node;
import water.sys.NodeVM;

public class SandboxLocal {
  public static void main(String[] args) throws Exception {
    ArrayList<Node> nodes = new ArrayList<Node>();
    int count = 1 + 2;

    for( int i = 0; i < count - 1; i++ ) {
      // sites.add(new NodeCL(args));
      nodes.add(new NodeVM(args));

      // Host host = new Host("192.168.1.15" + (i + 1));
      // nodes.add(new NodeHost(host, null, args));
    }

    H2O.main(args);
    for( Node node : nodes ) {
      node.inheritIO();
      node.start();
    }
    TestUtil.stall_till_cloudsize(nodes.size() + 1);

    File f = new File("smalldata/covtype/covtype.20k.data");
    // File f = new File("../../aaaa/datasets/millionx7_logreg.data.gz");
    // File f = new File("smalldata/test/rmodels/iris_x-iris-1-4_y-species_ntree-500.rdata");
    // File f = new File("py/testdir_single_jvm/syn_datasets/hastie_4x.data");
    Key key = TestUtil.load_test_file(f, "test");
    Key dest = Key.make("test.hex");
    ParseDataset.parse(dest, DKV.get(key));
  }
}
