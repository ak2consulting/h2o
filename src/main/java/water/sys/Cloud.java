package water.sys;

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
}