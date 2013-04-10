package water;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import jsr166y.ForkJoinPool;
import water.H2O.FJWThr;
import water.H2O.H2OCountedCompleter;

/**
 * A remotely executed FutureTask.  Flow is:
 *
 * 1- Build a DTask (or subclass).  This object will be replicated remotely.
 * 2- Make a RPC object, naming the target Node.  Call (re)call().  Call get() to
 * block for result, or cancel() or isDone(), etc.
 * 3- DTask will be serialized and sent to the target; small objects via UDP
 * and large via TCP (using AutoBuffer & auto-gen serializers).
 * 4- An RPC UDP control packet will be sent to target; this will also contain
 * the DTask if its small enough.
 * 4.5- The network may replicate (or drop) the UDP packet.  Dups may arrive.
 * 4.5- Sender may timeout, and send dup control UDP packets.
 * 5- Target will capture a UDP packet, and begin filtering dups (via task#).
 * 6- Target will deserialize the DTask, and call DTask.invoke() in a F/J thread.
 * 6.5- Target continues to filter (and drop) dup UDP sends (and timeout resends)
 * 7- Target finishes call, and puts result in DTask.
 * 8- Target serializes result and sends to back to sender.
 * 9- Target sends an ACK back (may be combined with the result if small enough)
 * 10- Target puts the ACK in H2ONode.TASKS for later filtering.
 * 10.5- Target receives dup UDP request, then replies with ACK back.
 * 11- Sender receives ACK result; deserializes; notifies waiters
 * 12- Sender sends ACKACK back
 * 12.5- Sender recieves dup ACK's, sends dup ACKACK's back
 * 13- Target recieves ACKACK, removes TASKS tracking
 *
 * @author <a href="mailto:cliffc@0xdata.com"></a>
 * @version 1.0
 */

public class RPC<V extends DTask> implements Future<V>, Delayed, ForkJoinPool.ManagedBlocker {
  // The target remote node to pester for a response.  NULL'd out if the target
  // disappears or we cancel things (hence not final).
  H2ONode _target;

  // The distributed Task to execute.  Think: code-object+args while this RPC
  // is a call-in-progress (i.e. has an 'execution stack')
  final V _dt;

  // True if _dt contains the final answer
  volatile boolean _done;

  // A locally-unique task number; a "cookie" handed to the remote process that
  // they hand back with the response packet.  These *never* repeat, so that we
  // can tell when a reply-packet points to e.g. a dead&gone task.
  int _tasknum;

  // Time we started this sucker up.  Controls re-send behavior.
  final long _started;
  long _retry;                  // When we should attempt a retry

  // We only send non-failing TCP info once; also if we used TCP it was large
  // so duplications are expensive.  However, we DO need to keep resending some
  // kind of "are you done yet?" UDP packet, incase the reply packet got dropped
  // (but also in case the main call was a single UDP packet and it got dropped).
  // Not volatile because set under lock.
  boolean _sentTcp;

  // Magic Cookies
  static final byte SERVER_UDP_SEND = 10;
  static final byte SERVER_TCP_SEND = 11;
  static final byte CLIENT_UDP_SEND = 12;
  static final byte CLIENT_TCP_SEND = 13;
  static final private String[] COOKIES = new String[] {
    "SERVER_UDP","SERVER_TCP","CLIENT_UDP","CLIENT_TCP" };

  public static <DT extends DTask> RPC<DT> call(H2ONode target, DT dtask) {
    return new RPC(target,dtask).call();
  }

  // Make a remotely executed FutureTask.  Must name the remote target as well
  // as the remote function.  This function is expected to be subclassed.
  public RPC( H2ONode target, V dtask ) {
    this(target,dtask,1.0f);
    setTaskNum();
  }
  // Only used for people who optimistically make RPCs that get thrown away and
  // never sent over the wire.  Split out task# generation from RPC <init> -
  // every task# MUST be sent over the wires, because the far end tracks the
  // task#'s in a dense list (no holes).
  public RPC( H2ONode target, V dtask, float f ) {
    _target = target;
    _dt = dtask;
    _started = System.currentTimeMillis();
    _retry = RETRY_MS;
  }
  public RPC<V> setTaskNum() {
    assert _tasknum == 0;
    _tasknum = _target.nextTaskNum();
    return this;
  }

