/*
 * LazyList.java
 *
 * Created on January 4, 2006, 1:41 PM
 *
 * From "Multiprocessor Synchronization and Concurrent Data Structures",
 * by Maurice Herlihy and Nir Shavit.
 * Copyright 2006 Elsevier Inc. All rights reserved.
 */

package lcd;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;

import javax.management.RuntimeErrorException;

import lcd.LCDAbstractQueuedSynchronizer.State;
import lcd.LCDRequest.Operation;

public class LCDList {

	/** The list head. */	
	private Node head;

	public LCDList() {
		this.head  = new Node(Integer.MIN_VALUE);
		this.head.next = new Node(Integer.MAX_VALUE);
	}

	public boolean insert(int value, LCDRequest request) {
		Node pred = this.head;
		while (true) {
			Node curr = pred.next;
			while (curr.key < value) {
				pred = curr; curr = curr.next;
			}
			if (!pred.lock(request, value, Operation.Insert)) {
				return handleOperation(value, true, pred, request);
			}
			Boolean addResult = simpleAdd(pred, value, request);
			if (addResult != null) {
				return addResult;
			} 
			if (pred.marked == true || pred.key >= value) {
				pred = head;
			}
		}
	}

	private Boolean simpleAdd(Node pred, int key, LCDRequest req) {
		try {
			Node curr = pred.next;
			if (!pred.marked && curr.key >= key) {
				if (curr.key == key) { 
					return false;
				} else {               
					Node Node = new Node(key);
					Node.next = curr;
					pred.next = Node;
					return true;
				}
			}
		} finally { 
			pred.quickUnlock(req);
		}
		return null;
	}

	public boolean remove(int value, LCDRequest request) {
		Node pred = this.head;
		while (true) {
			Node curr = pred.next;
			while (curr.key < value) {
				pred = curr; curr = curr.next;
			}
			if (!pred.lock(request, value, Operation.Remove)) {
				return handleOperation(value, false, pred, request);
			}
			Boolean remResult = simpleRemove(pred, value, request);
			if (remResult != null) {
				return remResult;
			}  
			if (pred.marked == true || pred.key >= value) {
				pred = head;
			}
		}
	}

	private Boolean simpleRemove(Node pred, int key, LCDRequest request) {
		try {
			Node curr = pred.next;
			if (!pred.marked && curr.key >= key) {
				if (curr.key > key) {
					return false;
				}
				if (!curr.lock(request, key, Operation.Mark)) {
					handleOperation(key, false, curr, request);
				} else {
					curr.marked = true;
					curr.quickUnlock(request);
				}
				pred.next = curr.next;
				return true;
			}
		} finally {                     
			pred.quickUnlock(request);
		}
		return null;
	}

	/**
	 * Add an element.
	 * @param pred 
	 * @param thread 
	 * @param item element to add
	 * @return true iff element was not there already
	 */
	private boolean handleOperation(int value, boolean isInsert, Node pred, LCDRequest request) {
		boolean lockedUnfair = false;
		Node curr;
		NodeHolders holders = new NodeHolders();
		LCDRequest qnode = request;
		LCDRequest sortedHead; 
		int key = value;
		Operation op;
		boolean collected = false;
		while (true) {
			if (qnode.wasCombined) {
				return returnResult(qnode);
			}
			State locked = qnode.state;
			if (qnode.delegatedLock || locked == State.WRONG_LOCK_INIT_HEAD || locked == State.WRONG_LOCK) {
				if (qnode.delegatedLock) {
					qnode.delegatedLock = false;
				}
				collected = true; 
				if (locked == State.WRONG_LOCK_INIT_HEAD) {
					qnode.head = qnode; 
				}
				
				key = locked == State.WRONG_LOCK_INIT_HEAD? qnode.key : qnode.head.key; 
				pred = handleIncorrectNode(pred, qnode, key);
				curr = pred.next;
				while (curr.key < key) {
					pred = curr; curr = curr.next;
				}
				lockedUnfair = pred.lock(request, request.key, request.op);
				continue;
			}
			
			if (locked == State.LOCKED) {
				qnode.state = State.NONE;
			}
			sortedHead = getNextRequest(qnode);

			if (sortedHead == null) {
				pred.unlock(request);
				return returnResult(qnode);
			}

			if (!collected && (sortedHead != qnode || 
					sortedHead.next != null || qnode.awakeList.size() > 0) ){
				collected = true;
			} 

			key = sortedHead.key;
			op = sortedHead.op;
			Node next = pred.next;
			LCDRequest markReq = null;
			try {
				holders.init();
				while (myValidate(pred, key, op, next.key)) {
					boolean successfulInsert = false;
					switch (op) {
					case Insert:
						successfulInsert = addToSublist(pred, sortedHead, qnode, key, 
								next.key, holders); break;

					case Remove: 
						next = removeNode(pred, (LCDCompositeRequest) qnode, sortedHead, key, next);
						collected = true; // if it hasn't finished it has collected
						break;

					case Mark:
						markReq = sortedHead;
						break;

					default:
						throw new RuntimeErrorException(null, "Unsupported operation: " + op);
					}
					sortedHead = finishOperation(qnode, sortedHead, pred, next, holders, op, successfulInsert, markReq);
					if (sortedHead == null) {
						boolean result = qnode.result;
						qnode.reset();
						return result;
					} 

					key = sortedHead.key;
					op = sortedHead.op;
				}
				connectSubList(pred, next, holders);
			} finally {
				if (lockedUnfair) {
					pred.quickUnlock(request);
				} else {
					pred.unlock(request);
				}
			} 
			if (pred.marked == true || key <= pred.key) {
				pred = performRestart(qnode);
			} 
			curr = pred.next;
			while (curr.key < key) {
				pred = curr; curr = curr.next;
			}
			lockedUnfair = pred.lock(request, request.key, request.op);
			if (!collected && lockedUnfair) {
				while (true) {
					if (isInsert) {
						Boolean addResult = simpleAdd(pred, value, request);
						if (addResult != null) {
							return addResult;
						} 
					} else { // op is delete
						Boolean remResult = simpleRemove(pred, value, request);
						if (remResult != null) {
							return remResult;
						}  
					}
					if (pred.marked == true || pred.key >= value) {
						pred = head;
					}
					curr = pred.next;
					while (curr.key < value) {
						pred = curr; curr = curr.next;
					}
					if (!pred.lock(request, request.key, request.op)) {
						lockedUnfair = false;
						break; // go to slow path
					}
				}
			}
		}
	}

