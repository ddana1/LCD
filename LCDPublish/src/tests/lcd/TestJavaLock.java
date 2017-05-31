package tests.lcd;

import java.io.IOException;
import java.util.concurrent.CyclicBarrier;

import lcd.LCDList;
import lcd.LCDRequest;
import lcd.LCDUnsafe;
import tests.TestDataStructure;
import tests.TestDataStructureThread;

public class TestJavaLock extends TestDataStructure {
	
	private LCDList list;
	
	public static void main(String[] args) throws InterruptedException, IOException {
		new TestJavaLock().runMain(args);
	}

	@Override
	protected Object createNewInstance() {
	    list = new LCDList();
	    LCDUnsafe.getUnsafeInstance();
		return list;
	}
	

	@Override
	protected TestDataStructureThread[] createDataStructureThreadsArray(int size) {
		return new TestThreadJavaLock[size];
	}
	
	@Override
	protected TestDataStructureThread initASingleThread(Object dataStructure, CyclicBarrier barrier,
			int numIters, int randomLimit, double containsLimit,
			double insertLimit, boolean debugMode, boolean print, String debugFilePath, int id) {
		return new TestThreadJavaLock((LCDList) dataStructure, barrier, numIters, randomLimit, 
				containsLimit, insertLimit, debugMode, print, debugFilePath, id);
	}
	
	protected void checkContains(int i) {
		list.contains(i);
	}

	protected void removeFromDataStructure(int i) {
		list.remove(i, new LCDRequest(null));
	}

	protected void addToDataStructure(int i) {
		list.insert(i, new LCDRequest(null));
	}
	
	protected int getSizeOfDataStructure(Object dataStructure) {
		return list.size();
	}
	
	@Override
	protected void clearDataStructure() {
		LCDList.Node h = list.head;
		LCDList.Node n = h.next;
		while (n != null) {
			h.next = null;
			h = n;
			n = n.next;
		}
		h.next = null;
	}

}
