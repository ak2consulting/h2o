package water;
import java.io.*;
import java.lang.Cloneable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import jsr166y.*;

// *DISTRIBUTED* RemoteTask
// Execute a set of Keys on the home for each Key.
// Limited to doing a map/reduce style.

// @author <a href="mailto:cliffc@0xdata.com"></a>
// @version 1.0
public abstract class DRemoteTask extends RemoteTask implements Cloneable {

  // Some useful fields for *local* execution.  These fields are never passed
  // over the wire, and so do not need to be in the users' read/write methods.
  private Key[] _keys;           // Keys to work on
  private int _lo, _hi;          // Range of keys to work on
  private DRemoteTask _left, _rite; // In-progress execution tree

  // Run some useful function over this *local* key, and record the results in
  // the 'this' DRemoteTask.
  abstract public void map( Key key );
  // Combine results from 'drt' into 'this' DRemoteTask
  abstract public void reduce( DRemoteTask drt );

  void ps(String msg) {
    Class clz = getClass();
    System.err.println(msg+clz+" keys["+_keys.length+"] _lo="+_lo+" _hi="+_hi);
  }

  // Make a copy of thyself, but set the (otherwise final) completer field.
  private final DRemoteTask copy_self( DRemoteTask completer ) {
    try {
      DRemoteTask dt = (DRemoteTask)this.clone();
      dt.setCompleter(completer); // Set completer, what used to be a final field
      dt._left = dt._rite = null;
      dt.setPendingCount(0);      // Volatile write for completer field; reset pending count also
      return dt;
    } catch( CloneNotSupportedException e ) { throw new Error(e); }
  }
  private final DRemoteTask make_child() { return copy_self(this); }

  // Do all the keys in the list associated with this Node.  Roll up the
  // results into *this* DRemoteTask.
  public final void compute() {
    if( _hi-_lo >= 2 ) { // Multi-key case: just divide-and-conquer down to 1 key
      final int mid = (_lo+_hi)>>>1; // Mid-point
      _left = make_child();     // Clone by any other name
      _rite = make_child();     // Clone by any other name
      _left._hi = mid;          // Reset mid-point
      _rite._lo = mid;          // Also set self mid-point
      setPendingCount(2);       // Two more pending forks
      _left.fork();             // Runs in another thread/FJ instance
      _rite.fork();             // Runs in another thread/FJ instance
    } else {
      if( _hi > _lo )           // Single key?
        map(_keys[_lo]);        // Get it, run it locally
    }
    tryComplete();              // And this task is complete
  }

  @Override public final void onCompletion( CountedCompleter caller ) {
    // Reduce results into 'this' so they collapse going up the execution tree.
    // NULL out child-references so we don't accidentilly keep large subtrees
    // alive: each one may be holding large partial results.
    if( _left != null ) reduce(_left); _left = null;
    if( _rite != null ) reduce(_rite); _rite = null;
  }

  // Top-level remote execution hook.  The Key is an ArrayLet or an array of
  // Keys; start F/J'ing on individual keys.  Blocks.
  public void invoke( Key args ) {
    invoke(flatten_keys(args)); // Convert to array-of-keys and invoke
  }

  public void invoke( Key[] args ) {
    fork(args).get();        // Block until the job is done
  }

  // Top-level remote execution hook.  The Key is an ArrayLet or an array of
  // Keys; start F/J'ing on individual keys.  Non-blocking.
  public Future fork( Key args ) {
    return fork(flatten_keys(args));
  }