	private Node handleIncorrectNode(Node pred, LCDRequest qnode, int key) {
		if (qnode.nodeToWakeUponRestart != null) {
			System.out.println("handleIncorrect");
			wakeupASingleRequest(qnode.nodeToWakeUponRestart);
		}
		qnode.unlockReset();
		if (pred.key >= key) {
			pred = head;
		}
		return pred;
	}

	private boolean returnResult(LCDRequest qnode) {
		qnode.wakeAllRequests();
		boolean result = qnode.result; 
		qnode.reset();
		return result;
	}

	private Node performRestart(LCDRequest qnode) {
		if (qnode.nodeToWakeUponRestart != null) {
			System.out.println("performRestart");
			wakeupASingleRequest(qnode.nodeToWakeUponRestart);
			qnode.nodeToWakeUponRestart = null;
		}
		return head;
	}

	private boolean handleMarkOperation(LCDRequest qnode, Node pred, LCDRequest sortedHead) {
		pred.marked = true;
		if (qnode != sortedHead) {
			if (qnode.nodeToWakeUponRestart != null) {
				System.out.println("handleMark");
				wakeupASingleRequest(qnode.nodeToWakeUponRestart);
			}
//			qnode.nodeToWakeUponRestart = sortedHead;
		}
		return true;
	}

	private Node removeNode(Node pred, LCDCompositeRequest qnode,
			LCDRequest sortedHead, int key, Node curr) {
		if (curr.key == key) {
			((TestThreadJavaLock) Thread.currentThread()).request = qnode.subRequest; 
			Node next = curr;
			boolean unfairLock = next.lock(qnode.subRequest, key, Operation.Mark);
			sortedHead.result = true;
			Node nextNext = next.next;
			if (unfairLock) {
				next.marked = true;
				next.quickUnlock(qnode.subRequest);
			} else {
				if (!qnode.subRequest.wasCombined) {
					next.marked = true;
					LCDRequest subSortedNext = qnode.subRequest.head.next;
					if (subSortedNext != null) { // there are other requests that should be handled, adopt them
						LCDAbstractQueuedSynchronizer.addReqs(qnode, sortedHead, subSortedNext, true, nextNext.key);
					}
					next.unlock(qnode.subRequest);
				}
				qnode.subRequest.reset();
			}
			((TestThreadJavaLock) Thread.currentThread()).request = qnode;
			next = nextNext;
			pred.next = next;
			return next;
		}
		return curr;
	}

	private boolean addToSublist(Node pred, LCDRequest sortedHead, LCDRequest myQNode, int key,
			int nextKey, NodeHolders holders) {
		if (nextKey != key) {
			Node newNode = new Node(key);
			if (holders.currentListHead == null) {
				holders.currentListHead = newNode;
			} else {
				holders.currentListIter.next = newNode;
			}
			holders.currentListIter = newNode;
			sortedHead.result = true;
			if (sortedHead != myQNode) {
				holders.requestsToWake.add(sortedHead);
			}
			return true;
		} 
		return false;
	}

