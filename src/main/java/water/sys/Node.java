package water.sys;

import java.io.IOException;

public interface Node {
  String address();

  void persistIO(String outFile, String errFile) throws IOException;

  void start();

  boolean isAlive();

  int waitFor();

  void kill();
}
