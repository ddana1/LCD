package tests;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.concurrent.CyclicBarrier;

import javax.management.RuntimeErrorException;

/**
 * @author Dana Drachsler-Cohen
 *
 */
public class TestDataStructure {

	private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors();
	private static final int NUM_ITERS = 1000000;
	private static final int RANDOM_LIMIT = 1024;
	private static final double CONTAINS_LIMIT = 0.6;
	private static final double INSERT_LIMIT = 0.8;
	private static final boolean TIMED_TRIALS = true; 
	private static final boolean SAME_PROCESS = true;
	private static final boolean PREFILL = false; 
	private static final int NUM_EXECUTIONS = 1;
	public static final int TRIAL_DURATION_SECONDS = 5; 
	
	/** The suffix of full output. */
	private static final String FULL_OUTPUT_SUFFIX = "_full_output.txt";
	
	/** The suffix of the error output. */
	private static final String ERROR_OUTPUT_SUFFIX = "_errors.txt";
	


	public static int numThreads = NUM_THREADS; 
	public static int numIters = NUM_ITERS;
	private static int randomLimit = RANDOM_LIMIT;
	private static double containsLimit = CONTAINS_LIMIT;
	private static double insertLimit = INSERT_LIMIT;
	public static int randomLimitArg = RANDOM_LIMIT;
	public static double containsLimitArg = CONTAINS_LIMIT;
	public static double insertLimitArg = INSERT_LIMIT;
	private static File writeToFile = null;
	private static File errorFile = null;
	private static int time = 0; // wait forever
	public static boolean debugMode = false;
	private static boolean print = false;
	private static boolean printDebug = false;
	private static String debugFilePath = null;
	private static FileOutputStream outputStream;
	private static String personalDebugFilePath = null;
	private static boolean empty = true;
	private static Boolean specificRun = false;
	public static Boolean timedTrials = TIMED_TRIALS;
	public static Boolean runInSameProcess = SAME_PROCESS;
	public static Boolean prefill = PREFILL;
	public static int numExecutions = NUM_EXECUTIONS;
	public static int trialDurationTime = TRIAL_DURATION_SECONDS;
	public static long[] throughput;
	public static long[] containsTime;
	public static long[] insertTime;
	public static long[] removeTime;
	public static long[] containsFailedTime;
	public static long[] insertFailedTime;
	public static long[] removeFailedTime;
	public static Boolean warmUp = false;
	public static boolean checkConcurrency = false;
	
	public static void main(String[] args) throws InterruptedException, IOException {		
		new TestDataStructure().runMain(args);
	}

	private boolean specialPrefill = false;

	protected void runMain(String[] args) throws InterruptedException,
			FileNotFoundException, IOException {
		setParams(args);
		if (debugMode) {
			numThreads = 1;
		}

		TestDataStructureThread[] threads; 
		if (warmUp) {
			threads = createDataStructureThreadsArray(Runtime.getRuntime().availableProcessors() / 2);
			trialDurationTime = 3;
			Object dataStructure = createNewInstance();
			execute(threads, 1000000, 0.2, 0.6, true, dataStructure);
			clearDataStructure();
			dataStructure = createNewInstance();
			execute(threads, 100, 0.2, 0.6, true, dataStructure);
			clearDataStructure();
			dataStructure = createNewInstance();
			execute(threads, 1000000, 0.1, 0.55, false, dataStructure);
			clearDataStructure();
//			for (int i = 0; i < 2; i++) { // eliminate hot spot effect
//				execute(threads);
//			}
			
		}
		
		threads = createDataStructureThreadsArray(numThreads);
		trialDurationTime = TRIAL_DURATION_SECONDS;
		if (numExecutions > 1) {
			System.out.print("(");
		}
//		specialPrefill = true;
		Object dataStructure = createNewInstance();
		Runtime runtime = Runtime.getRuntime();
		for (int i = 0; i < numExecutions; i++) {
			runtime.gc(); runtime.gc();
			long beginMemory = runtime.totalMemory() - runtime.freeMemory();
//			Object dataStructure = createNewInstance();
			long estimatedTime = execute(threads, randomLimitArg, containsLimitArg, insertLimitArg, prefill, dataStructure);
			runtime.gc(); runtime.gc();
			long usedMemory = runtime.totalMemory() - runtime.freeMemory() - beginMemory;
			int size = getSizeOfDataStructure(dataStructure);
			long nodeMemory = (long) 1.0 * usedMemory / (size == 0? 1 : size);
//			clearDataStructure();
//			runtime.gc(); runtime.gc();
			prepareOutput(i, estimatedTime, usedMemory, nodeMemory, this.getClass().getSimpleName(), getAdditionalResultOutput(threads));
			if (numExecutions > 1) {
				System.out.print("+" + (i + 1));
			}
		}
		clearDataStructure();
		runtime.gc(); runtime.gc();
		if (numExecutions > 1) {
			System.out.print(")");
		}
	}