	private void connectSubList(Node pred, Node next, NodeHolders holders) {
		if (holders.currentListHead != null) {
			holders.currentListIter.next = next;
			pred.next = holders.currentListHead;
			for (LCDRequest n : holders.requestsToWake) {
				wakeupASingleRequest(n);
			}
		}
	}

	private LCDRequest getNextRequest(LCDRequest qnode) {
		LCDRequest currHead = qnode.head;
		LCDRequest req = currHead;
		if (req.result == true) {
			LCDRequest reqNext;
			while (req != null && req.result == true) {
				reqNext = req.next;
				reqIsFinished(qnode, req, reqNext);
				req = reqNext;
			}

			if (req != null && req != currHead) {
				qnode.head = req;
			}
		}
		return req;
	}

	private void reqIsFinished(LCDRequest qnode, LCDRequest req, LCDRequest reqNext) {
		if (req != qnode) {
			wakeupASingleRequest(req);
		} else if (reqNext != null) {
			qnode.onlyHelpsOthers = true;
		}
	}

	private void wakeupASingleRequest(LCDRequest req) {
		req.state = State.COMPLETED;
		LockSupport.unpark(req.thread);
	}

	private LCDRequest finishOperation(LCDRequest qnode, LCDRequest sortedHead, Node pred, Node next, NodeHolders holders/*, Node currentListHead, Node currentListIter, Node currentListIterNext*/, Operation op,
			boolean successfulInsert, LCDRequest markReq) {
		LCDRequest sortedNext = sortedHead.next;
		LCDRequest temp;
		while (sortedNext != null && sortedNext.result == true) {
			temp = sortedNext.next;
			sortedNext.next = null;
			reqIsFinished(qnode, sortedNext, temp);
			sortedNext = temp;
		}
		sortedHead.next = null; 
		if (sortedHead != qnode) {
			if (op != Operation.Mark && !successfulInsert) {
				wakeupASingleRequest(sortedHead);
			}
		} else if (sortedNext != null) {
			qnode.onlyHelpsOthers = true;
		}
		if (sortedNext != null) { 
			qnode.head = sortedNext;
		} else {
			connectSubList(pred, next, holders);
			if (markReq != null) {
				handleMarkOperation(qnode, pred, markReq);
				if (markReq != qnode) {
					wakeupASingleRequest(markReq);
				} else {
					qnode.onlyHelpsOthers = true;
				}
			}
			qnode.wakeAllRequests();
		}
		return sortedNext;
	}

	private boolean myValidate(Node pred, int key, Operation op, int nextKey) {
		boolean marked = pred.marked;
		if (op == Operation.Mark && (marked != false || pred.key != key)) {
			System.out.println("?");
		}
		return (key > pred.key && key <= nextKey) && marked == false || 
				(op == Operation.Mark && marked == false && pred.key == key); 

	}

	public void check() {
		Node iter = head.next;
		int val = head.key;
		while (iter != null) {
			if (iter.key <= val) {
				System.err.println("bug: " + val + " < " + iter.key);
			}
			val = iter.key;
			iter = iter.next;
		}
	}

	/**
	 * Test whether element is present
	 * @param item element to test
	 * @return true iff element is present
	 */
	public boolean contains(int key) {
		Node curr = this.head;
		while (curr.key < key)
			curr = curr.next;
		return curr.key == key && !curr.marked;
	}
	/**
	 * list Node
	 */
	class Node {
		/**
		 * item's hash code
		 */
		int key;

		/**
		 * next Node in list
		 */
		volatile Node next;
		/**
		 * If true, Node is logically deleted.
		 */
		boolean marked;
		/**
		 * Synchronizes Node.
		 */
		LCDReentrantLock lock;
		/**
		 * Constructor for usual Node
		 * @param item element in list
		 */
		Node(int key) {      
			this.key = key;
			this.next = null;
			this.marked = false;
			this.lock = new LCDReentrantLock(this);
		}

		/**
		 * Lock Node
		 */
		Boolean lock(LCDRequest request, int key, Operation op) {
			return lock.LCDLock(request, key, op);
		}

		boolean tryLock(LCDRequest request) {
			return lock.tryLock(request);
		}

		void unlock(LCDRequest req) {
			lock.LCDUnlock(req); 
		}

		public void quickUnlock(LCDRequest req) {
			lock.LCDQuickUnlock(req);
		}

		@Override
		public String toString() {
			return Integer.toString(key);
		}
	}

	private class NodeHolders {

		Node currentListHead = null;

		Node currentListIter = null;

		Set<LCDRequest> requestsToWake = new HashSet<LCDRequest>();

		public void init() {
			currentListHead = null;
			currentListIter = null;
			requestsToWake.clear();
		}

	}

	public int size() {
		int count = 0;
		Node n = head.next;
		while (n.key != Integer.MAX_VALUE) {
			count++;
			n = n.next;
		}
		return count;
	}
}

