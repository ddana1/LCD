package tests.lcd;

import java.util.concurrent.CyclicBarrier;

import lcd.LCDCompositeRequest;
import lcd.LCDList;
import lcd.LCDRequest;
import tests.TestDataStructureThread;

public class TestThreadJavaLock extends TestDataStructureThread {
	
	LCDList list;
	public LCDRequest request;
	int locks;
	double avgTime;
	public int bigDelays;

	protected TestThreadJavaLock(LCDList list, CyclicBarrier barrier, int numIters, int randomLimit,
			double containsLimit, double insertLimit, boolean debugMode, 
			boolean print, String debugFilePath, int id) {
		super(barrier, numIters, randomLimit, containsLimit, insertLimit, debugMode, 
				print, debugFilePath, id);
		this.list = list;
		this.request = new LCDCompositeRequest(this);
		request.thread = this;
	}
	
	@Override
	protected boolean remove(int value) {
		return list.remove(value, request);
	}

	@Override
	protected boolean insert(int value) {
		return list.insert(value, request);
		
	}

	@Override
	protected boolean contains(int value) {
		return list.contains(value);
	}

}
