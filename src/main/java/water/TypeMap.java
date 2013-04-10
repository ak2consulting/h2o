package water;

import java.util.*;

public class TypeMap {
  static public final short NULL = (short) -1;
  static public final short PRIM_B = 1;
  static public final short VALUE_ARRAY;

  // Run main below to update TODO add as build step
  static private final String[] CLAZZES = {
    " BAD",                     // 0: BAD
    "[B",                       // 1: Array of Bytes
    "hex.ConfusionMatrix",
    "hex.Covariance$COV_Task",
    "hex.DGLM$GLMModel",
    "hex.DGLM$GLMParams",
    "hex.DGLM$GLMValidation",
    "hex.DGLM$GLMValidationFunc",
    "hex.DGLM$Gram",
    "hex.DGLM$GramMatrixFunc",
    "hex.DLSM$ADMMSolver",
    "hex.DLSM$GeneralizedGradientSolver",
    "hex.DLSM$LSMSolver",
    "hex.GLMGrid",
    "hex.GLMGrid$1",
    "hex.GLMGrid$GLMModels",
    "hex.GLMGrid$GridTask",
    "hex.Histogram$BinningTask",
    "hex.Histogram$Bins",
    "hex.Histogram$OutlineTask",
    "hex.KMeans$KMeansModel",
    "hex.KMeans$Lloyds",
    "hex.KMeans$Sampler",
    "hex.KMeans$Sqr",
    "hex.LinearRegression$CalcRegressionTask",
    "hex.LinearRegression$CalcSquareErrorsTasks",
    "hex.LinearRegression$CalcSumsTask",
    "hex.NOPTask",
    "hex.NewRowVecTask",
    "hex.NewRowVecTask$DataFrame",
    "hex.NewRowVecTask$RowFunc",
    "hex.Plot$Pixels",
    "hex.Plot$Res",
    "hex.RowVecTask",
    "hex.RowVecTask$Sampling",
    "hex.rf.Confusion",
    "hex.rf.DRF",
    "hex.rf.MinorityClasses$ClassExtractTask",
    "hex.rf.MinorityClasses$CountMClassRowsTask",
    "hex.rf.MinorityClasses$HistogramTask",
    "hex.rf.MinorityClasses$ReplicationTask",
    "hex.rf.MinorityClasses$UnbalancedClass",
    "hex.rf.RFModel",
    "hex.rf.Tree$1",
    "water.Atomic",
    "water.AtomicTest$Append$1",
    "water.AutoSerialTest",
    "water.BitsCmpTask",
    "water.DRemoteTask",
    "water.DTask",
    "water.Freezable",
    "water.H2ONode",
    "water.HeartBeat",
    "water.Iced",
    "water.Job",
    "water.Job$1",
    "water.Job$2",
    "water.Job$3",
    "water.Job$Fail",
    "water.Job$List",
    "water.KVTest$Atomic2",
    "water.KVTest$ByteHisto",
    "water.KVTest$RemoteBitSet",
    "water.Key",
    "water.Key$Ary",
    "water.Log$LogStr",
    "water.Log$Wrapper$1",
    "water.MRTask",
    "water.Model",
    "water.NOPTask",
    "water.TAtomic",
    "water.TaskGetKey",
    "water.TaskInvalidateKey",
    "water.TaskPutKey",
    "water.Value",
    "water.ValueArray",
    "water.ValueArray$Column",
    "water.exec.AddOperator",
    "water.exec.AndOperator",
    "water.exec.BooleanVectorFilter",
    "water.exec.DeepSingleColumnAssignment",
    "water.exec.DivOperator",
    "water.exec.EqOperator",
    "water.exec.GreaterOperator",
    "water.exec.GreaterOrEqOperator",
    "water.exec.Helpers$1",
    "water.exec.Helpers$ScallarCollector",
    "water.exec.Helpers$SigmaCalc",
    "water.exec.IifOperator",
    "water.exec.IifOperatorScalar2",
    "water.exec.IifOperatorScalar23",
    "water.exec.IifOperatorScalar3",
    "water.exec.InPlaceColSwap$ColSwapTask",
    "water.exec.LeftAdd",
    "water.exec.LeftAnd",
    "water.exec.LeftDiv",
    "water.exec.LeftEq",
    "water.exec.LeftGreater",
    "water.exec.LeftGreaterOrEq",
    "water.exec.LeftLess",
    "water.exec.LeftLessOrEq",
    "water.exec.LeftMod",
    "water.exec.LeftMul",
    "water.exec.LeftNeq",
    "water.exec.LeftOr",
    "water.exec.LeftSub",
    "water.exec.LessOperator",
    "water.exec.LessOrEqOperator",
    "water.exec.Log$MRLog",
    "water.exec.MRColumnProducer",
    "water.exec.MRVectorBinaryOperator",
    "water.exec.MRVectorTernaryOperator",
    "water.exec.MRVectorUnaryOperator",
    "water.exec.MakeEnum$GetEnumTask",
    "water.exec.MakeEnum$PackToEnumTask",
    "water.exec.Max$MRMax",
    "water.exec.Mean$MRMean",
    "water.exec.Min$MRMin",
    "water.exec.ModOperator",
    "water.exec.MulOperator",
    "water.exec.NeqOperator",
    "water.exec.OrOperator",
    "water.exec.ParametrizedMRVectorUnaryOperator",
    "water.exec.RandBitVect$RandVectBuilder",
    "water.exec.RightAnd",
    "water.exec.RightDiv",
    "water.exec.RightMod",
    "water.exec.RightOr",
    "water.exec.RightSub",
    "water.exec.SliceFilter",
    "water.exec.SubOperator",
    "water.exec.Sum$MRSum",
    "water.exec.TernaryWithScalarOperator",
    "water.exec.TernaryWithTwoScalarsOperator",
    "water.exec.UnaryMinus",
    "water.hdfs.TaskStore2HDFS",
    "water.parser.CheckParseSetup",
    "water.parser.DParseTask",
    "water.parser.DParseTask$AtomicUnion",
    "water.parser.Enum",
    "water.parser.ParseDataset",
    "water.parser.ParseDataset$2",
    "water.parser.ParseDataset$Progress",
    "water.parser.ParseDataset$UnzipTask",
    "water.store.s3.MultipartUpload",
    "water.store.s3.MultipartUpload$1",
    "water.store.s3.MultipartUpload$Progress",
    "water.util.FileIntegrityChecker",
    "water.util.JStackCollectorTask",
  };
  static private final HashMap<String,Integer> MAP = new HashMap();
  static {
    int va_id = -1;
    for( int i=0; i<CLAZZES.length; i++ ) {
      MAP.put(CLAZZES[i],i);
      if( CLAZZES[i].equals("water.ValueArray") ) va_id = i;
    }
    VALUE_ARRAY = (short)va_id; // Pre-cached the type id for ValueArray
  }

