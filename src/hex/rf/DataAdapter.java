package hex.rf;

import java.text.DecimalFormat;
import java.util.*;

import jsr166y.RecursiveAction;
import water.MemoryManager;
import water.ValueArray;

import com.google.common.primitives.Ints;

class DataAdapter  {
  private final int _numClasses;
  private final String[] _columnNames;
  private final C[] _c;
  private final ValueArray _ary;
  /** Unique cookie identifying this dataset*/
  private final int _dataId;
  private final int _seed;
  public final int _classIdx;
  public final int _numRows;

  //public static final int MAX_BIN_LOG = 2;
  /** Maximum arity for a column (not a hard limit at this point) */
  static final short BIN_LIMIT = 1024;

  DataAdapter(ValueArray ary, int classCol, int[] ignores, int rows,
      int data_id, int seed) {
    _seed = seed+data_id;
    _ary = ary;
    _columnNames = ary.col_names();
    _c = new C[_columnNames.length];

    _numClasses = (int)(ary.col_max(classCol) - ary.col_min(classCol))+1;
    assert 0 <= _numClasses && _numClasses < 65535;

    _classIdx = classCol;
    assert ignores.length < _columnNames.length;
    for( int i = 0; i < _columnNames.length; i++ ) {
      boolean ignore = Ints.indexOf(ignores, i) > 0;
      double range = _ary.col_max(i) - _ary.col_min(i);
      boolean raw = (_ary.col_size(i) > 0 && range < BIN_LIMIT && _ary.col_max(i) >= 0); //TODO do it for negative columns as well
      C.ColType t = C.ColType.SHORT;
      if(raw && range == 1)t = C.ColType.BOOL;
      else if(raw && range <= Byte.MAX_VALUE)t = C.ColType.BYTE;
      _c[i]= new C(_columnNames[i], rows, i==_classIdx, t, !raw, ignore);
      if(raw){
        _c[i]._smax = (short)range;
        _c[i]._min = (float)_ary.col_min(i);
        _c[i]._max = (float)_ary.col_max(i);
      }
    }
    _dataId = data_id;
    _numRows = rows;
  }

  /** Given a value in enum format, returns a value in the original range. */
  public float unmap(int col, int v){  // FIXME should this be a short???? JAN
    short idx = (short)v; // Convert split-point of the form X.5 to a (short)X
    C c = _c[col];
    if ( !c._bin ) return v + c._min;

    if (v == idx) {  // this value isn't a split
      return c._binned2raw[idx+0];
    } else {
      float flo = c._binned2raw[idx+0]; // Convert to the original values
      float fhi = (idx < _numRows) ? c._binned2raw[idx+1] : flo+1.0f;
      float fmid = (flo+fhi)/2.0f; // Compute an original split-value
      assert flo < fmid && fmid < fhi; // Assert that the float will properly split
      return fmid;
    }
  }

  /** Return the name of the data set. */
  public String name() { return _ary._key.toString(); }

  /** Encode the data in a compact form.*/
  public ArrayList<RecursiveAction> shrinkWrap() {
    ArrayList<RecursiveAction> res = new ArrayList(_c.length);
    for( final C c : _c ) {
      if( c.ignore() || !c._bin) continue;
      res.add(new RecursiveAction() {
        protected void compute() {
          c.shrink();
        };
      });
    }
    return res;
  }

  public void shrinkColumn(int col){_c[col].shrink();}

  public int seed()           { return _seed; }
  public int columns()        { return _c.length;}
  public int classOf(int idx) { return getEncodedColumnValue(idx,_classIdx); }
  public int dataId()         { return _dataId; }
  /** The number of possible prediction classes. */
  public int classes()        { return _numClasses; }
  /** True if we should ignore column i. */
  public boolean ignore(int i)     { return _c[i].ignore(); }

  /** Returns the number of bins, i.e. the number of distinct values in the column.  Zero if we are ignoring the column. */
  public int columnArity(int col) { return ignore(col) ? 0 : _c[col]._smax; }

  /** Return a short that represents the binned value of the original row,column value.  */
  public short getEncodedColumnValue(int rowIndex, int colIndex) { return _c[colIndex].getValue(rowIndex);}

  /** Return the array of all column names including ignored and class. */
  public String[] columnNames() { return _columnNames; }

  public void addValueRaw(float v, int row, int col){
    _c[col].addRaw(v, row);
  }


  public void addValue(short v, int row, int col){
    _c[col].setValue(row,v);
  }

  public void addValue(float v, int row, int col){
    // Find the bin value by lookup in _bin2raw array which is sorted so we can do binary lookup.
    // The index returned is - length - 1 in case the value
    int idx = Arrays.binarySearch(_c[col]._binned2raw,v);
    if(idx < 0)idx = -idx - 1;
    if(idx >= _c[col]._smax)System.err.println("unexpected sv = " + idx);
    // the array lookup can return the lengthof the array in case the value would be > max,
    // which should (does) not happen right now, but just in case for the future, cap it to the max bin value)
    _c[col].setValue(row, (short)Math.min(_c[col]._smax-1,idx));
  }