	private long execute(TestDataStructureThread[] threads, int maxKey, double contains, double insert, boolean prefill, Object dataStructure) throws InterruptedException, IOException {
		
		
		containsLimit = contains;
		insertLimit = insert;
		randomLimit = maxKey;
		
		if (specificRun) {
			specificRun();
		}
		if (prefill) {
			threads = createDataStructureThreadsArray(Runtime.getRuntime().availableProcessors() / 2);
			throughput = new long[threads.length];
			containsTime = new long[threads.length];
			insertTime = new long[threads.length];
			removeTime = new long[threads.length];
			containsFailedTime = new long[threads.length];
			insertFailedTime = new long[threads.length];
			removeFailedTime = new long[threads.length];
			CyclicBarrier barrier = new CyclicBarrier(threads.length);
			
			if (specialPrefill) {
				prefill(threads, dataStructure, barrier, 0, 1 - 1.0 / ((insertLimit - containsLimit) * 1.0 / (1 - insertLimit)));
			} else {
				prefill(threads, dataStructure, barrier);	
			}
		}
		threads = createDataStructureThreadsArray(numThreads);
		throughput = new long[threads.length];
		containsTime = new long[threads.length];
		insertTime = new long[threads.length];
		removeTime = new long[threads.length];
		containsFailedTime = new long[threads.length];
		insertFailedTime = new long[threads.length];
		removeFailedTime = new long[threads.length];
		CyclicBarrier barrier = new CyclicBarrier(threads.length);
		
		long startTime = executeExperiment(dataStructure, threads, barrier);
		uponTerminationWhenTimeIsTaken();
		long estimatedTime = System.nanoTime() - startTime;
		uponTermination(threads);
		return estimatedTime;
	}

	private void prefill(TestDataStructureThread[] threads,
			Object dataStructure, CyclicBarrier barrier, double contains, double insert)
			throws InterruptedException, IOException {
		if (insert == 0) {
			throw new RuntimeException("cannot insert elements");
		}
		double oldContains = containsLimit;
		double oldInsert = insertLimit;
		containsLimit = contains;
		insertLimit = insert;
		double ratio = 0;
		double lastRaio = 0;
		do {
			lastRaio = ratio;
			executeExperiment(dataStructure, threads, barrier);
			ratio = checkTimedTrialCondition(dataStructure);
//			System.out.print(((Math.round(ratio * 10000)) * 1.0) / 100.0 + " ");
			if (ratio - lastRaio < 0.001) {
				insertLimit = 1;
			} else {
				insertLimit = insert;
			}
		} while (time != 1 && !(ratio  < 1.05 && ratio  > 0.95));

		containsLimit = oldContains;
		insertLimit = oldInsert;
	}
	
