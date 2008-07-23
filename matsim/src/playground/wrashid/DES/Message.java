package playground.wrashid.DES;

public abstract class Message implements Comparable {
	
	private static long messageCounter=0;
	public double messageArrivalTime=0;
	public SimUnit sendingUnit;
	public SimUnit receivingUnit;
	public long messageType;
	public long messageId;
	public String queueKey=""; // only used because of implementation convenience (might be removed in future, if not needed)
	
	// all inheriting or extending modules must
	// invoke this Constructer first
	public Message() {
		messageId=messageCounter;
		messageCounter++;
	}
	
	public double getMessageArrivalTime() {
		return messageArrivalTime;
	}
	
	public void setMessageArrivalTime(double messageArrivalTime) {
		this.messageArrivalTime = messageArrivalTime;
	}
	
	public abstract void printMessageLogString();

	// two messages can not be equal!!!
	public int compareTo(Object obj){
		Message otherMessage= (Message) obj;
		if (messageArrivalTime>otherMessage.messageArrivalTime){
			return 1;
		} else if (messageArrivalTime<otherMessage.messageArrivalTime) {
			return -1;
		} else {
			return (int)(messageId-otherMessage.messageId);
		}
	}

}
