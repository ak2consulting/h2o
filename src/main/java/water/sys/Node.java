package water.sys;

import java.io.IOException;

public interface Node {
  String address();

  /**
   * Display out and err on parent's console with a header for each line.
   */
  void inheritIO();

  void persistIO(String outFile, String errFile) throws IOException;

  void start();

  boolean isAlive();

  int waitFor();

  void kill();
}