  // Make an initial RPC, or re-send a packet.  Always called on 1st send; also
  // called on a timeout.
  public synchronized RPC<V> call() {
    // Keep a global record, for awhile
    _target.taskPut(_tasknum,this);
    try {
      // We could be racing timeouts-vs-replies.  Blow off timeout if we have an answer.
      if( isDone() ) {
        _target.taskRemove(_tasknum);
        return this;
      }
      // Default strategy: (re)fire the packet and (re)start the timeout.  We
      // "count" exactly 1 failure: just whether or not we shipped via TCP ever
      // once.  After that we fearlessly (re)send UDP-sized packets until the
      // server replies.

      // Pack classloader/class & the instance data into the outgoing
      // AutoBuffer.  If it fits in a single UDP packet, ship it.  If not,
      // finish off the current AutoBuffer (which is now going TCP style), and
      // make a new UDP-sized packet.  On a re-send of a TCP-sized hunk, just
      // send the basic UDP control packet.
      if( !_sentTcp ) {
        // Ship the UDP packet!
        AutoBuffer ab = new AutoBuffer(_target).putTask(UDP.udp.exec,_tasknum);
        ab.put1(CLIENT_UDP_SEND).put(_dt);
        if( ab.hasTCP() ) _sentTcp = true;
        ab.close();
      } else {
        // Else it was sent via TCP in a prior attempt, and we've timed out.
        // This means the caller's ACK/answer probably got dropped and we need
        // him to resend it (or else the caller is still processing our
        // request).  Send a UDP reminder - but with the CLIENT_TCP_SEND flag
        // instead of the UDP send, and no DTask (since it previously went via
        // TCP, no need to resend it).
        AutoBuffer ab = new AutoBuffer(_target).putTask(UDP.udp.exec,_tasknum);
        ab.put1(CLIENT_TCP_SEND).close();
      }
      // Double retry until we exceed existing age.  This is the time to delay
      // until we try again.  Note that we come here immediately on creation,
      // so the first doubling happens before anybody does any waiting.  Also
      // note the generous 5sec cap: ping at least every 5 sec.
      _retry += (_retry < 5000 ) ? _retry : 5000;
      // Put self on the "TBD" list of tasks awaiting Timeout.
      // So: dont really 'forget' but remember me in a little bit.
      assert !UDPTimeOutThread.PENDING.contains(this);
      UDPTimeOutThread.PENDING.add(this);
      return this;
    } catch(Error t) {
      t.printStackTrace();
      throw t;
    }
  }

  // Similar to FutureTask.get() but does not throw any exceptions.  Returns
  // null for canceled tasks, including those where the target dies.
  public V get() {
    // check priorities - FJ task can only block on a task with higher priority!
    Thread cThr = Thread.currentThread();
    int priority = (cThr instanceof FJWThr) ? ((FJWThr)cThr)._priority : 0;
    assert _dt.priority() > priority || (_dt.priority() == priority && _dt instanceof DRemoteTask)
      : "*** Attempting to block on task (" + _dt.getClass() + ") with equal or lower priority. Can lead to deadlock! " + _dt.priority() + " <=  " + priority;
    if( _done ) return _dt; // Fast-path shortcut
    // Use FJP ManagedBlock for this blocking-wait - so the FJP can spawn
    // another thread if needed.
    try { ForkJoinPool.managedBlock(this); } catch( InterruptedException e ) { }
    if( _done ) return _dt; // Fast-path shortcut
    assert isCancelled();
    return null;
  }
  // Return true if blocking is unnecessary, which is true if the Task isDone.
  public boolean isReleasable() {  return isDone();  }
  // Possibly blocks the current thread.  Returns true if isReleasable would
  // return true.  Used by the FJ Pool management to spawn threads to prevent
  // deadlock is otherwise all threads would block on waits.
  public synchronized boolean block() {
    while( !isDone() ) { try { wait(); } catch( InterruptedException e ) { } }
    return true;
  }

  public final V get(long timeout, TimeUnit unit) {
    if( _done ) return _dt;     // Fast-path shortcut
    throw H2O.unimpl();
  }

  // Done if target is dead or canceled, or we have a result.
  public final boolean isDone() {  return _target==null || _done;  }
  // Done if target is dead or canceled
  public final boolean isCancelled() { return _target==null; }
  // Attempt to cancel job
  public final boolean cancel( boolean mayInterruptIfRunning ) {
    boolean did = false;
    synchronized(this) {        // Install the answer under lock
      if( !isCancelled() ) {
        did = true;             // Did cancel (was not cancelled already)
        _target = null;         // Flag as canceled
        UDPTimeOutThread.PENDING.remove(this);
        _target.taskRemove(_tasknum);
      }
      notifyAll();              // notify in any case
    }
    return did;
  }

  // ---
  // Handle the remote-side incoming UDP packet.  This is called on the REMOTE
  // Node, not local.  Wrong thread, wrong JVM.
  public static class RemoteHandler extends UDP {

    AutoBuffer call(AutoBuffer ab) {
      assert false:"task requests should be processed in RPC class now";
      throw H2O.unimpl();
    }

