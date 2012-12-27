package test;
import static org.junit.Assert.assertEquals;
import com.google.gson.*;
import hex.LinearRegression;
import hex.GLMSolver;
import hex.LSMSolver;
import java.util.Map;
import java.util.Random;
import org.junit.*;
import water.*;
import water.parser.ParseDataset;

// A series of tests designed to validate GLM's *statistical results* and not,
// i.e. correct behavior when handed bad/broken/null arguments (although those
// tests are also good).  

public class GLMTest extends TestUtil {

  JsonObject computeGLMlog( LSMSolver lsms, ValueArray va ) {
    return computeGLM( GLMSolver.Family.binomial, lsms, va); }

  JsonObject computeGLM( GLMSolver.Family family, LSMSolver lsms, ValueArray va ) {
    // All columns in order, and use last as response variable
    int[] cols= new int[va._cols.length];
    for( int i=0; i<cols.length; i++ ) cols[i]=i;

    // Now a Gaussian GLM model for the same thing
    GLMSolver.GLMParams glmp = new GLMSolver.GLMParams();
    glmp._f = family;
    glmp._l = glmp._f.defaultLink;
    glmp._familyArgs = glmp._f.defaultArgs;
    glmp._betaEps = 0.00001;
    glmp._maxIter = 100;
    glmp._expandCat = false;
    // Solver
    GLMSolver glms = new GLMSolver(lsms,glmp);
    // Solve it!
    GLMSolver.GLMModel m = glms.computeGLM(va, cols, null);
    JsonObject glm = m.toJson();
    return glm;
  }

  // ---
  // Test GLM on a simple dataset that has an easy Linear Regression.
  @Test public void testLinearRegression() {
    Key datakey = Key.make("datakey");
    try {
      // Make some data to test with.
      // Equation is: y = 0.1*x+0
      ValueArray va = 
        va_maker(datakey,
                 new byte []{  0 ,  1 ,  2 ,  3 ,  4 ,  5 ,  6 ,  7 ,  8 ,  9 },
                 new float[]{0.0f,0.1f,0.2f,0.3f,0.4f,0.5f,0.6f,0.7f,0.8f,0.9f});
      // Columns to solve over; last column is the result column
      int[] cols = new int[]{0,1};
      
      // Compute LinearRegression between columns 0 & 1
      JsonObject lr = LinearRegression.run(va,0,1);
      assertEquals( 0.0, lr.get("Beta0"   ).getAsDouble(), 0.000001);
      assertEquals( 0.1, lr.get("Beta1"   ).getAsDouble(), 0.000001);
      assertEquals( 1.0, lr.get("RSquared").getAsDouble(), 0.000001);

      LSMSolver lsms = LSMSolver.makeSolver(); // Default normalization of NONE
      JsonObject glm = computeGLM(GLMSolver.Family.gaussian,lsms,va);    // Solve it!
      JsonObject coefs = glm.get("coefficients").getAsJsonObject();
      assertEquals( 0.0, coefs.get("Intercept").getAsDouble(), 0.000001);
      assertEquals( 0.1, coefs.get("0")        .getAsDouble(), 0.000001);

    } finally {
      UKV.remove(datakey);
    }
  }

  // Now try with a more complex binomial regression
  @Test public void testLogReg_Basic() {
    Key datakey = Key.make("datakey");
    try {
      // Make some data to test with.  2 columns, all numbers from 0-9
      ValueArray va = va_maker(datakey,2,10, new DataExpr() {
          double expr( byte[] x ) { return 1.0/(1.0+Math.exp(-(0.1*x[0]+0.3*x[1]-2.5))); } } );

      LSMSolver lsms = LSMSolver.makeSolver(); // Default normalization of NONE
      JsonObject glm = computeGLMlog(lsms,va); // Solve it!
      JsonObject coefs = glm.get("coefficients").getAsJsonObject();
      assertEquals(-2.5, coefs.get("Intercept").getAsDouble(), 0.000001);
      assertEquals( 0.1, coefs.get("0")        .getAsDouble(), 0.000001);
      assertEquals( 0.3, coefs.get("1")        .getAsDouble(), 0.000001);

    } finally {
      UKV.remove(datakey);
    }
  }

