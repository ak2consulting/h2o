package water;

import hex.DGLM.*;
import hex.DGLM;
import hex.DLSM.*;
import hex.NewRowVecTask.DataFrame;
import org.junit.*;

public class CoreTest extends TestUtil {

  @BeforeClass public static void stall() { stall_till_cloudsize(2); }

  static int RUNTIME=30*1000;
  static int MAPTIME=1000;

  @Test public void testCPULoad() {
    int jobs = H2O.NUMCPUS*RUNTIME/MAPTIME;
    // Target all keys remotely: the bug is that the *remote* JVM loses a core
    // to the DRemoteTask.
    //H2O cloud = H2O.CLOUD;
    //H2ONode target = cloud._memary[0];
    //if( target == H2O.SELF ) target = cloud._memary[1];
    Key[] keys = new Key[jobs];
    for( int i=0; i<keys.length; i++ )
      //keys[i] = Key.make("CPU"+i,(byte)1,Key.DFJ_INTERNAL_USER,target);
      keys[i] = Key.make("CPU"+i);
    long start = System.currentTimeMillis();
    //FJPacket fjp = new FJPacket();
    //FJPNorm.submit(fjp);
    new CPULoad().invoke(keys);
    long now=System.currentTimeMillis();
    Log.unwrap(System.err,"Runtime= "+(now-start)+" Jobs="+jobs+" maptime="+MAPTIME);
  }

  public static class CPULoad extends MRTask {
    double _sum;
    @Override public void map( Key key ) {
      //Log.unwrap(System.err,"S key "+key);
      long start = System.currentTimeMillis();
      long stop = start+MAPTIME;
      long now;
      while( (now=System.currentTimeMillis()) < stop )
        _sum += Math.sqrt(now);
      //Log.unwrap(System.err,"D key "+key+" sum="+_sum);
    }
    @Override public void reduce( DRemoteTask drt ) {
      CPULoad cpu = (CPULoad)drt;
      _sum += cpu._sum;
      //Log.unwrap(System.err,"reduce "+_lo+"-"+_hi+" vs "+cpu._lo+"-"+cpu._hi);
    }
  }

  // ----------------------------------------------------
  public static void runGLMTest( DataFrame data, LSMSolver lsm, GLMParams glmp) {
    GLMModel m = DGLM.startGLMJob(data, lsm, glmp, null, 0, true).get();
    if(m != null)
      m.remove();
  }

  //@Test 
  public void testProstate(){
    //Key k = loadAndParseKey("h.hex","smalldata/logreg/100kx7_logreg.data.gz");
    Key k = loadAndParseKey("h.hex","../datasets/millionx7_logreg.data.gz");
    try{
      ValueArray ary = DKV.get(k).get();
      int[] cols= new int[ary._cols.length];
      for( int i=0; i<cols.length; i++ ) cols[i]=i;
      DataFrame data = DGLM.getData(ary, cols, null, true);

      long start = System.currentTimeMillis();
      for( int i=0; i<100; i++ ) {
        runGLMTest(data, new ADMMSolver(0,0), new GLMParams(Family.binomial));
        Log.unwrap(System.err,""+((double)((System.currentTimeMillis()-start))/(i+1.0))+"ms");
      }

    } finally {
      UKV.remove(k);
    }
  }
}