    // Pretty-print bytes 1-15; byte 0 is the udp_type enum
    public String print16( AutoBuffer ab ) {
      int flag = ab.getFlag();
      String clazz = (flag == CLIENT_UDP_SEND) ? TypeMap.className(ab.get2()) : "";
      return "task# "+ab.getTask()+" "+ clazz+" "+COOKIES[flag-SERVER_UDP_SEND];
    }
  }


  public static class RPCCall extends H2OCountedCompleter implements Delayed {
    volatile DTask _dt; // Set on construction, atomically set to null onAckAck
    final H2ONode _client;
    final int _tsknum;
    long _started;              // Retry fields for the ackack
    long _retry;
    volatile boolean _computed; // One time transition from false to true
    public RPCCall(DTask dt, H2ONode client, int tsknum) {
      _dt = dt;
      _client = client;
      _tsknum = tsknum;
      if( _dt == null ) _computed = true; // Only for Golden Completed Tasks (see H2ONode.java)
    }

    @Override public void compute2() {
      // Run the remote task on this server!
      _dt.invoke(_client);
      // Send results back
      AutoBuffer ab = new AutoBuffer(_client).putTask(UDP.udp.ack,_tsknum).put1(SERVER_UDP_SEND);
      _dt.write(ab);                 // Write the DTask - could be very large write
      _dt._repliedTcp = ab.hasTCP(); // Resends do not need to repeat TCP result
      _computed = true;              // After the TCP reply flag set, set computed bit
      ab.close();
      _client.record_task_answer(this); // Setup for retrying Ack & AckAck
    }
    // Re-send strictly the ack, because we're missing an AckAck
    public final void resend_ack() {
      assert _computed : "Found RPCCall not computed "+_tsknum;
      DTask dt = _dt;
      if( dt == null ) return;  // Received ACKACK already
      AutoBuffer rab = new AutoBuffer(_client).putTask(UDP.udp.ack,_tsknum);
      if( dt._repliedTcp ) rab.put1(RPC.SERVER_TCP_SEND) ; // Reply sent via TCP
      else        dt.write(rab.put1(RPC.SERVER_UDP_SEND)); // Reply sent via UDP
      rab.close();
      // Double retry until we exceed existing age.  This is the time to delay
      // until we try again.  Note that we come here immediately on creation,
      // so the first doubling happens before anybody does any waiting.  Also
      // note the generous 5sec cap: ping at least every 5 sec.
      _retry += (_retry < 5000 ) ? _retry : 5000;
    }
    @Override public byte priority() { return _dt.priority(); }
    // How long until we should do the "timeout" action?
    @Override public final long getDelay( TimeUnit unit ) {
      long delay = (_started+_retry)-System.currentTimeMillis();
      return unit.convert( delay, TimeUnit.MILLISECONDS );
    }
    // Needed for the DelayQueue API
    @Override public final int compareTo( Delayed t ) {
      RPCCall r = (RPCCall)t;
      long nextTime = _started+_retry, rNextTime = r._started+r._retry;
      return nextTime == rNextTime ? 0 : (nextTime > rNextTime ? 1 : -1);
    }
    static AtomicReferenceFieldUpdater<RPCCall,DTask> CAS_DT =
      AtomicReferenceFieldUpdater.newUpdater(RPCCall.class, DTask.class,"_dt");
  }