  static public int onLoad(String className) {
    Integer I = MAP.get(className);
    if(I == null)
      throw new RuntimeException("TypeMap missing " + className);
    return I;
  }

  static private final Freezable[] GOLD = new Freezable[CLAZZES.length];

  static public Iced newInstance(int id) {
    Iced f = (Iced)GOLD[id];
    if( f == null ) {
      try { GOLD[id] = f = (Iced) Class.forName(CLAZZES[id]).newInstance(); }
      catch( Exception e ) { throw new Error(e); }
    }
    return f.newInstance();
  }
  static public Freezable newFreezable(int id) {
    Freezable f = GOLD[id];
    if( f == null ) {
      try { GOLD[id] = f = (Freezable) Class.forName(CLAZZES[id]).newInstance(); }
      catch( Exception e ) { throw new Error(e); }
    }
    return f.newInstance();
  }

  static public String className(int id) { return CLAZZES[id]; }
  static public Class clazz(int id) {
    if( GOLD[id] == null ) newInstance(id);
    return GOLD[id].getClass();
  }

  //

  public static void main(String[] args) {
    ArrayList<String> list = new ArrayList<String>();
    for(String name : Boot.getClasses()) {
      try {
        Class c = Class.forName(name);
        if(Freezable.class.isAssignableFrom(c))
          list.add(c.getName());
      } catch(Throwable _) {
        System.out.println("Skipped: " + name);
      }
    }
    Collections.sort(list);
    for(String s : list)
      System.out.println("    \"" + s + "\",");
  }
}
