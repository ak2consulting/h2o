package water.sys;

import water.Boot;
import water.Log;

/**
 * Creates a node in a VM.
 */
public class NodeVM extends VM implements Node {
  private String _out, _err;

  public NodeVM(String[] args) {
    this(null, args);
  }

  public NodeVM(String[] javaArgs, String[] nodeArgs) {
    super(javaArgs, nodeArgs);
  }


  public static void main(String[] args) throws Exception {
    exitWithParent();
    Boot.main(args);
  }

  public static String localIP() {
    return Log.HOST; // TODO temp
  }
}