  // Compute the 'expr' result from the sum of coefficients,
  // plus a small random value.
  public static class DataExpr_Dirty extends DataExpr {
    final Random _R;
    final double _coefs[];
    DataExpr_Dirty( Random R, double[] coefs ) { _R = R; _coefs = coefs; }
    double expr( byte[] cols ) {
      double sum = _coefs[_coefs.length-1]+
        (_R.nextDouble()-0.5)/1000.0; // Add some noise
      for( int i = 0; i< cols.length; i++ )
        sum += cols[i]*_coefs[i];
      return 1.0/(1.0+Math.exp(-sum));
    }
  }

  @Test public void testLogReg_Dirty() {
    Key datakey = Key.make("datakey");
    try {
      Random R = new Random(0x987654321L);
      for( int i=0; i<10; i++ ) {
        double[] coefs = new double[] { R.nextDouble(),R.nextDouble(),R.nextDouble() };
        ValueArray va = va_maker(datakey,2,10, new DataExpr_Dirty(R, coefs));

        LSMSolver lsms = LSMSolver.makeSolver(); // Default normalization of NONE
        JsonObject glm = computeGLMlog(lsms,va); // Solve it!
        JsonObject res = glm.get("coefficients").getAsJsonObject();
        assertEquals(coefs[0], res.get("0")        .getAsDouble(), 0.001);
        assertEquals(coefs[1], res.get("1")        .getAsDouble(), 0.001);
        assertEquals(coefs[2], res.get("Intercept").getAsDouble(), 0.001);
        UKV.remove(datakey);
      }
    } finally {
      UKV.remove(datakey);
    }
  }

  @Test public void testLogReg_Penalty() {
    Key datakey = Key.make("datakey");
    try {
      // Make some data to test with.  2 columns, all numbers from 0-9
      ValueArray va = va_maker(datakey,2,10, new DataExpr() {
          double expr( byte[] x ) { return 1.0/(1.0+Math.exp(-(0.1*x[0]+0.3*x[1]-2.5))); } } );

      // No penalty
      LSMSolver lsms0 = LSMSolver.makeSolver();
      JsonObject glm = computeGLMlog(lsms0,va); // Solve it!
      JsonObject coefs = glm.get("coefficients").getAsJsonObject();
      assertEquals(-2.5, coefs.get("Intercept").getAsDouble(), 0.00001);
      assertEquals( 0.1, coefs.get("0")        .getAsDouble(), 0.000001);
      assertEquals( 0.3, coefs.get("1")        .getAsDouble(), 0.000001);

      // L1 penalty
      LSMSolver lsms1 = LSMSolver.makeL1Solver(LSMSolver.DEFAULT_LAMBDA,
                                               LSMSolver.DEFAULT_RHO,
                                               LSMSolver.DEFAULT_ALPHA);
      glm = computeGLMlog(lsms1,va); // Solve it!
      coefs = glm.get("coefficients").getAsJsonObject();
      assertEquals(-2.5, coefs.get("Intercept").getAsDouble(), 0.00001);
      assertEquals( 0.1, coefs.get("0")        .getAsDouble(), 0.000001);
      assertEquals( 0.3, coefs.get("1")        .getAsDouble(), 0.000001);
      
      // L2 penalty
      LSMSolver lsms2 = LSMSolver.makeL2Solver(LSMSolver.DEFAULT_LAMBDA);
      glm = computeGLMlog(lsms2,va); // Solve it!
      coefs = glm.get("coefficients").getAsJsonObject();
      assertEquals(-2.5, coefs.get("Intercept").getAsDouble(), 0.00001);
      assertEquals( 0.1, coefs.get("0")        .getAsDouble(), 0.000001);
      assertEquals( 0.3, coefs.get("1")        .getAsDouble(), 0.000001);

      // ELASTIC penalty
      LSMSolver lsmsx = LSMSolver.makeElasticNetSolver(LSMSolver.DEFAULT_LAMBDA,
                                                       LSMSolver.DEFAULT_LAMBDA2,
                                                       LSMSolver.DEFAULT_RHO,
                                                       LSMSolver.DEFAULT_ALPHA);
      glm = computeGLMlog(lsmsx,va); // Solve it!
      coefs = glm.get("coefficients").getAsJsonObject();
      assertEquals(-2.5, coefs.get("Intercept").getAsDouble(), 0.00001);
      assertEquals( 0.1, coefs.get("0")        .getAsDouble(), 0.000001);
      assertEquals( 0.3, coefs.get("1")        .getAsDouble(), 0.000001);

    } finally {
      UKV.remove(datakey);
    }
  }
}