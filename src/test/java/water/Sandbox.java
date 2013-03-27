package water;

import java.io.File;
import java.util.*;

import water.parser.ParseDataset;
import water.sys.*;
import water.util.Utils;

public class Sandbox {
  public static void main(String[] args) throws Exception {
    System.out.println("called: " + Arrays.toString(args));
//    TestUtil.stall_till_cloudsize(nodes.size() + 1);
//
//    // ArrayList<Node> nodes = new ArrayList<Node>();
//    // int count = 1 + 2;
//    //
//    // for( int i = 0; i < count - 1; i++ ) {
//    // // sites.add(new NodeCL(args));
//    // // sites.add(new NodeVM(args));
//    //
//    // Host host = new Host("192.168.1.15" + (i + 1));
//    // nodes.add(new NodeHost(host, null, args));
//    // }
//    //
//    // for( Node node : nodes )
//    // node.start();
//
//    File f = new File("smalldata/covtype/covtype.20k.data");
//    // File f = new File("../../aaaa/datasets/millionx7_logreg.data.gz");
//    // File f = new File("smalldata/test/rmodels/iris_x-iris-1-4_y-species_ntree-500.rdata");
//    // File f = new File("py/testdir_single_jvm/syn_datasets/hastie_4x.data");
//    Key key = TestUtil.load_test_file(f, "test");
//    Key dest = Key.make("test.hex");
//    ParseDataset.parse(dest, new Key[] { key });
  }
}
