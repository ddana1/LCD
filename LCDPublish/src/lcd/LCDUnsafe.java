package lcd;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

/**
 * The Unsafe implementation (defined here due to access permission to the original Unsafe class).
 * 
 * @see Unsafe
 *  
 */
public class LCDUnsafe {
	
	private static Unsafe unsafe = null;
	
	private LCDUnsafe() {}

    public static Unsafe getUnsafeInstance() {
    	if (unsafe == null) {
    		synchronized (LCDUnsafe.class) {
    			try {
    				Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
    				theUnsafeInstance.setAccessible(true);
    				unsafe =  (Unsafe) theUnsafeInstance.get(Unsafe.class);
    			} catch (Exception e) {
    				e.printStackTrace();
    				System.exit(1);
    			}
			}
    	}
		return unsafe;
    }
}
