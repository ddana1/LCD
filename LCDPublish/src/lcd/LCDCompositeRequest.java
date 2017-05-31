package lcd;

/**
 * A composite request is a request which contains another (simple) request.
 * 
 * @author Dana Drachsler-Cohen
 *
 */
public class LCDCompositeRequest extends LCDRequest {
	
	public LCDRequest subRequest;
	
	public LCDCompositeRequest(Thread thread) {
		super(thread);
		subRequest = new LCDRequest(thread);
	}
}