  /** Add a row to this data set. */
  public void addRow(float[] v, int row) {
    for( int i = 0; i < v.length; i++ ) _c[i].addRaw(v[i], row);
  }

  static final DecimalFormat df = new  DecimalFormat ("0.##");

  public boolean binColumn(int col){
    return _c[col]._bin;
  }

  public void printBinningInfo(){
    for(int i = 0; i < _c[0]._n; ++i){
      for(int j = 0; j < _c.length; ++j)
        System.out.print(_c[j].getValue(i) + "(" +  unmap(j,_c[j].getValue(i)) + ") ");
      System.out.println();
    }
  }

  private static class C {
    enum ColType {BOOL,BYTE,SHORT};
    ColType _ctype;
    String _name;
    boolean _ignore, _isClass, _bin;
    float _min=Float.MAX_VALUE, _max=Float.MIN_VALUE, _tot;
    short[] _binned;
    byte [] _bvalues;
    float[]  _raw;
    // TFloatIntHashMap _freq;
    float[] _binned2raw;
    BitSet _booleanValues;
    short _smax = -1;
    int _n;

    C(String s, int rows, boolean isClass, ColType t, boolean bin, boolean ignore) {
      _name = s;
      _isClass = isClass;
      _ignore = ignore;
      _bin = bin;
      _ctype = t;
      _n = rows;
      if(!_ignore){
        if(_bin){
          _raw = _bin?new float[rows]:null;
        } else {
          switch(_ctype){
          case BOOL:
            _booleanValues = new BitSet(rows);
            break;
          case BYTE:
            _bvalues = new byte[rows];
            break;
          case SHORT:
            _binned = new short[rows];
          }
        }
      }
    }

    public void setValue(int row, short s){
      switch(_ctype){
      case BOOL:
        if(s == 1)_booleanValues.set(row);
        break;
      case BYTE:
        if((byte)s != s){
          System.out.println((byte)s + " != " + s);
        }
        assert (byte)s == s;
        _bvalues[row] = (byte)s;
        break;
      case SHORT:
        _binned[row] = s;
      }
    }

    public short getValue(int i) {
      switch(_ctype){
      case BOOL:
        return (short)(_booleanValues.get(i)?1:0);
      case BYTE:
        return _bvalues[i];
      case SHORT:
        return _binned[i];
      }
      throw new Error("illegal column type " + _ctype);
    }

    void addRaw(float x, int row) {
      assert _bin;
      _min=Math.min(x,_min);
      _max=Math.max(x,_max);
      _tot+=x;
      _raw[row] = x;
    }


    boolean ignore() { return _ignore; }

    public String toString() {
      String res = "Column("+_name+")";
      if (ignore()) return res + " ignored!";
      res+= "  ["+DataAdapter.df.format(_min) +","+DataAdapter.df.format(_max)+"], avg=";
      res+= DataAdapter.df.format(_tot/_n) ;
      if (_isClass) res+= " CLASS ";
      return res;
    }

    /** For all columns except the classes - encode all floats as unique shorts.
     *  For the column holding the classes - encode it as 0-(numclasses-1).
     *  Sometimes the class allows a zero class (e.g. iris, poker) and sometimes
     *  it's one-based (e.g. covtype) or -1/+1 (arcene).   */
    void shrink() {
      if (ignore()) return;
      assert !_isClass;
      Arrays.sort(_raw);
      int ndups = 0;
      int i = 0;
      // count dups
      while(i < _raw.length-1){
        int j = i+1;
        while(j < _raw.length && _raw[i] == _raw[j]){
          ++ndups;
          ++j;
        }
        i = j;
      }
      int n = _raw.length - ndups;
      int rem = n % BIN_LIMIT;
      int maxBinSize = (n > BIN_LIMIT) ? (int)(n / BIN_LIMIT + Math.min(rem,1)) : 1;
      System.out.println("n = " + n + ", max bin size = " + maxBinSize);
      // Assign shorts to floats, with binning.
      _binned2raw = new float[Math.min(n, BIN_LIMIT)];
      _smax = 0;
      int cntCurBin = 1;
      _binned2raw[0] = _raw[0];
      for(i = 1; i < _raw.length; ++i) {
        if(_raw[i] == _binned2raw[_smax])continue; // remove dups
        if( ++cntCurBin > maxBinSize ) {
          if(rem > 0 && --rem == 0)--maxBinSize; // check if we can reduce the bin size
          ++_smax;
          cntCurBin = 1;
        }
        _binned2raw[_smax] = _raw[i];
      }
      ++_smax;
      if( n > BIN_LIMIT )
        Utils.pln(this + " this column's arity was cut from "+ n + " to " + _smax);
      _binned = MemoryManager.allocateMemoryShort(_n);
      _raw = null;
    }
  }
}
