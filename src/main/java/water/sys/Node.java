package water.sys;

public interface Node {
  void start();

  boolean isAlive();

  int waitFor();

  void kill();
}
