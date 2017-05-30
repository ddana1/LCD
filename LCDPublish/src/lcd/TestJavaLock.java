package lcd;

import java.io.IOException;
import java.util.concurrent.CyclicBarrier;

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
	
	@Override
	protected String getAdditionalResultOutput(TestDataStructureThread[] threads) {
		double avgTime = 0;
		int count = 0;
		for (TestDataStructureThread thread : threads) {
//			avgTime += ((TestThreadJavaLock) thread).avgTime;
			count += ((TestThreadJavaLock) thread).bigDelays;
		}
//		return Double.toString(avgTime / threads.length / Math.pow(10, 9));
		return Double.toString(count);
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

}
