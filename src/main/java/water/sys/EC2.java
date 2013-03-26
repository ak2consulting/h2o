package water.sys;

import java.io.*;
import java.util.*;

import org.apache.commons.codec.binary.Base64;

import water.H2O;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;

public abstract class EC2 {
  private static final String USER = System.getProperty("user.name");
  private static final String NAME = USER + "-H2O-Cloud";

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
      // request.withPlacement(new Placement(region + "c")); // All in same Availability Zone
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