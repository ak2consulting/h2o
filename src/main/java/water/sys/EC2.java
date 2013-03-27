package water.sys;

import java.io.*;
import java.util.*;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.ArrayUtils;

import water.*;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import com.google.gson.Gson;

public abstract class EC2 {
  private static final String USER = System.getProperty("user.name");
  private static final String NAME = USER + "-H2O-Cloud";

  public class Config {
    String   ec2_region = "us-east-1";
    String   ec2_type   = "m1.xlarge";
    int      ec2_count;
    String[] ec2_rsync_includes;
    String[] ec2_rsync_excludes;
  }

  /**
   * Can be invoked with args:<br>
   * - Config.json<br>
   * - Python file, or Java class name<br>
   * - Optional additional args
   */
  public static void main(String[] args) throws Exception {
    Gson json = new Gson();
    Config config = json.fromJson(new FileReader(args[0]), Config.class);
    Cloud c = resize(config.ec2_count, config.ec2_type, config.ec2_region);

    // Take first box as cloud master
    Host master = new Host(c.publicIPs()[0]);
    String[] includes = (String[]) ArrayUtils.addAll(Host.defaultIncludes(), config.ec2_rsync_includes);
    String[] excludes = (String[]) ArrayUtils.addAll(Host.defaultExcludes(), config.ec2_rsync_excludes);
    master.rsync(includes, excludes);

    // TODO parse Java args from config

    ArrayList<String> list = new ArrayList<String>();
    list.add("-mainClass");
    list.add(Master.class.getName());

    String hosts = "";
    for( String ip : c.privateIPs() )
      hosts += ip + ",";
    list.add("-hosts");
    list.add(hosts);

    list.addAll(Arrays.asList(args));
    RemoteRunner.launch(master, list.toArray(new String[0]));
  }

  /**
   * Runs on the first EC2 box.
   */
  public static class Master {
    public static void main(String[] args) throws Exception {
      VM.exitWithParent();

      ArrayList<Node> workers = new ArrayList<Node>();
      assert args[0].equals("-hosts");
      String[] ips = args[1].split(",");

      ArrayList<String> workersArgs = new ArrayList<String>();
      workersArgs.add(args[0]);
      workersArgs.add(args[1]);
      workersArgs.add("--log_headers");
      for( int i = 1; i < ips.length; i++ ) {
        Host host = new Host(ips[i]);
        workers.add(new NodeHost(host, null, workersArgs.toArray(new String[0])));
      }

      HashSet<Host> hosts = new HashSet<Host>();
      for( Node w : workers )
        if( w instanceof NodeHost )
          hosts.add(((NodeHost) w).host());
      Cloud.rsync(hosts.toArray(new Host[0]));

      for( Node w : workers ) {
        w.inheritIO();
        w.start();
      }

      ArrayList<String> list = new ArrayList<String>();
      list.add(args[0]);
      list.add(args[1]);
      // TODO interpret rest of config in Java?
      // args[2]
      list.add("--log_headers");
      H2O.main(list.toArray(new String[0]));

      if( args.length > 3 ) {
        TestUtil.stall_till_cloudsize(1 + workers.size());
        String main;
        String[] mainArgs;

        if( args[3].endsWith(".py") ) {
          main = Jython.class.getName();
          mainArgs = Arrays.copyOfRange(args, 3, args.length);
        } else {
          main = args[3];
          mainArgs = Arrays.copyOfRange(args, 4, args.length);
        }

        Class c = Boot._init.loadClass(main, true);
        c.getMethod("main", String[].class).invoke(null, (Object) mainArgs);
      }
    }
  }