	private void prefill(TestDataStructureThread[] threads,
			Object dataStructure, CyclicBarrier barrier)
			throws InterruptedException, IOException {
//		prefill(threads, dataStructure, barrier, containsLimit == 1? 0 : containsLimit, containsLimit == 1? 0.5 : insertLimit);
		prefill(threads, dataStructure, barrier, 0, containsLimit == 1? 0.5 : (insertLimit - containsLimit) / (1 - containsLimit));
	}


	private long executeExperiment(Object dataStructure,
			TestDataStructureThread[] threads, CyclicBarrier barrier)
			throws InterruptedException, IOException {
		long startTime;
		barrier = new CyclicBarrier(threads.length);
		for (int i = 0; i < threads.length; i++) {
			threads[i] = initASingleThread(dataStructure, barrier, 
					numIters, randomLimit, containsLimit, insertLimit, 
					debugMode, print, debugFilePath, i);         
		}
		
		startTime = System.nanoTime();
		if (time != 1) { 
			for (int i = 0; i < threads.length; i++) {
				threads[i].start();
			}
			int multiplyFactor = 1000;
			for (int i = 0; i < threads.length; i++) {
				threads[i].join(time * multiplyFactor);	
				if (threads[i].isAlive()) {
					prepareError(threads, i);
					time = 1;
					multiplyFactor = 1;
				}
			}
		}
		return startTime;
	}

	private double checkTimedTrialCondition(Object dataStructure) {
		double expectedSize = ((insertLimit - containsLimit) / (1 - containsLimit)) * randomLimit;
		int size = getSizeOfDataStructure(dataStructure);
		return size / expectedSize;
	}

	private void specificRun() {
		if (Math.pow(2, Math.getExponent(randomLimit + 1)) - 1 != randomLimit) {
			System.out.println("Warning, you must be doing something wrong");
		}
		int denominator = 2;
		while (true) {
			int nominator = 1;
			int d = (int) (((double) nominator / denominator) * randomLimit);
			while (d <= randomLimit) {
//					System.out.print(d + ":");
				addToDataStructure(d);
				nominator += 2;
				if (d == randomLimit) break;
				d = (int) (((double) nominator / denominator) * randomLimit);
			}
			if (d == randomLimit) {
				break;
			}
			denominator = denominator * 2;
		}
//			System.out.println();
		denominator = 4;
		while (true) {
			int nominator = 1;
			int d = (int) (((double) nominator / denominator) * randomLimit);
			while (d <= randomLimit) {
//					System.out.print(d + ":");
				removeFromDataStructure(d);
				nominator += 2;
				if (d == randomLimit) break;
				d = (int) (((double) nominator / denominator) * randomLimit);
			}
			if (d == randomLimit) {
				break;
			}
			denominator = denominator * 4;
		}
//			System.out.println();
//			Random r = new Random();
//			for (int i = 0; i < randomLimit/ 2; i++) {
//				addToDataStructure(i * 2);
//			}
//			
//			for (int i = 0; i < randomLimit/ 4; i++) {
//				removeFromDataStructure(r.nextInt(randomLimit / 2) * 2);
//			}
		
//				addToDataStructure(r.nextInt(randomLimit));
//			startTime = System.nanoTime();
//			for (int i = 0; i < numIters * 10; i++) {
//				checkContains(r.nextInt(randomLimit / 2) * 2 + 1);
//			}
	}

	protected void checkContains(int i) {
		throw new RuntimeErrorException(null, "no implemenetion");
	}

	protected void removeFromDataStructure(int i) {
		throw new RuntimeErrorException(null, "no implemenetion");
	}

	protected void uponTerminationWhenTimeIsTaken() {
		// TODO Auto-generated method stub
	}

	protected void addToDataStructure(int i) {
		throw new RuntimeErrorException(null, "no implemenetion");
	}
	
	protected int getSizeOfDataStructure(Object dataStructure) {
		throw new RuntimeErrorException(null, "no implemenetion");
	}
	
	protected void clearDataStructure() {
		throw new RuntimeErrorException(null, "no implemenetion");
	}


	protected void uponTermination(TestDataStructureThread[] threads) {
		// No impl.
	}

