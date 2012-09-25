package hexlytics;

import hexlytics.GLinearRegression.Row;


import water.*;
import Jama.Matrix;

/**
 * Logistic regression implementation, using iterative least squares method.
 *
 * Basically repeatedly calls GLR with custom input transformation until the fixpoint of the function is reached.
 * To compute the logistic regression using GLR, following operations are performed on the input:
 *
 *
 * @author tomasnykodym
 *
 */
public class LogisticRegression {
  public static class LogitMap extends GLinearRegression.LinearRow2VecMap{
    Matrix _beta;
    public LogitMap(){}
    public LogitMap(int[] xColIds, int yColId) {
      this(xColIds,yColId,new Matrix(xColIds.length+1,1));
    }
    public LogitMap(LogitMap other){
      super(other);
      _beta = (Matrix)other._beta.clone();
    }

    public LogitMap(int[] xColIds, int yColId, Matrix initialGuess) {
      super(xColIds, yColId);
      // make sure weighted x is separate object
      _beta = initialGuess;
    }

    @Override public Object clone(){
      return new LogitMap(this);
    }

    double gPrime(double p){
      if(p == 1 ||  p == 0) return 0;
      return 1 / (p*(1-p));
    }

    double g(double x){
      return Math.log(x)/Math.log(1-x);
    }

    double gInv(double x){
      return 1.0/(Math.exp(-x)+1.0);
    }

    @Override
    public Row map(int rid){
      Row r =  super.map(rid);
      assert 0 <= r.y && r.y <= 1;
      // transform input to the GLR according to Olga's slides
      // (glm lecture, page 12)
      // Step 1
      Matrix x = r.x.times(_beta);
      assert x.getRowDimension() == x.getColumnDimension();
      assert x.getRowDimension() == 1;
      double gmu = r.x.times(_beta).get(0, 0);
      double mu = gInv(gmu);
      // Step 2
      double vary = mu * (1 - mu);
      double dgmu = gPrime(mu);
      r.y = gmu + (r.y - mu)*dgmu;
      // compuet the weights (inverse of variance of z)
            double var = dgmu*dgmu*vary;
      r.wx.timesEquals(1/var);
      // step 3 performed by GLR
      return r;
    }

    @Override
    public int read(byte[] buf, int off) {
      off = super.read(buf, off);
      _beta = (Matrix)_row.wx.clone();
      for(int i = 0; i < _beta.getRowDimension(); ++i){
        _beta.set(i,0, UDP.get8d(buf, off)); off += 8;
      }
      return off;
    }
    @Override
    public int write(byte[] buf, int off) {
      off = super.write(buf, off);
      for(int i = 0; i < _beta.getRowDimension(); ++i){
        UDP.set8d(buf, off, _beta.get(i,0)); off += 8;
      }
      return off;
    }
  }
  static final String html_head = "<table>";

  static String getColName(int colId, ValueArray ary){
    String colName = ary.col_name(colId);
    if(colName == null) colName = "Column " + colId;
    return colName;
  }
  public static String web_main(ValueArray ary, int [] xColIds, int yColId){
    Matrix beta = solve(ary._key,xColIds,yColId);
    // add in the variable names
    StringBuilder bldr = new StringBuilder();
    bldr.append(html_head);
    bldr.append("<tr><td>Intercept</td>");
    for(int i = 0; i < xColIds.length; ++i)
      bldr.append("<td>" + getColName(xColIds[i], ary)  + "</td>");
    bldr.append("</tr><td>" +  beta.get(xColIds.length, 0));
    for(int i = 0; i < xColIds.length; ++i)
      bldr.append("<td>" + beta.get(i,0) + "</td>");
    bldr.append("</tr></table>");
    return bldr.toString();
  }

  public static Matrix solve(Key aryKey, int [] xColIds, int yColId) {
    // initial guess
    Matrix oldBeta = new Matrix(xColIds.length+1,1);
    // initial guess is all 1s
    Matrix newBeta = GLinearRegression.solveGLR(aryKey, new LogitMap(xColIds, yColId,oldBeta));
    Matrix diff = newBeta.minus(oldBeta);
    double norm = diff.transpose().times(diff).get(0, 0);

    while(norm > 1e-5){
      oldBeta = newBeta;
      newBeta = GLinearRegression.solveGLR(aryKey, new LogitMap(xColIds, yColId,oldBeta));
      diff = newBeta.minus(oldBeta);
      norm = diff.transpose().times(diff).get(0, 0);
    }
    return newBeta;
  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    throw new RuntimeException("TODO Auto-generated method stub");
  }

}