  // Top-level remote-execution hook.  Non-blocking.  Fires off jobs to remote
  // machines based on Keys.
  public Future fork( Key[] keys ) {
    // Split out the keys into disjointly-homed sets of keys.
    // Find the split point.  First find the range of home-indices.
    H2O cloud = H2O.CLOUD;
    int lo=cloud._memary.length, hi=-1;
    for( Key k : keys ) {
      int i = k.home(cloud);
      if( i<lo ) lo=i;
      if( i>hi ) hi=i;        // lo <= home(keys) <= hi
    }

    // Classic fork/join, but on CPUs.
    // Split into 3 arrays of keys: lo keys, hi keys and self keys
    final ArrayList<Key> locals = new ArrayList();
    final ArrayList<Key> lokeys = new ArrayList();
    final ArrayList<Key> hikeys = new ArrayList();
    int self_idx = cloud.nidx(H2O.SELF);
    int mid = (lo+hi)>>>1;    // Mid-point
    for( Key k : keys ) {
      int idx = k.home(cloud);
      if( idx == self_idx ) locals.add(k);
      else if( idx < mid )  lokeys.add(k);
      else                  hikeys.add(k);
    }

    // Launch off 2 tasks for the other sets of keys, and get a place-holder
    // for results to block on.
    Future f = new Future(this, remote_compute(lokeys), remote_compute(hikeys));

    // Setup for local recursion: just use the local keys.
    _keys = locals.toArray(new Key[locals.size()]); // Keys, including local keys (if any)
    _lo = 0;                    // Init the range of local keys
    _hi = _keys.length;
    if( _keys.length == 0 ) {   // Shortcut for no local work
      tryComplete();            // Indicate the local task is done
      return f;                 // But return the set of remote tasks
    }

    H2O.FJP_NORM.submit(this); // F/J non-blocking invoke
    // Return a cookie to block on
    return f;
  }

  // Junk class only used to allow blocking all results, both local & remote
  public static class Future {
    private final DRemoteTask _drt;
    private final TaskRemExec<DRemoteTask> _lo, _hi;
    Future(DRemoteTask drt, TaskRemExec<DRemoteTask> lo, TaskRemExec<DRemoteTask> hi ) {
      _drt = drt;  _lo = lo;  _hi = hi;
    }
    // Block until completed, without having to catch exceptions
    public DRemoteTask get() {
      try { _drt.get(); }   // block until complete
      catch( InterruptedException ex ) { throw new Error(ex); }
      catch(   ExecutionException ex ) { throw new Error(ex); }
      // Block for remote exec & reduce results into _drt
      if( _lo != null ) _drt.reduce(_lo.get());
      if( _hi != null ) _drt.reduce(_hi.get());
      return _drt;
    }
  };

  private final TaskRemExec remote_compute( ArrayList<Key> keys ) {
    if( keys.size() == 0 ) return null;
    H2O cloud = H2O.CLOUD;
    Key arg = keys.get(0);
    H2ONode target = cloud._memary[arg.home(cloud)];
    // Optimization: if sending just 1 key, and it is not a form which the
    // remote-side will expand, then send just the key, instead of a
    // key-of-keys containing 1 key.
    if( keys.size() ==1 && arg._kb[0] != Key.KEY_OF_KEYS && !arg.user_allowed() )
      return new TaskRemExec(target,make_child(),arg);

    arg = Key.make(UUID.randomUUID().toString(),(byte)0,Key.KEY_OF_KEYS,target);
    byte[] bits = new byte[8*keys.size()];
    int off = 4;                // Space for count of keys
    for( Key k : keys ) {       // Flatten to a byte array of keys
      while( k.wire_len() + off > bits.length )
        bits = Arrays.copyOf(bits,bits.length<<1);
      off = k.write(bits,off);
    }
    UDP.set4(bits,0,keys.size()); // Key count in the 1st 4 btyes
    Value vkeys = new Value(arg,Arrays.copyOf(bits,off));
    // Fork remotely and do not block for it
    return new TaskRemExec(target,make_child(),arg,vkeys);
  }

  private final Key[] flatten_keys( Key args ) {
    Value val = DKV.get(args);
    if( args._kb[0] == Key.KEY_OF_KEYS ) { // Key-of-keys: break out into array of keys
      if( val == null ) {
        System.err.println("Missing args in fork call: possibly the caller did not fence out a DKV.put(args) before calling "+(getClass().toString())+".fork(,,args,) with key "+args);
        throw new Error("Missing args");
      }
      // Parse all the keys out
      byte[] buf = val.get();
      int off = 0;
      int klen = UDP.get4(buf,off); off += 4;
      Key[] keys = new Key[klen];
      for( int i=0; i<klen; i++ ) {
        Key k = keys[i] = Key.read(buf,off);
        off += k.wire_len();
      }
      return keys;
    }

    // Arraylet: expand into the chunk keys
    if( val instanceof ValueArray ) {
      ValueArray ary = (ValueArray)val;
      Key[] keys = new Key[(int)ary.chunks()];
      for( int i=0; i<keys.length; i++ )
        keys[i] = ary.chunk_get(i);
      return keys;
    }
    // Handy wrap single key into a array-of-1 key
    return new Key[]{args};
  }
}