	private static void prepareError(TestDataStructureThread[] threads, int i) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("Thread " + i + " is alive\n");
		sb.append(threads[i].getErrorInfo() + "\n");
		StackTraceElement[] stackTrace = threads[i].getStackTrace();
		for (StackTraceElement stackTraceElement : stackTrace) { 
			sb.append(stackTraceElement + "\n");
		}
		if (errorFile == null) {
			System.out.println(sb.toString());
		} else {
			if (!runInSameProcess) {
				System.out.println(sb.toString());
			}
			FileOutputStream error = new FileOutputStream(errorFile, true);
			error.write((sb.toString()).getBytes());
			error.close();
		}
	}

	private static void prepareOutput(int executionNum, long estimatedTime, long usedMemory, long nodeMemory, String className, String additionalResultString)
			throws FileNotFoundException, IOException {
		double result = estimatedTime / Math.pow(10, 9);
		double memoryConsumption = usedMemory / 1024;

		double containsTimeAvg = 0;
		double insertTimeAvg = 0;
		double removeTimeAvg = 0;
		double containsFailedTimeAvg = 0;
		double insertFailedTimeAvg = 0;
		double removeFailedTimeAvg = 0;
		if (timedTrials) {
			long average = 0;
			for (int i = 0; i < numThreads; i++) {
				average += throughput[i];
				containsTimeAvg += containsTime[i];
				insertTimeAvg += insertTime[i];
				removeTimeAvg += removeTime[i];
				containsFailedTimeAvg += containsFailedTime[i];
				insertFailedTimeAvg += insertFailedTime[i];
				removeFailedTimeAvg += removeFailedTime[i];
			}
			containsTimeAvg = containsTimeAvg * 1.0 / containsTime.length;
			insertTimeAvg = insertTimeAvg * 1.0 / insertTime.length;
			removeTimeAvg = removeTimeAvg * 1.0 / removeTime.length;
			containsFailedTimeAvg = containsFailedTimeAvg * 1.0 / containsFailedTime.length;
			insertFailedTimeAvg = insertFailedTimeAvg * 1.0 / insertFailedTime.length;
			removeFailedTimeAvg = removeFailedTimeAvg * 1.0 / removeFailedTime.length;
			result = average;
		}
		if (time == 1) {
			result = -1;
		}
		
		String resultOutput = className + "	" + 
				(numExecutions == 1? "" : (executionNum + 1) + "	") + 
				Calendar.getInstance().getTime() + "	" + numThreads + "	" + 
				numIters + "	" + randomLimit + "	" + containsLimit + 
				"	" + insertLimit + "	" + result + "	total mem (KB) and node mem (bytes)\t" + memoryConsumption + "\t" + nodeMemory + "\tcTime/iTime/rTime\t" + containsTimeAvg + "\t" + insertTimeAvg + "\t" + removeTimeAvg + "\t" + 
				containsFailedTimeAvg + "\t" + insertFailedTimeAvg + "\t" + removeFailedTimeAvg + "\t" + additionalResultString;
		if (writeToFile == null) {
			System.out.println(resultOutput);
		} else {
			FileOutputStream output = null;
			try {
				output = new FileOutputStream(writeToFile, true);
				output.write((resultOutput + "\n").getBytes());
			} catch (FileNotFoundException e) {
				System.out.println("Exception while writing results: " + e.getMessage());
				throw e;
			} finally {
				output.close();
			}
			System.err.println(result + "," + memoryConsumption +"," + nodeMemory + "," + containsTimeAvg + "," + insertTimeAvg + "," + removeTimeAvg + "," + containsFailedTimeAvg + "," + insertFailedTimeAvg + "," + removeFailedTimeAvg);
		}
	}

	protected String getAdditionalResultOutput(TestDataStructureThread[] threads) {
		return "";
	}

	protected TestDataStructureThread initASingleThread(Object dataStructure,
			CyclicBarrier barrier, int numIters, int randomLimit, double containsLimit,
			double insertLimit, boolean debugMode, boolean print, String debugFilePath, int id) {
		throw new RuntimeErrorException(null, "no implemenetion");
	}

	protected TestDataStructureThread[] createDataStructureThreadsArray(int size) {
		throw new RuntimeErrorException(null, "no implemenetion");
	}

	protected Object createNewInstance() {
		throw new RuntimeErrorException(null, "no implemenetion");
	}

	protected static void setParams(String args[]) {	
		if (args.length > 0) {
			numThreads = new Integer(args[0]); 
		}

		// the number of iterations each thread will perform
		if (args.length > 1) {
			numIters = new Integer(args[1]); 
		}

		// the keys will be drawn from the domain [0,...,randomLimit - 1]
		if (args.length > 2) {
			randomLimitArg = new Integer(args[2]); 
		}

		// the probability to choose a contain operation 
		if (args.length > 3) {
			containsLimitArg = new Double(args[3]);
		}

		// (insertLimit - containsLimit) is the probability to choose an insert operation
		if (args.length > 4) {
			insertLimitArg = new Double(args[4]);
		}

		// the file to write the result, if no file is given, results will be written to stdout
		if (args.length > 5) {
			writeToFile = new File(args[5] + FULL_OUTPUT_SUFFIX);
			errorFile = new File(args[5] + ERROR_OUTPUT_SUFFIX);
		}
		
		// the maximal time (in seconds) to wait for a thread to finish, if 0 waits forever, default is zero
	    if (args.length > 6) {
	    	time = new Integer(args[6]);
	    }
	    
	    if (args.length > 7) {
	    	empty = new Boolean(args[7]);
	    }
	    
	    if (args.length > 8) {
	    	specificRun = new Boolean(args[8]);
	    }
	    
	    if (args.length > 9) {
	    	timedTrials = new Boolean(args[9]);
	    }
	    
	    if (args.length > 10) {
	    	runInSameProcess = new Boolean(args[10]);
	    }
	    
	    if (args.length > 11) {
	    	prefill = new Boolean(args[11]);
	    }
	    
	    if (args.length > 12) {
	    	numExecutions = new Integer(args[12]);
	    }
	    
	    if (args.length > 13) {
	    	warmUp = new Boolean(args[13]);
	    }
	    
	}
	
	public static void setNumThreads(int numThreads) {
		TestDataStructure.numThreads = numThreads;
	}
	
	public static void setRandomLimit(int randomLimit) {
		TestDataStructure.randomLimitArg = randomLimit;
	}
	
	public static void setContainsLimit(double containsLimit) {
		TestDataStructure.containsLimitArg = containsLimit;
	}
	
	public static void setInsertLimit(double insertLimit) {
		TestDataStructure.insertLimitArg = insertLimit;
	}
	
	public static void setDebugMode() {
		TestDataStructure.debugMode = true;
	}
	
	public static void setPrint() {
		TestDataStructure.print = true;
	}
	
	public static void setDebugFilePath(String debugFilePath) {
		TestDataStructure.debugFilePath = debugFilePath;
	}
	
	public static void setPersonalDebugFilePath(String debugFilePath) {
 		TestDataStructure.personalDebugFilePath  = debugFilePath;
		try {
			outputStream = new FileOutputStream(new File(debugFilePath));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static void setPrintDebug() {
		TestDataStructure.printDebug = true;
	}
	
	public static void setNumIters(int numIters) {
		TestDataStructure.numIters = numIters;
	}
	
	public static void printToStdOut(String string) {
		if (printDebug) {
			String outputStr = Calendar.getInstance().getTime() + "\t" + Thread.currentThread().getName() + "\t" + string;
			if (personalDebugFilePath == null) {
				System.out.println(outputStr);
			} else {
				try {
					outputStream.write((outputStr + "\n").getBytes());
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}

	public static void setTimedTrials(boolean b) {
		timedTrials = b;
	}
	
}