  /**
   * Create or terminate EC2 instances. Uses their Name tag to find existing ones.
   *
   * @return Public IPs
   */
  public static Cloud resize(int count, String type, String region) throws IOException {
    AmazonEC2Client ec2 = new AmazonEC2Client(H2O.getAWSCredentials());
    ec2.setEndpoint("ec2." + region + ".amazonaws.com");
    DescribeInstancesResult describeInstancesResult = ec2.describeInstances();
    List<Reservation> reservations = describeInstancesResult.getReservations();
    List<Instance> instances = new ArrayList<Instance>();

    for( Reservation reservation : reservations ) {
      for( Instance instance : reservation.getInstances() ) {
        String ip = ip(instance);
        if( ip != null ) {
          String name = null;
          if( instance.getTags().size() > 0 )
            name = instance.getTags().get(0).getValue();
          if( NAME.equals(name) )
            instances.add(instance);
        }
      }
    }
    System.out.println("Found " + instances.size() + " EC2 instances.");

    if( instances.size() > count ) {
      for( int i = 0; i < instances.size() - count; i++ ) {
        // TODO terminate
      }
    } else if( instances.size() < count ) {
      RunInstancesRequest request = new RunInstancesRequest();
      request.withInstanceType(type);
      if( region.startsWith("us-east") )
        request.withImageId("ami-04cf5c6d");
      else
        request.withImageId("ami-a83210ed");

      request.withMinCount(count - instances.size()).withMaxCount(count - instances.size());
      request.withSecurityGroupIds("ssh");
      // TODO better way to have boxes in same availability zone?
      request.withPlacement(new Placement(region + "c"));
      request.withUserData(new String(Base64.encodeBase64(cloudConfig().getBytes())));

      RunInstancesResult runInstances = ec2.runInstances(request);
      ArrayList<String> ids = new ArrayList<String>();
      for( Instance instance : runInstances.getReservation().getInstances() )
        ids.add(instance.getInstanceId());

      System.out.println("Creating " + ids.size() + " EC2 instances.");
      List<Instance> created = wait(ec2, ids);
      System.out.println("Created " + created.size() + " EC2 instances.");
      instances.addAll(created);
    }

    String[] pub = new String[instances.size()];
    String[] prv = new String[instances.size()];
    for( int i = 0; i < instances.size(); i++ ) {
      pub[i] = instances.get(i).getPublicIpAddress();
      prv[i] = instances.get(i).getPrivateIpAddress();
    }
    System.out.println("EC2 public IPs: " + Arrays.toString(pub));
    System.out.println("EC2 private IPs: " + Arrays.toString(prv));
    return new Cloud(pub, prv);
  }

//@formatter:off
  private static String cloudConfig() {
    return "#cloud-config\n" +
      "apt_update: false\n" +
      "runcmd:\n" +
      " - grep -q 'fs.file-max = 524288' /etc/sysctl.conf || echo -e '\\nfs.file-max = 524288' >> /etc/sysctl.conf\n" +
      " - sysctl -w fs.file-max=524288\n" +
      " - echo -e '* soft nofile 524288\\n* hard nofile 524288' > /etc/security/limits.d/increase-max-fd.conf\n" +
      // " - echo -e 'iptables -t nat -A PREROUTING -p tcp --dport 80 -j REDIRECT --to-ports 8080' >> /etc/rc.d/rc.local\n" +
      // " - yum -y remove java-1.6.0-openjdk\n" +
      // " - yum -y install java-1.7.0-openjdk\n" +
      " - useradd " + USER + "\n" +
      " - mkdir -p /home/" + USER + "/.ssh" + "\n" +
      " - cd /home/" + USER + "\n" +
      " - echo -e '\\n" + USER + " ALL=(ALL) NOPASSWD:ALL\\n' >> /etc/sudoers\n" +
      " - wget http://h2o_rsync.s3.amazonaws.com/h2o_rsync.zip\n" +
      " - unzip h2o_rsync.zip\n" +
      " - chown -R " + USER + ':' + USER + " /home/" + USER + "\n" +
      " - echo '" + pubKey() + "' >> .ssh/authorized_keys\n" +
      "";
  }
//@formatter:on

  private static String pubKey() {
    BufferedReader r = null;
    try {
      String pub = System.getProperty("user.home") + "/.ssh/id_rsa.pub";
      r = new BufferedReader(new FileReader(new File(pub)));
      return r.readLine();
    } catch( IOException e ) {
      throw new RuntimeException(e);
    } finally {
      if( r != null )
        try {
          r.close();
        } catch( IOException e ) {
          throw new RuntimeException(e);
        }
    }
  }

  private static List<Instance> wait(AmazonEC2Client ec2, List<String> ids) {
    boolean tagsDone = false;
    for( ;; ) {
      try {
        if( !tagsDone ) {
          CreateTagsRequest createTagsRequest = new CreateTagsRequest();
          createTagsRequest.withResources(ids).withTags(new Tag("Name", NAME));
          ec2.createTags(createTagsRequest);
          tagsDone = true;
        }
        DescribeInstancesRequest request = new DescribeInstancesRequest();
        request.withInstanceIds(ids);
        DescribeInstancesResult result = ec2.describeInstances(request);
        List<Reservation> reservations = result.getReservations();
        List<Instance> instances = new ArrayList<Instance>();
        for( Reservation reservation : reservations )
          for( Instance instance : reservation.getInstances() )
            if( ip(instance) != null )
              instances.add(instance);
        if( instances.size() == ids.size() ) {
          // Try to connect to SSH port on each box
          if( canConnect(instances) )
            return instances;
        }
      } catch( AmazonServiceException _ ) {
      }
      try {
        Thread.sleep(500);
      } catch( InterruptedException e ) {
        throw new RuntimeException(e);
      }
    }
  }

  private static String ip(Instance instance) {
    String ip = instance.getPublicIpAddress();
    if( ip != null && ip.length() != 0 )
      if( instance.getState().getName().equals("running") )
        return ip;
    return null;
  }

  private static boolean canConnect(List<Instance> instances) {
    for( Instance instance : instances ) {
      try {
        String ssh = Host.ssh() + " -q" + Host.SSH_OPTS + " " + instance.getPublicIpAddress();
        Process p = Runtime.getRuntime().exec(ssh + " exit");
        if( p.waitFor() != 0 )
          return false;
      } catch( Exception e ) {
        return false;
      } finally {
      }
    }
    return true;
  }
}
