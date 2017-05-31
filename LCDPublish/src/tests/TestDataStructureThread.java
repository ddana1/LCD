package tests;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import javax.management.RuntimeErrorException;

/**
 * @author Dana Drachsler-Cohen
 *
 */
public abstract class TestDataStructureThread extends Thread {
	
	private static final int MAX_VAL_CHECK_CON = 10000;

	private static final int[] primes = new int[]{2, 3, 5, 7, 11, 13, 17, 19, 23, 
		29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97, 101, 
		103, 107, 109, 113, 127, 131, 137, 139, 149, 151, 157, 163, 167, 173,
		179, 181, 191, 193, 197, 199, 211, 223, 227, 229, 233, 239, 241, 251,
		257, 263, 269, 271, 277, 281, 283, 293, 307, 311, 313, 317, 331, 337, 
		347, 349, 353, 359, 367, 373, 379, 383, 389, 397, 401, 409, 419, 421,
		431, 433, 439, 443, 449, 457, 461, 463, 467, 479, 487, 491, 499};
	
	private int randomLimit;
	private int numIters;
	private Random random;
	private double containsLimit;
	private double insertLimit;
	protected boolean debugMode;
	public double containsTime;
	public double containsCount;
	public double containsFailedTime;
	public double containsFailedCount;
	public double insertTime;
	public double insertCount;
	public double insertFailedTime;
	public double insertFailedCount;
	public double removeTime;
	public double removeCount;
	public double removeFailedTime;
	public double removeFailedCount;
	protected boolean print;
	private Set<Integer> setDebug; 
	protected StringBuilder sb;
	protected FileOutputStream debugFile;
	private int numThreads;
	protected int id;
	private CyclicBarrier barrier;

	private String name;
	
	protected TestDataStructureThread(CyclicBarrier barrier, int numIters, int randomLimit, 
			double containsLimit, double insertLimit, boolean debugMode, 
			boolean print, String debugFilePath, int id) {
		this.barrier = barrier;
		this.numIters = numIters;
		this.id = id;
		this.random = new Random();
		this.containsLimit = containsLimit;
		this.insertLimit = insertLimit;
		this.debugMode = debugMode;
		this.print = print;
		if (debugMode) {
			setDebug = new HashSet<Integer>();
			sb = new StringBuilder();
		}
		if (debugFilePath != null) {
			try {
				debugFile = new FileOutputStream(new File(debugFilePath));
			} catch (FileNotFoundException e) {
				throw new RuntimeErrorException(new Error(e));
			}
		}
		this.numThreads = TestDataStructure.numThreads;
		this.randomLimit = randomLimit;
		this.name = "T" + id;
	}

	public void run() {
		try {
			barrier.await();
		} catch (InterruptedException e1) {
			e1.printStackTrace();
			return;
		} catch (BrokenBarrierException e1) {
			e1.printStackTrace();
			return;
		}
		if (TestDataStructure.checkConcurrency) {
			for (int i = 1; i < MAX_VAL_CHECK_CON; i++) {
				int value = primes[id] * i;
				insert(value);
				int remainder = value;
				int iters = 0;
				while (remainder % primes[id] == 0) {
					iters++;
					remainder = remainder / primes[id];
				}
				if (iters % 2 == 0) {
					remove(value);
				}
			}
			try {
				barrier.await();
			} catch (InterruptedException e1) {
				e1.printStackTrace();
				return;
			} catch (BrokenBarrierException e1) {
				e1.printStackTrace();
				return;
			}
			for (int i = 1; i < MAX_VAL_CHECK_CON; i++) {
				int value = primes[id] * i;
				int remainder = value;
				int iters = 0;
				while (remainder % primes[id] == 0) {
					iters++;
					remainder = remainder / primes[id];
				}
				if (remainder != 1) continue;
				if (iters % 2 == 0) {
					if (contains(value)) {
						System.out.println("bug (1) in checking concurrency (TestDataStructureThread)!");
					}
				} else {
					if (!contains(value)) {
						System.out.println("bug (2) in checking concurrency (TestDataStructureThread)!");
					}
				}
			}
			return;
		}
		if (TestDataStructure.timedTrials) {
			long beginTime = System.nanoTime();
			long counter = 0;
			int numIterSmall = 100;
			double time = 0;
			int duration = TestDataStructure.trialDurationTime;
			do {
				for (int i = 0; i < numIterSmall; i++) {
					runSingleOperation();
				}
				counter += numIterSmall; 
				time = (System.nanoTime() - beginTime) / Math.pow(10,9);
			} while (time < duration);
//			TestDataStructure.throughput[id] = counter;
			time = (System.nanoTime() - beginTime) * 1.0 / Math.pow(10,9);
			TestDataStructure.throughput[id] = (long) (counter * 1.0 / time);
			TestDataStructure.containsTime[id] = (long) (containsTime * 1.0 / containsCount);
			TestDataStructure.insertTime[id] = (long) (insertTime * 1.0 / insertCount);
			TestDataStructure.removeTime[id] = (long) (removeTime * 1.0 / removeCount);
			TestDataStructure.containsFailedTime[id] = (long) (containsFailedTime * 1.0 / containsFailedCount);
			TestDataStructure.insertFailedTime[id] = (long) (insertFailedTime * 1.0 / insertFailedCount);
			TestDataStructure.removeFailedTime[id] = (long) (removeFailedTime * 1.0 / removeFailedCount);
		} else {
			runWithFixedNumIterations();
		}
		finish();
	}

