package lcd;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;

import lcd.LCDAbstractQueuedSynchronizer.State;

/**
 * The request object contains all fields required for the LCD execution.
 * 
 * @author Dana Drachsler-Cohen
 *
 */
public class LCDRequest {
	
	/** The owner thread. **/
	public Thread thread;
	
	/** The request details*/
	public int key;
	public Operation op;
	
	/** A flag that when set to true indicates that this request was completed, but the thread acts as a combiner. */
	public boolean onlyHelpsOthers; 
	
	/** The state status. */
	public volatile LCDAbstractQueuedSynchronizer.State state = State.NONE; 
	
	/** The next request in the request list. */
	public volatile LCDRequest next;
	
	/** The head of the request list. */
	public volatile LCDRequest head;
	
	/** A flag that indicates that the thread was combined (to avoid reading twice volatile field). */
	public boolean wasCombined;
	
	/** The request result. */
	public volatile boolean result = false;
	
	/** The set of thread that required to be awaken when this thread completes execution. */
	public Set<LCDRequest> awakeList = new HashSet<LCDRequest>(); 

	/** The node that asked for marking the successor. */
	public LCDRequest nodeToWakeUponRestart;

	/** A flag that indicates that the thread has delegated the lock to another thread. */
	public boolean delegatedLock = false;

	/** A flag that indicates that the thread has waited to acquire the lock, and thus needs to let the lock queue tail acquire the lock. */
	public boolean wakeTailUponUnlock = false;
	
	/** The operation types. */
	public enum Operation {
		Insert, Remove, Mark;
	}

	public LCDRequest(Thread thread) {
		this.thread = thread;
		reset();
	}

	public void setNewOpNode(int value, Operation op) {
		this.key = value;
		this.op = op;
	}

	public void reset() {
		shortReset();
		this.result = false;
		this.onlyHelpsOthers = false;
	}

	public void shortReset() {
		this.next = null;
		this.head = this;
		unlockReset();
	}

	public void unlockReset() {
		this.state = State.NONE;
		this.wasCombined = false;
		this.nodeToWakeUponRestart = null;
	}

	public void wakeAllRequests() {
		if (nodeToWakeUponRestart != null) {
			System.out.println("Req:wakeAllRequests");
			nodeToWakeUponRestart.state = State.COMPLETED;
			LockSupport.unpark(nodeToWakeUponRestart.thread);
			nodeToWakeUponRestart = null;
		}
		if (awakeList.size() > 0) {
			Iterator<LCDRequest> iterator = awakeList.iterator();

			LCDRequest qnode;
			while (iterator.hasNext()) {
				qnode = iterator.next();
				qnode.state = State.COMPLETED;
				LockSupport.unpark(qnode.thread);
			}
			awakeList.clear();
		}
		
	}
	
	@Override
	public String toString() {
		return thread + ": " + key + " " + op;
	}
}