  // Handle traffic, from a client to this server asking for work to be done.
  // Called from either a F/J thread (generally with a UDP packet) or from the
  // TCPReceiver thread.
  static AutoBuffer remote_exec( final AutoBuffer ab ) {
    long lo = ab.get8(0), hi = ab.get8(8); // for dbg
    final int task = ab.getTask();
    final int flag = ab.getFlag();
    assert flag==CLIENT_UDP_SEND || flag==CLIENT_TCP_SEND; // Client-side send
    // Atomically record an instance of this task, one-time-only replacing a
    // null with an RPCCall, a placeholder while we work on a proper responce -
    // and it serves to let us discard dup UDP requests.
    RPCCall old = ab._h2o.has_task(task);
    // This is a UDP packet requesting an answer back for a request sent via
    // TCP but the UDP packet has arrived ahead of the TCP.  Just drop the UDP
    // and wait for the TCP to appear.
    if( old == null && flag == CLIENT_TCP_SEND ) {
      if(ab.hasTCP())TimeLine.printMyTimeLine();
      assert !ab.hasTCP():"ERROR: got tcp with existing task #, FROM " + ab._h2o.toString() + " AB: " +  UDP.printx16(lo,hi);      // All the resends should be UDP only
      // DROP PACKET
    } else if( old == null ) {  // New task?
      // Read the DTask Right Now.  If we are the TCPReceiver thread, then we
      // are reading in that thread... and thus TCP reads are single-threaded.
      RPCCall rpc = new RPCCall(ab.get(DTask.class),ab._h2o,task);
      RPCCall rpc2 = ab._h2o.record_task(rpc);
      if( rpc2==null ) {        // Atomically insert (to avoid double-work)
        H2O.submitTask(rpc);    // And execute!
      } else {                  // Else lost the task-insertion race
        if(ab.hasTCP())TimeLine.printMyTimeLine();
        assert !ab.hasTCP():"ERROR: got tcp with existing task #, FROM " + ab._h2o.toString() + " AB: " +  UDP.printx16(lo,hi);      // All the resends should be UDP only
        // DROP PACKET
      }

    } else if( !old._computed ) {
      // This packet has not been fully computed.  Hence it's still a work-in-
      // progress locally.  We have no answer to reply but we do not want to
      // re-offer the packet for repeated work.  Just ignore the packet.
      if(ab.hasTCP())TimeLine.printMyTimeLine();
      assert !ab.hasTCP():"ERROR: got tcp with existing task #, FROM " + ab._h2o.toString() + " AB: " +  UDP.printx16(lo,hi);      // All the resends should be UDP only
      // DROP PACKET
    } else {
      // This is an old re-send of the same thing we've answered to before.
      // Send back the same old answer ACK.  If we sent via TCP before, then
      // we know the answer got there so just send a control-ACK back.  If we
      // sent via UDP, resend the whole answer.
      if(ab.hasTCP())TimeLine.printMyTimeLine();
      assert !ab.hasTCP():"ERROR: got tcp with existing task #, FROM " + ab._h2o.toString() + " AB: " +  UDP.printx16(lo,hi);      // All the resends should be UDP only
      old.resend_ack();
    }
    return ab;
  }

  // TCP large RECEIVE of results.  Note that 'this' is NOT the RPC object
  // that is hoping to get the received object, nor is the current thread the
  // RPC thread blocking for the object.  The current thread is the TCP
  // reader thread.
  static void tcp_ack( final AutoBuffer ab ) {
    // Get the RPC we're waiting on
    int task = ab.getTask();
    RPC rpc = ab._h2o.taskGet(task);
    // Race with canceling a large RPC fetch: Task is already dead.  Do not
    // bother reading from the TCP socket, just bail out & close socket.
    if( rpc == null ) {
      ab.drainClose();
    } else {
      assert rpc._tasknum == task;
      assert !rpc._done;
      // Here we have the result, and we're on the correct Node but wrong
      // Thread.  If we just return, the TCP reader thread will close the
      // remote, the remote will UDP ACK the RPC back, and back on the current
      // Node but in the correct Thread, we'd wake up and realize we received a
      // large result.
      rpc.response(ab);
    }
    // ACKACK the remote, telling him "we got the answer"
    new AutoBuffer(ab._h2o).putTask(UDP.udp.ackack.ordinal(),task).close();
  }

  // Got a response UDP packet, or completed a large TCP answer-receive.
  // Install it as The Answer packet and wake up anybody waiting on an answer.
  protected void response( AutoBuffer ab ) {
    assert _tasknum==ab.getTask();
    if( _done ) { ab.close(); return; } // Ignore duplicate response packet
    int flag = ab.getFlag();    // Must read flag also, to advance ab
    if( flag == SERVER_TCP_SEND ) { ab.close(); return; } // Ignore UDP packet for a TCP reply
    assert flag == SERVER_UDP_SEND;
    synchronized(this) {        // Install the answer under lock
      if( _done ) { ab.close(); return; } // Ignore duplicate response packet
      UDPTimeOutThread.PENDING.remove(this);
      _dt.read(ab);             // Read the answer (under lock?)
      ab.close();               // Also finish the read (under lock?)
      _dt.onAck();              // One time only execute (before sending ACKACK)
      _done = true;             // Only read one (of many) response packets
      ab._h2o.taskRemove(_tasknum); // Flag as task-completed, even if the result is null
      notifyAll();              // And notify in any case
    }
  }

  // ---
  static final long RETRY_MS = 200; // Initial UDP packet retry in msec
  // How long until we should do the "timeout" action?
  @Override public final long getDelay( TimeUnit unit ) {
    long delay = (_started+_retry)-System.currentTimeMillis();
    return unit.convert( delay, TimeUnit.MILLISECONDS );
  }
  // Needed for the DelayQueue API
  @Override public final int compareTo( Delayed t ) {
    RPC<?> dt = (RPC<?>)t;
    long nextTime = _started+_retry, dtNextTime = dt._started+dt._retry;
    return nextTime == dtNextTime ? 0 : (nextTime > dtNextTime ? 1 : -1);
  }
}