	private void runWithFixedNumIterations() {
//		if (true) {
//		for (int j = 0; j < numIters / 10; j++) {
//			int insert = random.nextInt(9);
//			int remove = random.nextInt(10 - insert - 1) + insert + 1;
//			int value = randomLimit * id + random.nextInt(randomLimit);
//			for (int i = 0; i < 10; i++) {
//				if (i == insert) {
//					insert(value);
//				} else if (i == remove) { 
//					remove(value);
//				} else {
//					contains(random.nextInt(randomLimit * numThreads));
//				}
//			}
//		}
//		return;
//	}

		int index = 0;
		String name = Thread.currentThread().getName();
		if (!print && !debugMode && debugFile == null) {
			for (int j = 0; j < numIters; j++) {
				runSingleOperation();
			}
		} else {
			
			for (int j = 0; j < numIters; j++) {
				double rand = random.nextDouble();
				int value = random.nextInt(randomLimit);
				if (rand < containsLimit) {
					try {
						debugAndPrint(name, "contains", value);
						boolean result = contains(value);
						validate("contains", value, result);
					} catch (RuntimeException e) {
						debugAndPrint(name, "contains", value);
						printToErr();
						throw e;
					}
				} else if (rand < insertLimit) {
					try {
						debugAndPrint(name, "insert", value);
						boolean result = insert(value);
						validate("insert", value, result);
						if (debugMode && result) {
							setDebug.add(value);
						}
					} catch (RuntimeException e) {
						debugAndPrint(name, "insert", value);
						printToErr();
						throw e;
					}
				} else {
					try {
						debugAndPrint(name, "remove", value);
						boolean result = remove(value);
						validate("remove", value, result);
						if (debugMode && result) {
							setDebug.remove(value);
						}
					} catch (RuntimeException e) {
						debugAndPrint(name, "remove", value);
						printToErr();
						throw e;
					}
				}
					
			}
		}
	}

	private void runSingleOperation() {
		double rand = random.nextDouble();
		//				int value = random.nextInt(randomLimit / 2) * 2 + 1;
		int value = random.nextInt(randomLimit);
		if (rand < containsLimit) {
			if (print) debugAndPrint(name, "contains", value);
			long beginTime = System.nanoTime();
			boolean contains = contains(value);
			long endTime = System.nanoTime();
			if (contains) {
				containsTime += endTime - beginTime;
				containsCount++;
			} else {
				containsFailedTime += endTime - beginTime;
				containsFailedCount++;
			}
		} else if (rand < insertLimit) {
			//					value = 2 * (value - 1) * numThreads + 2 * id + 1;
			//					if (index >= list.size()) System.out.println("wanted more values");
			//					else {
			//						int value = list.get(index);
			//						index++;
			if (print) debugAndPrint(name, "insert", value);
			long beginTime = System.nanoTime();
			boolean insert = insert(value);
			//			long endTime = System.nanoTime();
			long endTime = System.nanoTime();
			if (insert) {
				insertTime += endTime - beginTime;
				insertCount++;
			} else {
				insertFailedTime += endTime - beginTime;
				insertFailedCount++;
			}
		} else {
			//					int value = random.nextInt(randomLimit / 2) * 2 + 1;
			if (print) debugAndPrint(name, "remove", value);
			long beginTime = System.nanoTime();
			boolean remove = remove(value);
			long endTime = System.nanoTime();
			if (remove) {
				removeTime += endTime - beginTime;
				removeCount++;
			} else {
				removeFailedTime += endTime - beginTime;
				removeFailedCount++;
			}
		}
	}

public String getErrorInfo() {
		return "";
	}
	
	protected void finish() {
	}
	
	private void printToErr() {
		if (debugMode) {
			System.err.println("begin");
			System.err.println(sb.toString());
		}
	}

	protected void debugAndPrint(String threadName, String command, int value) {
		String printStr = threadName + "\t" + command + "\t (" + value + ")"; 
		if (print) {
			System.out.println(printStr);
		}
		if (debugMode) {
			sb.append(printStr + "\n");
		}
		if (debugFile != null) {
			try {
				debugFile.write((printStr + "\n").getBytes());
			} catch (IOException e) {
				throw new RuntimeErrorException(new Error(e));
			}
		}
	}

	protected void validate(String command, int value, boolean result) {
		if (debugMode) {
			if (command != "insert") {
				if (result && !setDebug.contains(value)) {
					printToErr();
					throw new RuntimeErrorException(null, "err 1: set doesn't contain value but returned true");
				}
				if (!result && setDebug.contains(value)) {
					printToErr();
					throw new RuntimeErrorException(null, "err 2: set contains value but returned false");
				}
			} else {
				if (!result && !setDebug.contains(value)) {
					printToErr();
					throw new RuntimeErrorException(null, "err 3: set doesn't contain value but returned false");
				}
				if (result && setDebug.contains(value)) {
					printToErr();
					throw new RuntimeErrorException(null, "err 4: set contains value but returned true");
				}
			}
		}
	}

	protected abstract boolean remove(int value);

	protected abstract boolean insert(int value);

	protected abstract boolean contains(int value);

}
